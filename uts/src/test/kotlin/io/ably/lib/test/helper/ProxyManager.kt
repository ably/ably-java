package io.ably.lib.test.helper

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

// GitHub release downloads 302-redirect to the asset CDN; Ktor CIO follows redirects by default.
private val client = HttpClient(CIO) {
    followRedirects = true
}

/**
 * Manages the lifecycle of the `uts-proxy` binary used for integration tests.
 *
 * Downloads the binary from GitHub releases on first use, caching it at
 * `~/.cache/uts-proxy/<version>/uts-proxy`. Safe for concurrent Gradle test workers —
 * a `FileLock` on `uts-proxy.lock` serialises the download across OS processes, while
 * a [Mutex] serialises it within the same JVM.
 *
 * Call [ensureProxy] in `@BeforeAll` / `setUpAll()` for every proxy integration test suite.
 */
object ProxyManager {

    private const val PROXY_VERSION = "v0.2.0"
    private const val VERSION_BARE = "0.2.0"
    const val CONTROL_PORT = 10100
    private const val SANDBOX_HOST = "sandbox.realtime.ably-nonprod.net"
    private const val GITHUB_BASE =
        "https://github.com/ably/uts-proxy/releases/download/$PROXY_VERSION"

    val sandboxRealtimeHost: String = SANDBOX_HOST
    val sandboxRestHost: String = SANDBOX_HOST

    private val CHECKSUMS = mapOf(
        "uts-proxy_${VERSION_BARE}_darwin_amd64.tar.gz" to
            "4abc4bd0682b61d53889c3ad3b240b44cf942878ed9fb04e8912a48070d2666d",
        "uts-proxy_${VERSION_BARE}_darwin_arm64.tar.gz" to
            "2b95cdb5659988f54ad3d413c713f94f944e3b0014011aba2e339b9537c59b2f",
        "uts-proxy_${VERSION_BARE}_linux_amd64.tar.gz"  to
            "aa6d536101ebc3bfa6870ca4cfb75be1947360dc5c1c77d7a8536baa1fee7caa",
        "uts-proxy_${VERSION_BARE}_linux_arm64.tar.gz"  to
            "c8f9363ae579508004727175a098bd0b73518ee3f08cf9071b0c372f8199767a",
    )

    private val os: String by lazy {
        val name = System.getProperty("os.name").lowercase()
        when {
            name.contains("mac")   -> "darwin"
            name.contains("linux") -> "linux"
            else -> error("Unsupported OS for uts-proxy: ${System.getProperty("os.name")}")
        }
    }

    private val arch: String by lazy {
        when (System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64"   -> "amd64"
            "aarch64", "arm64"  -> "arm64"
            else -> error("Unsupported arch for uts-proxy: ${System.getProperty("os.arch")}")
        }
    }

    private val archiveName: String get() = "uts-proxy_${VERSION_BARE}_${os}_${arch}.tar.gz"

    private val cacheDir: Path
        get() = Path.of(System.getProperty("user.home"), ".cache", "uts-proxy", PROXY_VERSION)

    private val binaryPath: Path get() = cacheDir.resolve("uts-proxy")

    @Volatile private var proxyProcess: Process? = null
    private val mutex = Mutex()

    /**
     * Ensures the `uts-proxy` process is running on [CONTROL_PORT].
     *
     * If the proxy is already healthy (e.g. started by a previous test class in the same run),
     * this is a no-op. Otherwise it downloads + verifies the binary and starts the process.
     *
     * @param timeoutMs Maximum real-time milliseconds to wait for the process to become healthy.
     */
    suspend fun ensureProxy(timeoutMs: Int = 15_000): Unit = mutex.withLock {
        if (isHealthy()) return
        ensureBinary()
        proxyProcess = withContext(Dispatchers.IO) {
            ProcessBuilder(binaryPath.toString(), "--port", "$CONTROL_PORT")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
        waitForHealth(timeoutMs.toLong())
    }

    /**
     * No-op retained for Dart API compatibility.
     * The proxy process is shared for the lifetime of the test suite and exits with the JVM.
     */
    fun stopProxy() = Unit

    // ── Internal ──────────────────────────────────────────────────────────────

    internal suspend fun isHealthy(): Boolean = runCatching {
        client.get("http://localhost:$CONTROL_PORT/health").status.value == 200
    }.getOrDefault(false)

    private suspend fun waitForHealth(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) return
            delay(200)
        }
        proxyProcess?.destroyForcibly()
        proxyProcess = null
        error("uts-proxy did not become healthy within ${timeoutMs}ms")
    }

    /** Ensures the binary is present in the cache, downloading and extracting if needed. */
    private suspend fun ensureBinary() = withContext(Dispatchers.IO) {
        Files.createDirectories(cacheDir)
        // FileLock serialises across multiple Gradle test worker JVMs.
        val lockFile = cacheDir.resolve("uts-proxy.lock")
        FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
            channel.lock().use {
                val file = binaryPath.toFile()
                if (file.exists() && sha256Hex(file.readBytes()) == CHECKSUMS[archiveName]) {
                    return@withContext  // already cached and valid
                }
                val archiveBytes = downloadArchive()
                verifyChecksum(archiveBytes)
                val binary = extractFromTarGz(archiveBytes)
                Files.write(binaryPath, binary, CREATE, TRUNCATE_EXISTING)
                binaryPath.toFile().setExecutable(true)
            }
        }
    }

    private suspend fun downloadArchive(): ByteArray {
        System.err.println("Downloading uts-proxy $PROXY_VERSION ($archiveName)…")
        val response = client.get("$GITHUB_BASE/$archiveName")
        check(response.status.value == 200) {
            "Failed to download uts-proxy from $GITHUB_BASE/$archiveName: HTTP ${response.status.value}"
        }
        return response.body()
    }

    private fun verifyChecksum(bytes: ByteArray) {
        val expected = CHECKSUMS[archiveName]
            ?: error("No checksum for $archiveName — unsupported platform/arch")
        val actual = sha256Hex(bytes)
        check(actual == expected) {
            "Checksum mismatch for $archiveName: expected $expected, got $actual"
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Extracts the `uts-proxy` binary from a `.tar.gz` archive using only JDK stdlib.
     *
     * TAR format: sequential 512-byte header blocks each followed by file-data blocks
     * (padded to a multiple of 512). We parse only the fields we need:
     * - offset   0–99 : filename (null-terminated)
     * - offset 124–135: file size in octal ASCII
     * - offset    156 : entry type ('0'/NUL = regular file, '5' = directory, …)
     */
    private fun extractFromTarGz(archiveBytes: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(archiveBytes)).use { gzip ->
            val headerBuf = ByteArray(512)
            while (true) {
                // Read one header block (exactly 512 bytes)
                var totalRead = 0
                while (totalRead < 512) {
                    val n = gzip.read(headerBuf, totalRead, 512 - totalRead)
                    if (n < 0) break
                    totalRead += n
                }
                // End-of-archive: two consecutive zero-filled 512-byte blocks
                if (totalRead < 512 || headerBuf.all { it == 0.toByte() }) break

                // Filename (null-terminated, strip leading ./ or /)
                val nameEnd = (0 until 100).firstOrNull { headerBuf[it] == 0.toByte() } ?: 100
                val name = String(headerBuf, 0, nameEnd).trimStart('.', '/')

                // File size (octal ASCII at offset 124, 12 bytes)
                val sizeStr = String(headerBuf, 124, 12).trimEnd(' ').trim()
                val size = if (sizeStr.isEmpty()) 0L else sizeStr.toLong(8)

                // Entry type flag at offset 156
                val typeFlag = headerBuf[156].toInt().toChar()
                val isRegularFile = typeFlag == '0' || typeFlag == ' '

                if (isRegularFile && name == "uts-proxy" && size > 0) {
                    val content = ByteArray(size.toInt())
                    var read = 0
                    while (read < size) {
                        val n = gzip.read(content, read, (size - read).toInt())
                        if (n < 0) error("Unexpected end of archive while reading uts-proxy entry")
                        read += n
                    }
                    return content
                }

                // Skip this entry's data blocks (size rounded up to 512-byte boundary)
                val dataBytes = (size + 511) / 512 * 512
                var skipped = 0L
                while (skipped < dataBytes) {
                    val n = gzip.skip(dataBytes - skipped)
                    if (n <= 0) break
                    skipped += n
                }
            }
        }
        error("uts-proxy binary not found in archive '$archiveName'")
    }
}
