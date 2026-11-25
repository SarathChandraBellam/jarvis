package com.astra.hud.data.store

import com.astra.hud.model.BillingCadence
import com.astra.hud.model.PlannerReminder
import com.astra.hud.model.SubscriptionCategory
import com.astra.hud.model.TrackedSubscription
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePlannerStoreTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test fun remindersSubscriptionsAndAlertStatePersistAcrossRestarts() = runTest {
    val file = File(tmp.root, "planner.json")
    val store1 = FilePlannerStore(file)
    store1.addReminder(PlannerReminder("r1", "Cancel trial", 630, "2026-11-24"))
    store1.toggleReminder("r1")
    store1.addSubscription(
      TrackedSubscription("s1", "Claude Pro", 1999.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.SOFTWARE, isAutoDetected = false),
    )
    store1.toggleSubscriptionPaused("s1")
    store1.dismissAlert("sub:s1:2026-11-24")
    store1.markNotified(listOf("bill:b1:2026-11-24"))

    val store2 = FilePlannerStore(file)
    assertEquals(1, store2.reminders.value.size)
    assertTrue(store2.reminders.value.single().isDone)
    assertEquals("Claude Pro", store2.customSubscriptions.value.single().name)
    assertTrue("s1" in store2.pausedSubscriptionIds.value)
    assertTrue("sub:s1:2026-11-24" in store2.dismissedAlertKeys.value)
    assertTrue("bill:b1:2026-11-24" in store2.notifiedAlertKeys.value)
  }
}
