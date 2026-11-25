package com.astra.hud.data.store

import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.OrganizedItem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileIntelStoreTest {

  private fun tempFile(): File = File(Files.createTempDirectory("astra").toFile(), "store.json")

  private fun item(id: String, ts: Long, title: String = "t") =
    OrganizedItem(id, "r$id", IntelCategory.MESSAGE, title, "s", timestamp = ts, source = DataSource.SMS)

  @Test fun upsertPreservesStatusAndPersists() = runTest {
    val f = tempFile()
    val store = FileIntelStore(f)
    store.upsert(listOf(item("a", 1), item("b", 2)))
    store.setStatus("a", ItemStatus.DONE)
    store.upsert(listOf(item("a", 1, title = "new")))
    val a = store.items.value.first { it.id == "a" }
    assertEquals(ItemStatus.DONE, a.status)
    assertEquals("new", a.title)
    assertEquals("b", store.items.value.first().id) // newest first

    val reloaded = FileIntelStore(f)
    assertEquals(2, reloaded.items.value.size)
    assertEquals(ItemStatus.DONE, reloaded.items.value.first { it.id == "a" }.status)
  }

  @Test fun capsItemsHashesAndBriefings() = runTest {
    val f = tempFile()
    val store = FileIntelStore(f)
    store.upsert((1..600).map { item("i$it", it.toLong()) })
    assertEquals(500, store.items.value.size)
    assertEquals("i600", store.items.value.first().id)

    store.markProcessed((1..2100).map { "h$it" })
    assertFalse(store.isProcessed("h1"))
    assertTrue(store.isProcessed("h2100"))

    (1..9).forEach { d -> store.saveBriefing(DailyBriefing("2026-11-0$d", "h", "s", emptyList(), "local", 0)) }
    assertNull(store.loadBriefing("2026-11-01"))
    assertEquals("h", FileIntelStore(f).loadBriefing("2026-11-09")?.headline)

    store.clear()
    assertTrue(store.items.value.isEmpty())
    assertFalse(store.isProcessed("h2100"))
    assertNull(store.loadBriefing("2026-11-09"))
  }

  @Test fun corruptFileStartsEmpty() {
    val f = tempFile().apply { parentFile?.mkdirs(); writeText("{garbage") }
    assertTrue(FileIntelStore(f).items.value.isEmpty())
  }
}
