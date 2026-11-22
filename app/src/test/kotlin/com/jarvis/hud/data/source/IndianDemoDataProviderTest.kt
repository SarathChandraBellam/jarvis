package com.jarvis.hud.data.source

import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.RawEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianDemoDataProviderTest {
  private val now = 1_795_000_000_000L
  private val provider = IndianDemoDataProvider()

  @Test
  fun rawEvents_haveUniqueIdsAndHashes() {
    val events = provider.rawEvents(now)
    assertTrue(events.size >= 18)
    assertEquals(events.size, events.map { it.id }.toSet().size)
    assertEquals(events.size, events.map { it.contentHash }.toSet().size)
    assertTrue(events.any { it.source == DataSource.SMS })
    assertTrue(events.any { it.source == DataSource.NOTIFICATION })
    assertTrue(events.all { it.timestamp <= now })
  }

  @Test
  fun rawEvents_areDeterministic() {
    assertEquals(provider.rawEvents(now), IndianDemoDataProvider().rawEvents(now))
  }

  @Test
  fun simulatedAlert_isUniqueEachCall() {
    val alerts = (0 until 12).map { provider.simulatedAlert(now) }
    assertEquals(alerts.size, alerts.map { it.id }.toSet().size)
    assertEquals(alerts.size, alerts.map { it.contentHash }.toSet().size)
  }

  @Test
  fun calendarCallsUsage_areWellFormed() {
    val cal = provider.calendar(now)
    assertTrue(cal.size in 4..5)
    assertTrue(cal.all { it.endTime > it.startTime })
    assertEquals(8, provider.calls(now).size)
    assertTrue(provider.appUsage().all { it.foregroundMinutes > 0 })
  }

  @Test
  fun hash_isSha256HexAndStable() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      ContentHash.sha256Hex(""),
    )
    val a = ContentHash.of(DataSource.SMS, "X", "body", 1L)
    assertEquals(64, a.length)
    assertEquals(a, ContentHash.of(DataSource.SMS, "X", "body", 1L))
    assertNotEquals(a, ContentHash.of(DataSource.NOTIFICATION, "X", "body", 1L))
  }

  @Test
  fun hub_dedupesAndBounds() {
    NotificationHub.clear()
    val e = RawEvent("n1", DataSource.NOTIFICATION, "A", "t", "b", 1L, "h1")
    NotificationHub.publish(e)
    NotificationHub.publish(e.copy(id = "n2"))
    assertEquals(1, NotificationHub.snapshot().size)
    repeat(250) { NotificationHub.publish(e.copy(id = "x$it", contentHash = "hx$it")) }
    val snap = NotificationHub.snapshot()
    assertEquals(NotificationHub.CAPACITY, snap.size)
    assertEquals("x249", snap.first().id)
    NotificationHub.clear()
  }
}
