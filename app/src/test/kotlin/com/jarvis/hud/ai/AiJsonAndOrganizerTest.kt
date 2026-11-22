package com.jarvis.hud.ai

import com.jarvis.hud.contracts.OpenRouterClient
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.DeviceTelemetry
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.RawEvent
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonAndOrganizerTest {

  private val zone = ZoneId.of("Asia/Kolkata")
  private val now = ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

  private val events = listOf(
    RawEvent("e1", DataSource.SMS, "AD-HDFCBK", "", "Rs 4,250 debited at SWIGGY. OTP 123456", now, "h1"),
    RawEvent("e2", DataSource.SMS, "AX-AMAZON", "", "Your Amazon package is out for delivery", now, "h2"),
  )

  private val arrayJson = """
    [{"rawEventId":"e1","category":"FINANCE","title":"₹4,250 debited · Swiggy","summary":"Food order","amount":4250,"isCredit":false,"merchant":"Swiggy","dueDate":null,"priority":"NORMAL","extra":"ignored"},
     {"rawEventId":"e2","category":"delivery","title":"Amazon · Out for delivery","summary":"Arrives today","amount":"₹0","priority":"high"}]
  """.trimIndent()

  @Test fun parsesUnfencedArray() {
    val out = AiJson.parseOrganizedItems(arrayJson, events)
    assertEquals(2, out.size)
    assertEquals(IntelCategory.FINANCE, out["e1"]!!.category)
    assertEquals(4250.0, out["e1"]!!.amount!!, 0.0)
    assertEquals("item-h1", out["e1"]!!.id)
    assertEquals(IntelCategory.DELIVERY, out["e2"]!!.category)
    assertEquals(Priority.HIGH, out["e2"]!!.priority)
  }

  @Test fun parsesFencedArrayWithProse() {
    val raw = "Sure! Here you go:\n```json\n$arrayJson\n```\nLet me know."
    assertEquals(2, AiJson.parseOrganizedItems(raw, events).size)
  }

  @Test fun parsesWrappedObject() {
    val raw = """{"items": $arrayJson}"""
    assertEquals(2, AiJson.parseOrganizedItems(raw, events).size)
  }

  @Test fun garbageYieldsEmpty() {
    assertTrue(AiJson.parseOrganizedItems("I cannot help with that.", events).isEmpty())
    assertTrue(AiJson.parseOrganizedItems("[{not json", events).isEmpty())
    assertTrue(AiJson.parseOrganizedItems("", events).isEmpty())
  }

  @Test fun dropsUnknownIdsAndBadCategories() {
    val raw = """[{"rawEventId":"zzz","category":"FINANCE","title":"x"},{"rawEventId":"e1","category":"WEIRD","title":"y"}]"""
    assertTrue(AiJson.parseOrganizedItems(raw, events).isEmpty())
  }

  @Test fun parsesBriefing() {
    val b = AiJson.parseBriefing("```json\n{\"headline\":\"Good morning, Sir.\",\"summary\":\"All quiet.\",\"highlights\":[\"a\",\"\",\"b\"]}\n```")
    assertNotNull(b)
    assertEquals(listOf("a", "b"), b!!.highlights)
    assertNull(AiJson.parseBriefing("nope"))
    assertNull(AiJson.parseBriefing("{\"headline\":\"\",\"summary\":\"\"}"))
  }

  private class FakeClient(private val response: (String) -> Result<String>) : OpenRouterClient {
    val prompts = mutableListOf<String>()
    override suspend fun chat(apiKey: String, modelId: String, systemPrompt: String, messages: List<Pair<String, String>>): Result<String> {
      prompts += messages.last().second
      return response(messages.last().second)
    }
    override suspend fun listModels(apiKey: String): Result<List<OpenRouterModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(apiKey: String, modelId: String) = ConnectionTestResult(true, "ok")
  }

  private val localParser = LocalIntelParser({ now }, zone)

  @Test fun organizerUsesAiAndFallsBackForMissingEntries() = runTest {
    val onlyFirst = """```json
      [{"rawEventId":"e1","category":"FINANCE","title":"AI title","priority":"NORMAL"}]
      ```"""
    val client = FakeClient { Result.success(onlyFirst) }
    val org = HybridIntelOrganizer(client, RegexRedactor(), localParser)
    val out = org.organize(events, JarvisSettings(apiKey = "sk-test"))
    assertEquals(2, out.size)
    assertEquals("AI title", out[0].title)
    assertEquals(IntelCategory.DELIVERY, out[1].category) // local fallback
    // OTP redacted before sending
    assertFalse(client.prompts.single().contains("123456"))
    // payload is valid JSON after the header line
    JSONArray(client.prompts.single().substringAfter('\n'))
  }

  @Test fun organizerFallsBackOnFailureAndWithoutKeyAndForNonSharedSources() = runTest {
    val failing = FakeClient { Result.failure(RuntimeException("boom")) }
    val out = HybridIntelOrganizer(failing, RegexRedactor(), localParser).organize(events, JarvisSettings(apiKey = "sk"))
    assertEquals(listOf(IntelCategory.SYSTEM, IntelCategory.DELIVERY), out.map { it.category }) // e1 carries an OTP

    val never = FakeClient { error("should not be called") }
    HybridIntelOrganizer(never, RegexRedactor(), localParser).organize(events, JarvisSettings(apiKey = ""))
    HybridIntelOrganizer(never, RegexRedactor(), localParser).organize(events, JarvisSettings(apiKey = "sk", aiShareSources = setOf(DataSource.CALENDAR)))
    assertTrue(never.prompts.isEmpty())
  }

  @Test fun organizerBatchesAt25() = runTest {
    val many = (1..60).map { RawEvent("r$it", DataSource.SMS, "X", "", "hello $it", now, "hh$it") }
    val client = FakeClient { Result.success("[]") }
    val out = HybridIntelOrganizer(client, RegexRedactor(), localParser).organize(many, JarvisSettings(apiKey = "sk"))
    assertEquals(60, out.size)
    assertEquals(3, client.prompts.size)
  }

  @Test fun localBriefingAndAssistant() = runTest {
    val items = listOf(
      OrganizedItem("i1", "e1", IntelCategory.FINANCE, "₹4,000 debited · Swiggy", "", amount = 4000.0, merchant = "Swiggy", timestamp = now, source = DataSource.SMS),
      OrganizedItem("i2", "e2", IntelCategory.FINANCE, "₹250 debited", "", amount = 250.0, timestamp = now, source = DataSource.SMS),
      OrganizedItem("i3", "e3", IntelCategory.BILL, "Credit card bill", "", amount = 12450.0, dueDate = "2026-11-23", priority = Priority.CRITICAL, timestamp = now, source = DataSource.SMS),
    )
    val cal = listOf(CalendarEntry("c1", "Standup", now + 3_600_000, now + 5_400_000, null))
    val noKeyClient = FakeClient { error("no") }
    val b = JarvisBriefingGenerator(noKeyClient, { now }, zone)
      .generate(items, cal, DeviceTelemetry(88, false, 10f, 128f), JarvisSettings(apiKey = ""))
    assertEquals("2026-11-22", b.dateKey)
    assertEquals("local", b.modelUsed)
    assertEquals("Good morning, Sir.", b.headline)
    assertTrue(b.summary, b.summary.contains("1 critical item") && b.summary.contains("₹4,250 spent today"))
    assertTrue(b.highlights.any { it.contains("Standup") })

    val assistant = JarvisAskAssistant(noKeyClient, { now }, zone)
    val spend = assistant.answer("How much did I spend today?", emptyList(), items, cal, JarvisSettings(apiKey = ""))
    assertFalse(spend.isFromUser)
    assertTrue(spend.content, spend.content.contains("₹4,250"))
    val other = assistant.answer("Write me a poem", emptyList(), items, cal, JarvisSettings(apiKey = ""))
    assertTrue(other.content.contains("OpenRouter"))
  }

  @Test fun aiBriefingUsesModel() = runTest {
    val client = FakeClient { Result.success("""{"headline":"Good morning, Boss.","summary":"Quiet day.","highlights":["x"]}""") }
    val b = JarvisBriefingGenerator(client, { now }, zone)
      .generate(emptyList(), emptyList(), DeviceTelemetry(50, true, 1f, 2f), JarvisSettings(apiKey = "sk", modelId = "m/x", userCallsign = "Boss"))
    assertEquals("m/x", b.modelUsed)
    assertEquals("Good morning, Boss.", b.headline)
  }

  @Test fun freeModelDetection() {
    assertTrue(OkHttpOpenRouterClient.isFreeModel("a/b", "0", "0"))
    assertTrue(OkHttpOpenRouterClient.isFreeModel("a/b:free", "0.001", "0.002"))
    assertFalse(OkHttpOpenRouterClient.isFreeModel("a/b", "0.000001", "0"))
    assertEquals(401, OkHttpOpenRouterClient.httpError(401, "{}", "m").httpCode)
  }
}
