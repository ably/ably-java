package io.ably.lib.objects.integration

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectValue
import io.ably.lib.objects.integration.helpers.fixtures.createUserMapObject
import io.ably.lib.objects.integration.helpers.fixtures.createUserProfileMapObject
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.objects.type.map.LiveMapValue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    val userMap = rootMap.get("user")?.asLiveMap
    assertNotNull(userMap, "User map should be synchronized")
    assertEquals(5L, userMap.size(), "User map should contain 5 top-level entries")

    // Assert Counter Objects
    // Test loginCounter - should have initial value of 5
    val loginCounter = userMap.get("loginCounter")?.asLiveCounter
    assertNotNull(loginCounter, "Login counter should exist")
    assertEquals(5.0, loginCounter.value(), "Login counter should have initial value of 5")

    // Test sessionCounter - should have initial value of 0
    val sessionCounter = userMap.get("sessionCounter")?.asLiveCounter
    assertNotNull(sessionCounter, "Session counter should exist")
    assertEquals(0.0, sessionCounter.value(), "Session counter should have initial value of 0")

    // Assert User Profile Map
    val userProfile = userMap.get("userProfile")?.asLiveMap
    assertNotNull(userProfile, "User profile map should exist")
    assertEquals(6L, userProfile.size(), "User profile should contain 6 entries")

    // Assert user profile primitive values
    assertEquals("user123", userProfile.get("userId")?.asString, "User ID should match expected value")
    assertEquals("John Doe", userProfile.get("name")?.asString, "User name should match expected value")
    assertEquals("john@example.com", userProfile.get("email")?.asString, "User email should match expected value")
    assertEquals(true, userProfile.get("isActive")?.asBoolean, "User should be active")

    // Assert Preferences Map (nested within user profile)
    val preferences = userProfile.get("preferences")?.asLiveMap
    assertNotNull(preferences, "Preferences map should exist")
    assertEquals(4L, preferences.size(), "Preferences should contain 4 entries")
    assertEquals("dark", preferences.get("theme")?.asString, "Theme preference should be dark")
    assertEquals(true, preferences.get("notifications")?.asBoolean, "Notifications should be enabled")
    assertEquals("en", preferences.get("language")?.asString, "Language should be English")
    assertEquals(3.0, preferences.get("maxRetries")?.asNumber, "Max retries should be 3")

    // Assert Metrics Map (nested within user profile)
    val metrics = userProfile.get("metrics")?.asLiveMap
    assertNotNull(metrics, "Metrics map should exist")
    assertEquals(4L, metrics.size(), "Metrics should contain 4 entries")
    assertEquals("2024-01-01T08:30:00Z", metrics.get("lastLoginTime")?.asString, "Last login time should match")
    assertEquals(42.0, metrics.get("profileViews")?.asNumber, "Profile views should be 42")

    // Test counter references within metrics map
    val totalLoginsCounter = metrics.get("totalLogins")?.asLiveCounter
    assertNotNull(totalLoginsCounter, "Total logins counter should exist")
    assertEquals(5.0, totalLoginsCounter.value(), "Total logins should reference login counter with value 5")

    val activeSessionsCounter = metrics.get("activeSessions")?.asLiveCounter
    assertNotNull(activeSessionsCounter, "Active sessions counter should exist")
    assertEquals(0.0, activeSessionsCounter.value(), "Active sessions should reference session counter with value 0")

    // Assert direct references to maps from top-level user map
    val preferencesMapRef = userMap.get("preferencesMap")?.asLiveMap
    assertNotNull(preferencesMapRef, "Preferences map reference should exist")
    assertEquals(4L, preferencesMapRef.size(), "Referenced preferences map should have 4 entries")
    assertEquals("dark", preferencesMapRef.get("theme")?.asString, "Referenced preferences should match nested preferences")

    val metricsMapRef = userMap.get("metricsMap")?.asLiveMap
    assertNotNull(metricsMapRef, "Metrics map reference should exist")
    assertEquals(4L, metricsMapRef.size(), "Referenced metrics map should have 4 entries")
    assertEquals("2024-01-01T08:30:00Z", metricsMapRef.get("lastLoginTime")?.asString, "Referenced metrics should match nested metrics")

    // Verify that references point to the same objects
    assertEquals(preferences.get("theme")?.asString, preferencesMapRef.get("theme")?.asString, "Preference references should point to same data")
    assertEquals(metrics.get("profileViews")?.asNumber, metricsMapRef.get("profileViews")?.asNumber, "Metrics references should point to same data")
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
        "name" to ObjectData(value = ObjectValue.String("Alice")),
        "age" to ObjectData(value = ObjectValue.Number(30)),
        "isActive" to ObjectData(value = ObjectValue.Boolean(true))
      )
    )
    restObjects.setMapRef(channelName, "root", "testMap", testMapObjectId)

    // wait for updated testMap to be available in the root map
    assertWaiter { rootMap.get("testMap") != null }

    // Assert initial state after creation
    val testMap = rootMap.get("testMap")?.asLiveMap
    assertNotNull(testMap, "Test map should be created and accessible")
    assertEquals(3L, testMap.size(), "Test map should have 3 initial entries")
    assertEquals("Alice", testMap.get("name")?.asString, "Initial name should be Alice")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Initial age should be 30")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Initial active status should be true")

    // Step 2: Update an existing field (name from "Alice" to "Bob")
    restObjects.setMapValue(channelName, testMapObjectId, "name", ObjectValue.String("Bob"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("name")?.asString == "Bob" }

    // Assert after updating existing field
    assertEquals(3L, testMap.size(), "Map size should remain the same after update")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should be updated to Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")

    // Step 3: Add a new field (email)
    restObjects.setMapValue(channelName, testMapObjectId, "email", ObjectValue.String("bob@example.com"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("email")?.asString == "bob@example.com" }

    // Assert after adding new field
    assertEquals(4L, testMap.size(), "Map size should increase after adding new field")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should be added successfully")

    // Step 4: Add another new field with different data type (score as number)
    restObjects.setMapValue(channelName, testMapObjectId, "score", ObjectValue.Number(85))
    // Wait for the map to be updated
    assertWaiter { testMap.get("score")?.asNumber == 85.0 }

    // Assert after adding second new field
    assertEquals(5L, testMap.size(), "Map size should increase to 5 after adding score")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should be added as numeric value")

    // Step 5: Update the boolean field
    restObjects.setMapValue(channelName, testMapObjectId, "isActive", ObjectValue.Boolean(false))
    // Wait for the map to be updated
    assertWaiter { testMap.get("isActive")?.asBoolean == false }

    // Assert after updating boolean field
    assertEquals(5L, testMap.size(), "Map size should remain 5 after boolean update")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should be updated to false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should remain unchanged")

    // Step 6: Remove a field (age)
    restObjects.removeMapValue(channelName, testMapObjectId, "age")
    // Wait for the map to be updated
    assertWaiter { testMap.get("age") == null }

    // Assert after removing field
    assertEquals(4L, testMap.size(), "Map size should decrease to 4 after removing age")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertNull(testMap.get("age"), "Age should be removed and return null")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should remain unchanged")

    // Step 7: Remove another field (score)
    restObjects.removeMapValue(channelName, testMapObjectId, "score")
    // Wait for the map to be updated
    assertWaiter { testMap.get("score") == null }

    // Assert final state after second removal
    assertEquals(3L, testMap.size(), "Map size should decrease to 3 after removing score")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertNull(testMap.get("score"), "Score should be removed and return null")
    assertNull(testMap.get("age"), "Age should remain null")

    // Final verification - ensure all expected keys exist and unwanted keys don't
    assertEquals(3, testMap.size(), "Final map should have exactly 3 entries")

    val finalKeys = testMap.keys().toSet()
    assertEquals(setOf("name", "isActive", "email"), finalKeys, "Final keys should match expected set")

    val finalValues = testMap.values().map { it.value }.toSet()
    assertEquals(setOf("Bob", false, "bob@example.com"), finalValues, "Final string values should match expected set")
  }

  /**
   * Tests sequential map operations including creation with initial data, updating existing fields,
   * adding new fields, and removing fields. Validates the resulting data after each operation.
   */
  @Test
  fun testLiveMapOperationsUsingRealtime() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    val rootMap = channel.objects.root

    // Step 1: Create a new map with initial data
    val testMapObject = objects.createMap(
      mapOf(
        "name" to LiveMapValue.of("Alice"),
        "age" to LiveMapValue.of(30),
        "isActive" to LiveMapValue.of(true),
      )
    )
    rootMap.set("testMap", LiveMapValue.of(testMapObject))

    // wait for updated testMap to be available in the root map
    assertWaiter { rootMap.get("testMap") != null }

    // Assert initial state after creation
    val testMap = rootMap.get("testMap")?.asLiveMap
    assertNotNull(testMap, "Test map should be created and accessible")
    assertEquals(3L, testMap.size(), "Test map should have 3 initial entries")
    assertEquals("Alice", testMap.get("name")?.asString, "Initial name should be Alice")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Initial age should be 30")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Initial active status should be true")

    // Step 2: Update an existing field (name from "Alice" to "Bob")
    testMap.set("name", LiveMapValue.of("Bob"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("name")?.asString == "Bob" }

    // Assert after updating existing field
    assertEquals(3L, testMap.size(), "Map size should remain the same after update")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should be updated to Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")

    // Step 3: Add a new field (email)
    testMap.set("email", LiveMapValue.of("bob@example.com"))
    // Wait for the map to be updated
    assertWaiter { testMap.get("email")?.asString == "bob@example.com" }

    // Assert after adding new field
    assertEquals(4L, testMap.size(), "Map size should increase after adding new field")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should be added successfully")

    // Step 4: Add another new field with different data type (score as number)
    testMap.set("score", LiveMapValue.of(85))
    // Wait for the map to be updated
    assertWaiter { testMap.get("score")?.asNumber == 85.0 }

    // Assert after adding second new field
    assertEquals(5L, testMap.size(), "Map size should increase to 5 after adding score")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(true, testMap.get("isActive")?.asBoolean, "Active status should remain unchanged")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should be added as numeric value")

    // Step 5: Update the boolean field
    testMap.set("isActive", LiveMapValue.of(false))
    // Wait for the map to be updated
    assertWaiter { testMap.get("isActive")?.asBoolean == false }

    // Assert after updating boolean field
    assertEquals(5L, testMap.size(), "Map size should remain 5 after boolean update")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(30.0, testMap.get("age")?.asNumber, "Age should remain unchanged")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should be updated to false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should remain unchanged")

    // Step 6: Remove a field (age)
    testMap.remove("age")
    // Wait for the map to be updated
    assertWaiter { testMap.get("age") == null }

    // Assert after removing field
    assertEquals(4L, testMap.size(), "Map size should decrease to 4 after removing age")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertNull(testMap.get("age"), "Age should be removed and return null")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertEquals(85.0, testMap.get("score")?.asNumber, "Score should remain unchanged")

    // Step 7: Remove another field (score)
    testMap.remove("score")
    // Wait for the map to be updated
    assertWaiter { testMap.get("score") == null }

    // Assert final state after second removal
    assertEquals(3L, testMap.size(), "Map size should decrease to 3 after removing score")
    assertEquals("Bob", testMap.get("name")?.asString, "Name should remain Bob")
    assertEquals(false, testMap.get("isActive")?.asBoolean, "Active status should remain false")
    assertEquals("bob@example.com", testMap.get("email")?.asString, "Email should remain unchanged")
    assertNull(testMap.get("score"), "Score should be removed and return null")
    assertNull(testMap.get("age"), "Age should remain null")

    // Final verification - ensure all expected keys exist and unwanted keys don't
    assertEquals(3, testMap.size(), "Final map should have exactly 3 entries")

    val finalKeys = testMap.keys().toSet()
    assertEquals(setOf("name", "isActive", "email"), finalKeys, "Final keys should match expected set")

    val finalValues = testMap.values().map { it.value }.toSet()
    assertEquals(setOf("Bob", false, "bob@example.com"), finalValues, "Final string values should match expected set")
  }

  @Test
  fun testLiveMapChangesUsingSubscription() = runTest {
    val channelName = generateChannelName()
    val userProfileObjectId = restObjects.createUserProfileMapObject(channelName)
    restObjects.setMapRef(channelName, "root", "userProfile", userProfileObjectId)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    // Get the user profile map object from the root map
    val userProfile = rootMap.get("userProfile")?.asLiveMap
    assertNotNull(userProfile, "User profile should be synchronized")
    assertEquals(4L, userProfile.size(), "User profile should contain 4 entries")

    // Verify initial values
    assertEquals("user123", userProfile.get("userId")?.asString, "Initial userId should be user123")
    assertEquals("John Doe", userProfile.get("name")?.asString, "Initial name should be John Doe")
    assertEquals("john@example.com", userProfile.get("email")?.asString, "Initial email should be john@example.com")
    assertEquals(true, userProfile.get("isActive")?.asBoolean, "Initial isActive should be true")

    // Subscribe to changes in the user profile map
    val userProfileUpdates = mutableListOf<LiveMapUpdate>()
    val userProfileSubscription = userProfile.subscribe { update -> userProfileUpdates.add(update) }

    // Step 1: Update an existing field in the user profile map (change the name)
    restObjects.setMapValue(channelName, userProfileObjectId, "name", ObjectValue.String("Bob Smith"))

    // Wait for the update to be received
    assertWaiter { userProfileUpdates.isNotEmpty() }

    // Verify the update was received
    assertEquals(1, userProfileUpdates.size, "Should receive one update")
    val firstUpdateMap = userProfileUpdates.first().update
    assertEquals(1, firstUpdateMap.size, "Should have one key change")
    assertTrue(firstUpdateMap.containsKey("name"), "Update should contain name key")
    assertEquals(LiveMapUpdate.Change.UPDATED, firstUpdateMap["name"], "name should be marked as UPDATED")

    // Verify the value was actually updated
    assertEquals("Bob Smith", userProfile.get("name")?.asString, "Name should be updated to Bob Smith")

    // Step 2: Update another field in the user profile map (change the email)
    userProfileUpdates.clear()
    restObjects.setMapValue(channelName, userProfileObjectId, "email", ObjectValue.String("bob@example.com"))

    // Wait for the second update
    assertWaiter { userProfileUpdates.isNotEmpty() }

    // Verify the second update
    assertEquals(1, userProfileUpdates.size, "Should receive one update for the second change")
    val secondUpdateMap = userProfileUpdates.first().update
    assertEquals(1, secondUpdateMap.size, "Should have one key change")
    assertTrue(secondUpdateMap.containsKey("email"), "Update should contain email key")
    assertEquals(LiveMapUpdate.Change.UPDATED, secondUpdateMap["email"], "email should be marked as UPDATED")

    // Verify the value was actually updated
    assertEquals("bob@example.com", userProfile.get("email")?.asString, "Email should be updated to bob@example.com")

    // Step 3: Remove an existing field from the user profile map (remove isActive)
    userProfileUpdates.clear()
    restObjects.removeMapValue(channelName, userProfileObjectId, "isActive")

    // Wait for the removal update
    assertWaiter { userProfileUpdates.isNotEmpty() }

    // Verify the removal update
    assertEquals(1, userProfileUpdates.size, "Should receive one update for removal")
    val removalUpdateMap = userProfileUpdates.first().update
    assertEquals(1, removalUpdateMap.size, "Should have one key change")
    assertTrue(removalUpdateMap.containsKey("isActive"), "Update should contain isActive key")
    assertEquals(LiveMapUpdate.Change.REMOVED, removalUpdateMap["isActive"], "isActive should be marked as REMOVED")

    // Verify final state of the user profile map
    assertEquals(3L, userProfile.size(), "User profile should have 3 entries after removing isActive")
    assertEquals("user123", userProfile.get("userId")?.asString, "userId should remain unchanged")
    assertEquals("Bob Smith", userProfile.get("name")?.asString, "name should remain updated")
    assertEquals("bob@example.com", userProfile.get("email")?.asString, "email should remain updated")
    assertNull(userProfile.get("isActive"), "isActive should be removed")

    // Clean up subscription
    userProfileUpdates.clear()
    userProfileSubscription.unsubscribe()
    // No updates should be received after unsubscribing
    restObjects.setMapValue(channelName, userProfileObjectId, "country", ObjectValue.String("uk"))

    // Wait for a moment to ensure no updates are received
    assertWaiter { userProfile.size() == 4L }

    assertTrue(userProfileUpdates.isEmpty(), "No updates should be received after unsubscribing")
  }
}
