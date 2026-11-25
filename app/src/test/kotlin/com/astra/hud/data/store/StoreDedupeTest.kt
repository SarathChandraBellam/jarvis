package com.astra.hud.data.store

import com.astra.hud.ai.LocalIntelParser
import com.astra.hud.testdata.SampleInbox
import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.OrganizedItem
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StoreDedupeTest {

  private fun tempFile(): File = File(Files.createTempDirectory("astra").toFile(), "store.json")
  private val t0 = 1_795_000_000_000L

  private fun tx(id: String, ts: Long, source: DataSource, sender: String, ref: String? = null, account: String? = null) =
    OrganizedItem(
      id, "r$id", IntelCategory.FINANCE, "₹2,450 debited", "", amount = 2450.0, timestamp = ts,
      source = source, sender = sender, reference = ref, accountTail = account,
    )

  @Test fun duplicateFromSecondChannel_isMergedAcrossUpserts() = runTest {
    val store = FileIntelStore(tempFile())
    store.upsert(listOf(tx("sms", t0, DataSource.SMS, "VM-HDFCBK", account = "4821")))
    store.upsert(listOf(tx("app", t0 + 60_000, DataSource.NOTIFICATION, "HDFC Bank", account = "4821")))
    assertEquals(listOf("sms"), store.items.value.map { it.id })
  }

  @Test fun duplicatesInsideOneUpsert_areMerged() = runTest {
    val store = FileIntelStore(tempFile())
    store.upsert(
      listOf(
        tx("sms", t0, DataSource.SMS, "VM-PAYTMB", ref = "432987651201"),
        tx("app", t0 + 5_000, DataSource.NOTIFICATION, "Paytm", ref = "432987651201"),
      ),
    )
    assertEquals(1, store.items.value.size)
  }

  @Test fun dismissedPayment_staysDismissedWhenItsEchoArrives() = runTest {
    val store = FileIntelStore(tempFile())
    store.upsert(listOf(tx("sms", t0, DataSource.SMS, "VM-HDFCBK")))
    store.setStatus("sms", ItemStatus.DISMISSED)
    store.upsert(listOf(tx("app", t0 + 60_000, DataSource.NOTIFICATION, "HDFC Bank")))
    assertEquals(ItemStatus.DISMISSED, store.items.value.single().status)
  }

  @Test fun newFieldsSurviveRestart() = runTest {
    val f = tempFile()
    FileIntelStore(f).upsert(listOf(tx("sms", t0, DataSource.SMS, "VM-HDFCBK", ref = "R12345678", account = "4821")))
    val item = FileIntelStore(f).items.value.single()
    assertEquals("VM-HDFCBK", item.sender)
    assertEquals("R12345678", item.reference)
    assertEquals("4821", item.accountTail)
  }

  @Test fun duplicatesSavedByOlderVersions_areCollapsedOnLoad() = runTest {
    val f = tempFile()
    val dto = StoreFileDto(
      items = listOf(
        ItemStoreDto.from(tx("app", t0 + 60_000, DataSource.NOTIFICATION, "HDFC Bank")),
        ItemStoreDto.from(tx("sms", t0, DataSource.SMS, "VM-HDFCBK")),
      ),
    )
    f.writeText(Json.encodeToString(StoreFileDto.serializer(), dto))
    assertEquals(listOf("sms"), FileIntelStore(f).items.value.map { it.id })
  }

  @Test fun oldStoreFilesWithoutNewFields_stillLoad() = runTest {
    val f = tempFile()
    f.writeText(
      """{"items":[{"id":"x","category":"FINANCE","title":"₹10 debited","summary":"","amount":10.0,"timestamp":1,"source":"SMS"}]}""",
    )
    val item = FileIntelStore(f).items.value.single()
    assertEquals("x", item.id)
    assertEquals(null, item.sender)
  }

  /**
   * End to end on a sample inbox, which reports the Swiggy payment by SMS + HDFC app and the
   * kirana payment by SMS + Paytm app. Today's spend must count each payment once.
   */
  @Test fun sampleDay_countsEachPaymentOnce() = runTest {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.of(2026, 11, 23, 23, 30).atZone(zone).toInstant().toEpochMilli()
    val events = SampleInbox().rawEvents(now)
    val parser = LocalIntelParser(clock = { now }, zone = zone)
    val store = FileIntelStore(tempFile())

    // Feed in the order a phone would see them: SMS scan first, notifications arriving live.
    store.upsert(events.filter { it.source == DataSource.SMS }.map(parser::parse))
    events.filter { it.source == DataSource.NOTIFICATION }.forEach { store.upsert(listOf(parser.parse(it))) }

    val dayStart = LocalDateTime.of(2026, 11, 23, 0, 0).atZone(zone).toInstant().toEpochMilli()
    val todayDebits = store.items.value.filter {
      it.category == IntelCategory.FINANCE && !it.isCredit && it.amount != null && it.timestamp >= dayStart
    }
    // Swiggy ₹2,450 + Ramesh Kirana ₹350 + Myntra ₹4,299 — not ₹9,899 (with the two echoes).
    assertEquals(7_099.0, todayDebits.sumOf { it.amount!! }, 0.001)
    assertEquals(3, todayDebits.size)

    val swiggy = todayDebits.firstOrNull { it.amount == 2450.0 }
    assertNotNull(swiggy)
    assertEquals("Swiggy", swiggy!!.merchant)
    assertEquals("432918765510", swiggy.reference)
  }
}
