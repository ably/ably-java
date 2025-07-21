package io.ably.lib.objects.integration

import io.ably.lib.objects.LiveCounter
import io.ably.lib.objects.LiveMap
import io.ably.lib.objects.assertWaiter
import io.ably.lib.objects.integration.helpers.fixtures.createUserMapWithCountersObject
import io.ably.lib.objects.integration.setup.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    val userMap = rootMap.get("user") as LiveMap
    assertNotNull(userMap, "User map should be synchronized")
    assertEquals(7L, userMap.size(), "User map should contain 7 top-level entries")

    // Assert direct counter objects at the top level of the user map
    // Test profileViews counter - should have initial value of 127
    val profileViewsCounter = userMap.get("profileViews") as LiveCounter
    assertNotNull(profileViewsCounter, "Profile views counter should exist")
    assertEquals(127L, profileViewsCounter.value(), "Profile views counter should have initial value of 127")

    // Test postLikes counter - should have initial value of 45
    val postLikesCounter = userMap.get("postLikes") as LiveCounter
    assertNotNull(postLikesCounter, "Post likes counter should exist")
    assertEquals(45L, postLikesCounter.value(), "Post likes counter should have initial value of 45")

    // Test commentCount counter - should have initial value of 23
    val commentCountCounter = userMap.get("commentCount") as LiveCounter
    assertNotNull(commentCountCounter, "Comment count counter should exist")
    assertEquals(23L, commentCountCounter.value(), "Comment count counter should have initial value of 23")

    // Test followingCount counter - should have initial value of 89
    val followingCountCounter = userMap.get("followingCount") as LiveCounter
    assertNotNull(followingCountCounter, "Following count counter should exist")
    assertEquals(89L, followingCountCounter.value(), "Following count counter should have initial value of 89")

    // Test followersCount counter - should have initial value of 156
    val followersCountCounter = userMap.get("followersCount") as LiveCounter
    assertNotNull(followersCountCounter, "Followers count counter should exist")
    assertEquals(156L, followersCountCounter.value(), "Followers count counter should have initial value of 156")

    // Test loginStreak counter - should have initial value of 7
    val loginStreakCounter = userMap.get("loginStreak") as LiveCounter
    assertNotNull(loginStreakCounter, "Login streak counter should exist")
    assertEquals(7L, loginStreakCounter.value(), "Login streak counter should have initial value of 7")

    // Assert the nested engagement metrics map
    val engagementMetrics = userMap.get("engagementMetrics") as LiveMap
    assertNotNull(engagementMetrics, "Engagement metrics map should exist")
    assertEquals(4L, engagementMetrics.size(), "Engagement metrics map should contain 4 counter entries")

    // Assert counter objects within the engagement metrics map
    // Test totalShares counter - should have initial value of 34
    val totalSharesCounter = engagementMetrics.get("totalShares") as LiveCounter
    assertNotNull(totalSharesCounter, "Total shares counter should exist")
    assertEquals(34L, totalSharesCounter.value(), "Total shares counter should have initial value of 34")

    // Test totalBookmarks counter - should have initial value of 67
    val totalBookmarksCounter = engagementMetrics.get("totalBookmarks") as LiveCounter
    assertNotNull(totalBookmarksCounter, "Total bookmarks counter should exist")
    assertEquals(67L, totalBookmarksCounter.value(), "Total bookmarks counter should have initial value of 67")

    // Test totalReactions counter - should have initial value of 189
    val totalReactionsCounter = engagementMetrics.get("totalReactions") as LiveCounter
    assertNotNull(totalReactionsCounter, "Total reactions counter should exist")
    assertEquals(189L, totalReactionsCounter.value(), "Total reactions counter should have initial value of 189")

    // Test dailyActiveStreak counter - should have initial value of 12
    val dailyActiveStreakCounter = engagementMetrics.get("dailyActiveStreak") as LiveCounter
    assertNotNull(dailyActiveStreakCounter, "Daily active streak counter should exist")
    assertEquals(12L, dailyActiveStreakCounter.value(), "Daily active streak counter should have initial value of 12")

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
    val totalUserCounterValues = listOf(127L, 45L, 23L, 89L, 156L, 7L).sum()
    val totalEngagementCounterValues = listOf(34L, 67L, 189L, 12L).sum()
    assertEquals(447L, totalUserCounterValues, "Sum of user counter values should be 447")
    assertEquals(302L, totalEngagementCounterValues, "Sum of engagement counter values should be 302")
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
    val testCounterObjectId = restObjects.createCounter(channelName, initialValue = 10L)
    restObjects.setMapRef(channelName, "root", "testCounter", testCounterObjectId)

    // Wait for updated testCounter to be available in the root map
    assertWaiter { rootMap.get("testCounter") != null }

    // Assert initial state after creation
    val testCounter = rootMap.get("testCounter") as LiveCounter
    assertNotNull(testCounter, "Test counter should be created and accessible")
    assertEquals(10L, testCounter.value(), "Counter should have initial value of 10")

    // Step 2: Increment counter by 5 (10 + 5 = 15)
    restObjects.incrementCounter(channelName, testCounterObjectId, 5L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 15L }

    // Assert after first increment
    assertEquals(15L, testCounter.value(), "Counter should be incremented to 15")

    // Step 3: Increment counter by 3 (15 + 3 = 18)
    restObjects.incrementCounter(channelName, testCounterObjectId, 3L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 18L }

    // Assert after second increment
    assertEquals(18L, testCounter.value(), "Counter should be incremented to 18")

    // Step 4: Increment counter by a larger amount: 12 (18 + 12 = 30)
    restObjects.incrementCounter(channelName, testCounterObjectId, 12L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 30L }

    // Assert after third increment
    assertEquals(30L, testCounter.value(), "Counter should be incremented to 30")

    // Step 5: Decrement counter by 7 (30 - 7 = 23)
    restObjects.decrementCounter(channelName, testCounterObjectId, 7L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 23L }

    // Assert after first decrement
    assertEquals(23L, testCounter.value(), "Counter should be decremented to 23")

    // Step 6: Decrement counter by 4 (23 - 4 = 19)
    restObjects.decrementCounter(channelName, testCounterObjectId, 4L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 19L }

    // Assert after second decrement
    assertEquals(19L, testCounter.value(), "Counter should be decremented to 19")

    // Step 7: Increment counter by 1 (19 + 1 = 20)
    restObjects.incrementCounter(channelName, testCounterObjectId, 1L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 20L }

    // Assert after final increment
    assertEquals(20L, testCounter.value(), "Counter should be incremented to 20")

    // Step 8: Decrement counter by a larger amount: 15 (20 - 15 = 5)
    restObjects.decrementCounter(channelName, testCounterObjectId, 15L)
    // Wait for the counter to be updated
    assertWaiter { testCounter.value() == 5L }

    // Assert after large decrement
    assertEquals(5L, testCounter.value(), "Counter should be decremented to 5")

    // Final verification - test final increment to ensure counter still works
    restObjects.incrementCounter(channelName, testCounterObjectId, 25L)
    assertWaiter { testCounter.value() == 30L }

    // Assert final state
    assertEquals(30L, testCounter.value(), "Counter should have final value of 30")

    // Verify the counter object is still accessible and functioning
    assertNotNull(testCounter, "Counter should still be accessible at the end")

    // Verify we can still access it from the root map
    val finalCounterCheck = rootMap.get("testCounter") as LiveCounter
    assertNotNull(finalCounterCheck, "Counter should still be accessible from root map")
    assertEquals(30L, finalCounterCheck.value(), "Final counter value should be 30 when accessed from root map")
  }
}
