package io.ably.lib.objects.integration

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectValue
import io.ably.lib.objects.integration.helpers.fixtures.createUserMapObject
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultLiveMapTest: IntegrationTest() {
  /**
   * Tests the synchronization process when a user map object is initialized before channel attach.
   * This includes checking the initial values of all nested maps, counters, and primitive data types
   * in the comprehensive user map object structure.
   */
  @Test
  fun testLiveMapSync() = runTest {
    val channelName = generateChannelName()
    val userMapObjectId = restObjects.createUserMapObject(channelName)
    restObjects.setMapRef(channelName, "root", "user", userMapObjectId)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    // Get the user map object from the root map
    val userMap = rootMap.get("user") as LiveMap
    assertNotNull(userMap, "User map should be synchronized")
    assertEquals(5L, userMap.size(), "User map should contain 5 top-level entries")

    // Assert Counter Objects
    // Test loginCounter - should have initial value of 5
    val loginCounter = userMap.get("loginCounter") as LiveCounter
    assertNotNull(loginCounter, "Login counter should exist")
    assertEquals(5L, loginCounter.value(), "Login counter should have initial value of 5")

    // Test sessionCounter - should have initial value of 0
    val sessionCounter = userMap.get("sessionCounter") as LiveCounter
    assertNotNull(sessionCounter, "Session counter should exist")
    assertEquals(0L, sessionCounter.value(), "Session counter should have initial value of 0")

    // Assert User Profile Map
    val userProfile = userMap.get("userProfile") as LiveMap
    assertNotNull(userProfile, "User profile map should exist")
    assertEquals(6L, userProfile.size(), "User profile should contain 6 entries")

    // Assert user profile primitive values
    assertEquals("user123", userProfile.get("userId"), "User ID should match expected value")
    assertEquals("John Doe", userProfile.get("name"), "User name should match expected value")
    assertEquals("john@example.com", userProfile.get("email"), "User email should match expected value")
    assertEquals(true, userProfile.get("isActive"), "User should be active")

    // Assert Preferences Map (nested within user profile)
    val preferences = userProfile.get("preferences") as LiveMap
    assertNotNull(preferences, "Preferences map should exist")
    assertEquals(4L, preferences.size(), "Preferences should contain 4 entries")
    assertEquals("dark", preferences.get("theme"), "Theme preference should be dark")
    assertEquals(true, preferences.get("notifications"), "Notifications should be enabled")
    assertEquals("en", preferences.get("language"), "Language should be English")
    assertEquals(3.0, preferences.get("maxRetries"), "Max retries should be 3")

    // Assert Metrics Map (nested within user profile)
    val metrics = userProfile.get("metrics") as LiveMap
    assertNotNull(metrics, "Metrics map should exist")
    assertEquals(4L, metrics.size(), "Metrics should contain 4 entries")
    assertEquals("2024-01-01T08:30:00Z", metrics.get("lastLoginTime"), "Last login time should match")
    assertEquals(42.0, metrics.get("profileViews"), "Profile views should be 42")

    // Test counter references within metrics map
    val totalLoginsCounter = metrics.get("totalLogins") as LiveCounter
    assertNotNull(totalLoginsCounter, "Total logins counter should exist")
    assertEquals(5L, totalLoginsCounter.value(), "Total logins should reference login counter with value 5")

    val activeSessionsCounter = metrics.get("activeSessions") as LiveCounter
    assertNotNull(activeSessionsCounter, "Active sessions counter should exist")
    assertEquals(0L, activeSessionsCounter.value(), "Active sessions should reference session counter with value 0")

    // Assert direct references to maps from top-level user map
    val preferencesMapRef = userMap.get("preferencesMap") as LiveMap
    assertNotNull(preferencesMapRef, "Preferences map reference should exist")
    assertEquals(4L, preferencesMapRef.size(), "Referenced preferences map should have 4 entries")
    assertEquals("dark", preferencesMapRef.get("theme"), "Referenced preferences should match nested preferences")

    val metricsMapRef = userMap.get("metricsMap") as LiveMap
    assertNotNull(metricsMapRef, "Metrics map reference should exist")
    assertEquals(4L, metricsMapRef.size(), "Referenced metrics map should have 4 entries")
    assertEquals("2024-01-01T08:30:00Z", metricsMapRef.get("lastLoginTime"), "Referenced metrics should match nested metrics")

    // Verify that references point to the same objects
    assertEquals(preferences.get("theme"), preferencesMapRef.get("theme"), "Preference references should point to same data")
    assertEquals(metrics.get("profileViews"), metricsMapRef.get("profileViews"), "Metrics references should point to same data")
  }

  /**
   * Tests sequential map operations including creation with initial data, updating existing fields,
   * adding new fields, and removing fields. Validates the resulting data after each operation.
   */
  @Test
  fun testLiveMapOperations() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    // Step 1: Create a new map with initial data
    val testMapObjectId = restObjects.createMap(
      channelName,
      data = mapOf(
        "name" to ObjectData(value = ObjectValue("Alice")),
        "age" to ObjectData(value = ObjectValue(30)),
        "isActive" to ObjectData(value = ObjectValue(true))
      )
    )
    restObjects.setMapRef(channelName, "root", "testMap", testMapObjectId)

    // wait for updated testMap to be available in the root map
    assertWaiter { rootMap.get("testMap") != null }

    // Assert initial state after creation
    val testMap = rootMap.get("testMap") as LiveMap
    assertNotNull(testMap, "Test map should be created and accessible")
    assertEquals(3L, testMap.size(), "Test map should have 3 initial entries")
    assertEquals("Alice", testMap.get("name"), "Initial name should be Alice")
    assertEquals(30.0, testMap.get("age"), "Initial age should be 30")
    assertEquals(true, testMap.get("isActive"), "Initial active status should be true")

    // Step 2: Update an existing field (name from "Alice" to "Bob")
    restObjects.setMapValue(channelName, testMapObjectId, "name", ObjectValue("Bob"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("name") == "Bob" }

    // Assert after updating existing field
    assertEquals(3L, testMap.size(), "Map size should remain the same after update")
    assertEquals("Bob", testMap.get("name"), "Name should be updated to Bob")
    assertEquals(30.0, testMap.get("age"), "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive"), "Active status should remain unchanged")

    // Step 3: Add a new field (email)
    restObjects.setMapValue(channelName, testMapObjectId, "email", ObjectValue("bob@example.com"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("email") == "bob@example.com" }

    // Assert after adding new field
    assertEquals(4L, testMap.size(), "Map size should increase after adding new field")
    assertEquals("Bob", testMap.get("name"), "Name should remain Bob")
    assertEquals(30.0, testMap.get("age"), "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive"), "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email"), "Email should be added successfully")

    // Step 4: Add another new field with different data type (score as number)
    restObjects.setMapValue(channelName, testMapObjectId, "score", ObjectValue(85))
    // Wait for the map to be updated
    assertWaiter { testMap.get("score") == 85.0 }

    // Assert after adding second new field
    assertEquals(5L, testMap.size(), "Map size should increase to 5 after adding score")
    assertEquals("Bob", testMap.get("name"), "Name should remain Bob")
    assertEquals(30.0, testMap.get("age"), "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive"), "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email"), "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score"), "Score should be added as numeric value")

    // Step 5: Update the boolean field
    restObjects.setMapValue(channelName, testMapObjectId, "isActive", ObjectValue(false))
    // Wait for the map to be updated
    assertWaiter { testMap.get("isActive") == false }

    // Assert after updating boolean field
    assertEquals(5L, testMap.size(), "Map size should remain 5 after boolean update")
    assertEquals("Bob", testMap.get("name"), "Name should remain Bob")
    assertEquals(30.0, testMap.get("age"), "Age should remain unchanged")
    assertEquals(false, testMap.get("isActive"), "Active status should be updated to false")
    assertEquals("bob@example.com", testMap.get("email"), "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score"), "Score should remain unchanged")

    // Step 6: Remove a field (age)
    restObjects.removeMapValue(channelName, testMapObjectId, "age")
    // Wait for the map to be updated
    assertWaiter { testMap.get("age") == null }

    // Assert after removing field
    assertEquals(4L, testMap.size(), "Map size should decrease to 4 after removing age")
    assertEquals("Bob", testMap.get("name"), "Name should remain Bob")
    assertNull(testMap.get("age"), "Age should be removed and return null")
    assertEquals(false, testMap.get("isActive"), "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email"), "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score"), "Score should remain unchanged")

    // Step 7: Remove another field (score)
    restObjects.removeMapValue(channelName, testMapObjectId, "score")
    // Wait for the map to be updated
    assertWaiter { testMap.get("score") == null }

    // Assert final state after second removal
    assertEquals(3L, testMap.size(), "Map size should decrease to 3 after removing score")
    assertEquals("Bob", testMap.get("name"), "Name should remain Bob")
    assertEquals(false, testMap.get("isActive"), "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email"), "Email should remain unchanged")
    assertNull(testMap.get("score"), "Score should be removed and return null")
    assertNull(testMap.get("age"), "Age should remain null")

    // Final verification - ensure all expected keys exist and unwanted keys don't
    assertEquals(3, testMap.size(), "Final map should have exactly 3 entries")

    val finalKeys = testMap.keys().toSet()
    assertEquals(setOf("name", "isActive", "email"), finalKeys, "Final keys should match expected set")

    val finalValues = testMap.values().toSet()
    assertEquals(setOf("Bob", false, "bob@example.com"), finalValues, "Final values should match expected set")
  }
}
