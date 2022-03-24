package io.ably.lib.transport;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SafeSSLSocketFactoryTest {
    SafeSSLSocketFactory safeSSLSocketFactory;

    @Before
    public void setup() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        safeSSLSocketFactory = new SafeSSLSocketFactory(sslContext.getSocketFactory());
    }

    @Test
    public void should_not_use_insecure_tls_protocols() throws IOException {
        // given
        Set<String> insecureProtocols = new HashSet<>(Arrays.asList(
            "SSLv3",
            "TLSv1",
            "TLSv1.1"
        ));

        // when
        SSLSocket sslSocket = (SSLSocket) safeSSLSocketFactory.createSocket();

        // then
        for (String enabledProtocol : sslSocket.getEnabledProtocols()) {
            Assert.assertFalse(
                "Protocol " + enabledProtocol + " is insecure and should not be enabled",
                insecureProtocols.contains(enabledProtocol)
            );

        }
    }

    @Test
    public void should_use_at_least_one_secure_tls_protocol() throws IOException {
        // given
        Set<String> secureProtocols = new HashSet<>(Arrays.asList(
            "TLSv1.2",
            "TLSv1.3"
        ));

        // when
        SSLSocket sslSocket = (SSLSocket) safeSSLSocketFactory.createSocket();

        // then
        boolean isUsingSecureProtocol = containsAnySecureProtocol(sslSocket.getEnabledProtocols(), secureProtocols);
        Assert.assertTrue("No secure protocols are enabled", isUsingSecureProtocol);
    }

    private boolean containsAnySecureProtocol(String[] protocols, Set<String> secureProtocols) {
        for (String protocol : protocols) {
            if (secureProtocols.contains(protocol)) {
                return true;
            }
        }
        return false;
    }
}
