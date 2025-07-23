package io.ably.lib.objects.integration.helpers.fixtures

import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectValue
import io.ably.lib.objects.integration.helpers.RestObjects

/**
 * Initializes a comprehensive test fixture object tree on the specified channel.
 *
 * This method creates a predetermined object hierarchy rooted at a "root" map object,
 * establishing references between different types of live objects to enable comprehensive testing.
 *
 * **Object Tree Structure:**
 * ```
 * root (Map)
 * ├── emptyCounter → Counter(value=0)
 * ├── initialValueCounter → Counter(value=10)
 * ├── referencedCounter → Counter(value=20)
 * ├── emptyMap → Map{}
 * ├── referencedMap → Map{
 * │   └── "counterKey" → referencedCounter
 * │   }
 * └── valuesMap → Map{
 *     ├── "string" → "stringValue"
 *     ├── "emptyString" → ""
 *     ├── "bytes" → <binary data>
 *     ├── "emptyBytes" → <empty binary>
 *     ├── "maxSafeInteger" → Long.MAX_VALUE
 *     ├── "negativeMaxSafeInteger" → Long.MIN_VALUE
 *     ├── "number" → 1
 *     ├── "zero" → 0
 *     ├── "true" → true
 *     ├── "false" → false
 *     ├── "object" → {"foo": "bar"}
 *     ├── "array" → ["foo", "bar", "baz"]
 *     └── "mapRef" → referencedMap
 *     }
 * ```
 *
 * @param channelName The channel where the object tree will be created
 */
internal fun RestObjects.initializeRootMap(channelName: String) {
  // Create counters
  val emptyCounterObjectId = createCounter(channelName)
  setMapRef(channelName, "root", "emptyCounter", emptyCounterObjectId)

  val initialValueCounterObjectId = createCounter(channelName, 10.0)
  setMapRef(channelName, "root", "initialValueCounter", initialValueCounterObjectId)

  val referencedCounterObjectId = createCounter(channelName, 20.0)
  setMapRef(channelName, "root", "referencedCounter", referencedCounterObjectId)

  // Create maps
  val emptyMapObjectId = createMap(channelName)
  setMapRef(channelName, "root", "emptyMap", emptyMapObjectId)

  val referencedMapObjectId = createMap(
    channelName,
    data = mapOf("counterKey" to DataFixtures.mapRef(referencedCounterObjectId))
  )
  setMapRef(channelName, "root", "referencedMap", referencedMapObjectId)

  val valuesMapObjectId = createMap(
    channelName,
    data = DataFixtures.mapWithAllValues(referencedMapObjectId)
  )
  setMapRef(channelName, "root", "valuesMap", valuesMapObjectId)
}


/**
 * Creates a comprehensive test fixture object tree on the specified channel using
 *
 * This method establishes a hierarchical structure of live objects for testing map operations,
 * creating various types of objects and establishing references between them.
 *
 * **Object Tree Structure:**
 * ```
 * testMap (Map)
 * ├── userProfile → Map{
 * │   ├── "userId" → "user123"
 * │   ├── "name" → "John Doe"
 * │   ├── "email" → "john@example.com"
 * │   ├── "isActive" → true
 * │   ├── "metrics" → metricsMap
 * │   └── "preferences" → preferencesMap
 * │   }
 * ├── loginCounter → Counter(value=5)
 * ├── sessionCounter → Counter(value=0)
 * ├── preferencesMap → Map{
 * │   ├── "theme" → "dark"
 * │   ├── "notifications" → true
 * │   ├── "language" → "en"
 * │   └── "maxRetries" → 3
 * │   }
 * └── metricsMap → Map{
 *     ├── "totalLogins" → loginCounter
 *     ├── "activeSessions" → sessionCounter
 *     ├── "lastLoginTime" → "2024-01-01T08:30:00Z"
 *     └── "profileViews" → 42
 *     }
 * ```
 *
 * @param channelName The channel where the test object tree will be created
 */
internal fun RestObjects.createUserMapObject(channelName: String): String {
  // Create the main test map first
  val testMapObjectId = createMap(channelName)

  // Create counter objects for testing numeric operations
  val loginCounterObjectId = createCounter(channelName, 5.0)
  val sessionCounterObjectId = createCounter(channelName, 0.0)

  // Create a preferences map with various data types
  val preferencesMapObjectId = createMap(
    channelName,
    data = mapOf(
      "theme" to ObjectData(value = ObjectValue("dark")),
      "notifications" to ObjectData(value = ObjectValue(true)),
      "language" to ObjectData(value = ObjectValue("en")),
      "maxRetries" to ObjectData(value = ObjectValue(3))
    )
  )

  // Create a metrics map that tracks single user activity
  val metricsMapObjectId = createMap(
    channelName,
    data = mapOf(
      "totalLogins" to DataFixtures.mapRef(loginCounterObjectId),
      "activeSessions" to DataFixtures.mapRef(sessionCounterObjectId),
      "lastLoginTime" to ObjectData(value = ObjectValue("2024-01-01T08:30:00Z")),
      "profileViews" to ObjectData(value = ObjectValue(42))
    )
  )

  // Create a user profile map with mixed data types and references
  val userProfileMapObjectId = createUserProfileMapObject(channelName)
  setMapRef(channelName, userProfileMapObjectId, "metrics", metricsMapObjectId)
  setMapRef(channelName, userProfileMapObjectId, "preferences", preferencesMapObjectId)

  // Set up the main test map structure with references to all created objects
  setMapRef(channelName, testMapObjectId, "userProfile", userProfileMapObjectId)
  setMapRef(channelName, testMapObjectId, "loginCounter", loginCounterObjectId)
  setMapRef(channelName, testMapObjectId, "sessionCounter", sessionCounterObjectId)
  setMapRef(channelName, testMapObjectId, "preferencesMap", preferencesMapObjectId)
  setMapRef(channelName, testMapObjectId, "metricsMap", metricsMapObjectId)

  return testMapObjectId
}

/**
 * Creates a user profile map object with basic user information for testing.
 *
 * This method creates a simple user profile map containing essential user data fields
 * that are commonly used in user management systems. The map contains primitive data types
 * representing basic user information.
 *
 * **Object Structure:**
 * ```
 * userProfileMap (Map)
 * ├── "userId" → "user123"
 * ├── "name" → "John Doe"
 * ├── "email" → "john@example.com"
 * └── "isActive" → true
 * ```
 *
 * This structure provides a foundation for testing map operations on user profile data,
 * including field updates, additions, and removals. The map contains a mix of string,
 * boolean, and numeric data types to test various primitive value handling.
 *
 * @param channelName The channel where the user profile map will be created
 * @return The object ID of the created user profile map
 */
internal fun RestObjects.createUserProfileMapObject(channelName: String): String {
   return createMap(
    channelName,
    data = mapOf(
      "userId" to ObjectData(value = ObjectValue("user123")),
      "name" to ObjectData(value = ObjectValue("John Doe")),
      "email" to ObjectData(value = ObjectValue("john@example.com")),
      "isActive" to ObjectData(value = ObjectValue(true)),
    )
  )
}
