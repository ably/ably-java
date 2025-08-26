package io.ably.lib.objects.integration

import io.ably.lib.objects.assertWaiter
import io.ably.lib.objects.integration.helpers.ObjectId
import io.ably.lib.objects.integration.helpers.fixtures.createUserEngagementMatrixMap
import io.ably.lib.objects.integration.helpers.fixtures.createUserMapWithCountersObject
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.type.map.LiveMapValue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultLiveCounterTest: IntegrationTest() {
  /**
   * Tests the synchronization process when a user map object with counters is initialized before channel attach.
   * This includes checking the initial values of all counter objects and nested maps in the
   * comprehensive user engagement counter structure.
   */
  @Test
  fun testLiveCounterSync() = runTest {
    val channelName = generateChannelName()
    val userMapObjectId = restObjects.createUserMapWithCountersObject(channelName)
    restObjects.setMapRef(channelName, "root", "user", userMapObjectId)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    // Get the user map object from the root map
    val userMap = rootMap.get("user")?.asLiveMap
    assertNotNull(userMap, "User map should be synchronized")
    assertEquals(7L, userMap.size(), "User map should contain 7 top-level entries")

    // Assert direct counter objects at the top level of the user map
    // Test profileViews counter - should have initial value of 127
    val profileViewsCounter = userMap.get("profileViews")?.asLiveCounter
    assertNotNull(profileViewsCounter, "Profile views counter should exist")
    assertEquals(127.0, profileViewsCounter.value(), "Profile views counter should have initial value of 127")

    // Test postLikes counter - should have initial value of 45
    val postLikesCounter = userMap.get("postLikes")?.asLiveCounter
    assertNotNull(postLikesCounter, "Post likes counter should exist")
    assertEquals(45.0, postLikesCounter.value(), "Post likes counter should have initial value of 45")

    // Test commentCount counter - should have initial value of 23
    val commentCountCounter = userMap.get("commentCount")?.asLiveCounter
    assertNotNull(commentCountCounter, "Comment count counter should exist")
    assertEquals(23.0, commentCountCounter.value(), "Comment count counter should have initial value of 23")

    // Test followingCount counter - should have initial value of 89
    val followingCountCounter = userMap.get("followingCount")?.asLiveCounter
    assertNotNull(followingCountCounter, "Following count counter should exist")
    assertEquals(89.0, followingCountCounter.value(), "Following count counter should have initial value of 89")

    // Test followersCount counter - should have initial value of 156
    val followersCountCounter = userMap.get("followersCount")?.asLiveCounter
    assertNotNull(followersCountCounter, "Followers count counter should exist")
    assertEquals(156.0, followersCountCounter.value(), "Followers count counter should have initial value of 156")

    // Test loginStreak counter - should have initial value of 7
    val loginStreakCounter = userMap.get("loginStreak")?.asLiveCounter
    assertNotNull(loginStreakCounter, "Login streak counter should exist")
    assertEquals(7.0, loginStreakCounter.value(), "Login streak counter should have initial value of 7")

    // Assert the nested engagement metrics map
    val engagementMetrics = userMap.get("engagementMetrics")?.asLiveMap
    assertNotNull(engagementMetrics, "Engagement metrics map should exist")
    assertEquals(4L, engagementMetrics.size(), "Engagement metrics map should contain 4 counter entries")

    // Assert counter objects within the engagement metrics map
    // Test totalShares counter - should have initial value of 34
    val totalSharesCounter = engagementMetrics.get("totalShares")?.asLiveCounter
    assertNotNull(totalSharesCounter, "Total shares counter should exist")
    assertEquals(34.0, totalSharesCounter.value(), "Total shares counter should have initial value of 34")

    // Test totalBookmarks counter - should have initial value of 67
    val totalBookmarksCounter = engagementMetrics.get("totalBookmarks")?.asLiveCounter
    assertNotNull(totalBookmarksCounter, "Total bookmarks counter should exist")
    assertEquals(67.0, totalBookmarksCounter.value(), "Total bookmarks counter should have initial value of 67")

    // Test totalReactions counter - should have initial value of 189
    val totalReactionsCounter = engagementMetrics.get("totalReactions")?.asLiveCounter
    assertNotNull(totalReactionsCounter, "Total reactions counter should exist")
    assertEquals(189.0, totalReactionsCounter.value(), "Total reactions counter should have initial value of 189")

    // Test dailyActiveStreak counter - should have initial value of 12
    val dailyActiveStreakCounter = engagementMetrics.get("dailyActiveStreak")?.asLiveCounter
    assertNotNull(dailyActiveStreakCounter, "Daily active streak counter should exist")
    assertEquals(12.0, dailyActiveStreakCounter.value(), "Daily active streak counter should have initial value of 12")

    // Verify that all expected counter keys exist at the top level
    val topLevelKeys = userMap.keys().toSet()
    val expectedTopLevelKeys = setOf(
      "profileViews", "postLikes", "commentCount", "followingCount",
      "followersCount", "loginStreak", "engagementMetrics"
    )
    assertEquals(expectedTopLevelKeys, topLevelKeys, "Top-level keys should match expected counter keys")

    // Verify that all expected counter keys exist in the engagement metrics map
    val engagementKeys = engagementMetrics.keys().toSet()
    val expectedEngagementKeys = setOf(
      "totalShares", "totalBookmarks", "totalReactions", "dailyActiveStreak"
    )
    assertEquals(expectedEngagementKeys, engagementKeys, "Engagement metrics keys should match expected counter keys")

    // Verify total counter values match expectations (useful for integration testing)
    val totalUserCounterValues = listOf(127.0, 45.0, 23.0, 89.0, 156.0, 7.0).sum()
    val totalEngagementCounterValues = listOf(34.0, 67.0, 189.0, 12.0).sum()
    assertEquals(447.0, totalUserCounterValues, "Sum of user counter values should be 447")
    assertEquals(302.0, totalEngagementCounterValues, "Sum of engagement counter values should be 302")
  }

  /**
   * Tests sequential counter operations including creation with initial value, incrementing by various amounts,
   * decrementing by various amounts, and validates the resulting counter value after each operation.
   */
  @Test
  fun testLiveCounterOperations() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    // Step 1: Create a new counter with initial value of 10
    val testCounterObjectId = restObjects.createCounter(channelName, initialValue = 10.0)
    restObjects.setMapRef(channelName, "root", "testCounter", testCounterObjectId)

    // Wait for updated testCounter to be available in the root map
    assertWaiter { rootMap.get("testCounter") != null }

    // Assert initial state after creation
    val testCounter = rootMap.get("testCounter")?.asLiveCounter
    assertNotNull(testCounter, "Test counter should be created and accessible")
    assertEquals(10.0, testCounter.value(), "Counter should have initial value of 10")

    // Step 2: Increment counter by 5 (10 + 5 = 15)
    restObjects.incrementCounter(channelName, testCounterObjectId, 5.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 15.0 }

    // Assert after first increment
    assertEquals(15.0, testCounter.value(), "Counter should be incremented to 15")

    // Step 3: Increment counter by 3 (15 + 3 = 18)
    restObjects.incrementCounter(channelName, testCounterObjectId, 3.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 18.0 }

    // Assert after second increment
    assertEquals(18.0, testCounter.value(), "Counter should be incremented to 18")

    // Step 4: Increment counter by a larger amount: 12 (18 + 12 = 30)
    restObjects.incrementCounter(channelName, testCounterObjectId, 12.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 30.0 }

    // Assert after third increment
    assertEquals(30.0, testCounter.value(), "Counter should be incremented to 30")

    // Step 5: Decrement counter by 7 (30 - 7 = 23)
    restObjects.decrementCounter(channelName, testCounterObjectId, 7.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 23.0 }

    // Assert after first decrement
    assertEquals(23.0, testCounter.value(), "Counter should be decremented to 23")

    // Step 6: Decrement counter by 4 (23 - 4 = 19)
    restObjects.decrementCounter(channelName, testCounterObjectId, 4.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 19.0 }

    // Assert after second decrement
    assertEquals(19.0, testCounter.value(), "Counter should be decremented to 19")

    // Step 7: Increment counter by 1 (19 + 1 = 20)
    restObjects.incrementCounter(channelName, testCounterObjectId, 1.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 20.0 }

    // Assert after final increment
    assertEquals(20.0, testCounter.value(), "Counter should be incremented to 20")

    // Step 8: Decrement counter by a larger amount: 15 (20 - 15 = 5)
    restObjects.decrementCounter(channelName, testCounterObjectId, 15.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 5.0 }

    // Assert after large decrement
    assertEquals(5.0, testCounter.value(), "Counter should be decremented to 5")

    // Final verification - test final increment to ensure counter still works
    restObjects.incrementCounter(channelName, testCounterObjectId, 25.0)
    assertWaiter { testCounter.value() == 30.0 }

    // Assert final state
    assertEquals(30.0, testCounter.value(), "Counter should have final value of 30")

    // Verify the counter object is still accessible and functioning
    assertNotNull(testCounter, "Counter should still be accessible at the end")

    // Verify we can still access it from the root map
    val finalCounterCheck = rootMap.get("testCounter")?.asLiveCounter
    assertNotNull(finalCounterCheck, "Counter should still be accessible from root map")
    assertEquals(30.0, finalCounterCheck.value(), "Final counter value should be 30 when accessed from root map")
  }

  @Test
  fun testLiveCounterOperationsUsingRealtime() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    val rootMap = channel.objects.root

    // Step 1: Create a new counter with initial value of 10
    val testCounterObject = objects.createCounter( 10.0)
    rootMap.set("testCounter", LiveMapValue.of(testCounterObject))

    // Wait for updated testCounter to be available in the root map
    assertWaiter { rootMap.get("testCounter") != null }

    // Assert initial state after creation
    val testCounter = rootMap.get("testCounter")?.asLiveCounter
    assertNotNull(testCounter, "Test counter should be created and accessible")
    assertEquals(10.0, testCounter.value(), "Counter should have initial value of 10")

    // Step 2: Increment counter by 5 (10 + 5 = 15)
    testCounter.increment(5.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 15.0 }

    // Assert after first increment
    assertEquals(15.0, testCounter.value(), "Counter should be incremented to 15")

    // Step 3: Increment counter by 3 (15 + 3 = 18)
    testCounter.increment(3.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 18.0 }

    // Assert after second increment
    assertEquals(18.0, testCounter.value(), "Counter should be incremented to 18")

    // Step 4: Increment counter by a larger amount: 12 (18 + 12 = 30)
    testCounter.increment(12.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 30.0 }

    // Assert after third increment
    assertEquals(30.0, testCounter.value(), "Counter should be incremented to 30")

    // Step 5: Decrement counter by 7 (30 - 7 = 23)
    testCounter.decrement(7.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 23.0 }

    // Assert after first decrement
    assertEquals(23.0, testCounter.value(), "Counter should be decremented to 23")

    // Step 6: Decrement counter by 4 (23 - 4 = 19)
    testCounter.decrement(4.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 19.0 }

    // Assert after second decrement
    assertEquals(19.0, testCounter.value(), "Counter should be decremented to 19")

    // Step 7: Increment counter by 1 (19 + 1 = 20)
    testCounter.increment(1.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 20.0 }

    // Assert after final increment
    assertEquals(20.0, testCounter.value(), "Counter should be incremented to 20")

    // Step 8: Decrement counter by a larger amount: 15 (20 - 15 = 5)
    testCounter.decrement(15.0)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 5.0 }

    // Assert after large decrement
    assertEquals(5.0, testCounter.value(), "Counter should be decremented to 5")

    // Final verification - test final increment to ensure counter still works
    testCounter.increment(25.0)
    assertWaiter { testCounter.value() == 30.0 }

    // Assert final state
    assertEquals(30.0, testCounter.value(), "Counter should have final value of 30")

    // Verify the counter object is still accessible and functioning
    assertNotNull(testCounter, "Counter should still be accessible at the end")

    // Verify we can still access it from the root map
    val finalCounterCheck = rootMap.get("testCounter")?.asLiveCounter
    assertNotNull(finalCounterCheck, "Counter should still be accessible from root map")
    assertEquals(30.0, finalCounterCheck.value(), "Final counter value should be 30 when accessed from root map")
  }

  @Test
  fun testLiveCounterChangesUsingSubscription() = runTest {
    val channelName = generateChannelName()
    val userEngagementMapId = restObjects.createUserEngagementMatrixMap(channelName)
    restObjects.setMapRef(channelName, "root", "userMatrix", userEngagementMapId)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root

    val userEngagementMap = rootMap.get("userMatrix")?.asLiveMap
    assertEquals(4L, userEngagementMap!!.size(), "User engagement map should contain 4 top-level entries")

    val totalReactions = userEngagementMap.get("totalReactions")?.asLiveCounter
    assertEquals(189.0, totalReactions!!.value(), "Total reactions counter should have initial value of 189")

    // Subscribe to changes on the totalReactions counter
    val counterUpdates = mutableListOf<Double>()
    val totalReactionsSubscription = totalReactions.subscribe { update ->
      counterUpdates.add(update.update.amount)
    }

    // Step 1: Increment the totalReactions counter by 10 (189 + 10 = 199)
    restObjects.incrementCounter(channelName, totalReactions.ObjectId, 10.0)

    // Wait for the update to be received
    assertWaiter { counterUpdates.isNotEmpty() }

    // Verify the increment update was received
    assertEquals(1, counterUpdates.size, "Should receive one update for increment")
    assertEquals(10.0, counterUpdates.first(), "Update should contain increment amount of 10")
    assertEquals(199.0, totalReactions.value(), "Counter should be incremented to 199")

    // Step 2: Decrement the totalReactions counter by 5 (199 - 5 = 194)
    counterUpdates.clear()
    restObjects.decrementCounter(channelName, totalReactions.ObjectId, 5.0)

    // Wait for the second update
    assertWaiter { counterUpdates.isNotEmpty() }

    // Verify the decrement update was received
    assertEquals(1, counterUpdates.size, "Should receive one update for decrement")
    assertEquals(-5.0, counterUpdates.first(), "Update should contain decrement amount of -5")
    assertEquals(194.0, totalReactions.value(), "Counter should be decremented to 194")

    // Step 3: Increment the totalReactions counter by 15 (194 + 15 = 209)
    counterUpdates.clear()
    restObjects.incrementCounter(channelName, totalReactions.ObjectId, 15.0)

    // Wait for the third update
    assertWaiter { counterUpdates.isNotEmpty() }

    // Verify the third increment update was received
    assertEquals(1, counterUpdates.size, "Should receive one update for third increment")
    assertEquals(15.0, counterUpdates.first(), "Update should contain increment amount of 15")
    assertEquals(209.0, totalReactions.value(), "Counter should be incremented to 209")

    // Clean up subscription
    counterUpdates.clear()
    totalReactionsSubscription.unsubscribe()

    // No updates should be received after unsubscribing
    restObjects.incrementCounter(channelName, totalReactions.ObjectId, 20.0)

    // Wait for a moment to ensure no updates are received
    assertWaiter { totalReactions.value() == 229.0 }

    assertTrue(counterUpdates.isEmpty(), "No updates should be received after unsubscribing")
  }
}
