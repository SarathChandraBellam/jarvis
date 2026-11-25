package com.astra.hud.ai

import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDeduperTest {

  private val t0 = 1_795_000_000_000L
  private val min = 60_000L

  private fun tx(
    id: String,
    amount: Double = 2450.0,
    ts: Long = t0,
    source: DataSource = DataSource.SMS,
    sender: String? = "VM-HDFCBK",
    credit: Boolean = false,
    account: String? = null,
    ref: String? = null,
    merchant: String? = null,
    title: String = "₹${amount.toLong()} ${if (credit) "credited" else "debited"}" + (merchant?.let { " · $it" } ?: ""),
    status: ItemStatus = ItemStatus.ACTIVE,
    priority: Priority = Priority.NORMAL,
    category: IntelCategory = IntelCategory.FINANCE,
  ) = OrganizedItem(
    id = id, rawEventId = "raw-$id", category = category, title = title, summary = "",
    amount = amount, isCredit = credit, merchant = merchant, priority = priority, status = status,
    timestamp = ts, source = source, sender = sender, accountTail = account, reference = ref,
  )

  // ------------------------------------------------------------------ isSameTransaction

  @Test fun bankSmsAndBankAppNotification_areSamePayment() {
    val sms = tx("sms", account = "4821", ref = "432918765510", merchant = "swiggy@icici")
    val app = tx("app", ts = t0 + min, source = DataSource.NOTIFICATION, sender = "HDFC Bank", account = "4821", merchant = "Swiggy")
    assertTrue(TransactionDeduper.isSameTransaction(sms, app))
    assertTrue(TransactionDeduper.isSameTransaction(app, sms))
  }

  @Test fun twoNotificationApps_areDifferentChannels() {
    val gpay = tx("gpay", source = DataSource.NOTIFICATION, sender = "Google Pay")
    val bank = tx("bank", ts = t0 + 2 * min, source = DataSource.NOTIFICATION, sender = "HDFC Bank")
    assertTrue(TransactionDeduper.isSameTransaction(gpay, bank))
  }

  @Test fun sameChannelSameAmount_isARealRepeatPayment() {
    val chai1 = tx("c1", amount = 50.0)
    val chai2 = tx("c2", amount = 50.0, ts = t0 + 3 * min)
    assertFalse(TransactionDeduper.isSameTransaction(chai1, chai2))
  }

  @Test fun smsSenderPrefixesAreTheSameChannel() {
    assertEquals("HDFCBK", TransactionDeduper.normalizeSender("VM-HDFCBK"))
    assertEquals("HDFCBK", TransactionDeduper.normalizeSender("AD-HDFCBK-S"))
    assertEquals("HDFCBK", TransactionDeduper.normalizeSender("hdfcbk"))
    val a = tx("a", amount = 50.0, sender = "VM-HDFCBK")
    val b = tx("b", amount = 50.0, sender = "AD-HDFCBK", ts = t0 + min)
    assertFalse(TransactionDeduper.isSameTransaction(a, b))
  }

  @Test fun differentAccounts_areDifferentPayments() {
    val a = tx("a", account = "4821")
    val b = tx("b", source = DataSource.NOTIFICATION, sender = "ICICI", account = "5544")
    assertFalse(TransactionDeduper.isSameTransaction(a, b))
  }

  @Test fun oneSideWithoutAccount_stillMatches() {
    val a = tx("a", account = "4821")
    val b = tx("b", source = DataSource.NOTIFICATION, sender = "PhonePe", account = null)
    assertTrue(TransactionDeduper.isSameTransaction(a, b))
  }

  @Test fun matchingReferences_matchEvenWhenHoursApartAndSameChannel() {
    val a = tx("a", ref = "432987651201")
    val b = tx("b", ts = t0 + 5 * 60 * min, ref = "432987651201")
    assertTrue(TransactionDeduper.isSameTransaction(a, b))
    // References are compared ignoring punctuation / case.
    val c = tx("c", source = DataSource.NOTIFICATION, sender = "Paytm", ref = "4329-8765-1201")
    assertTrue(TransactionDeduper.isSameTransaction(a, c))
  }

  @Test fun matchingReferencesTooFarApart_doNotMatch() {
    val a = tx("a", ref = "432987651201")
    val b = tx("b", ts = t0 + TransactionDeduper.REFERENCE_WINDOW_MS + 1, ref = "432987651201")
    assertFalse(TransactionDeduper.isSameTransaction(a, b))
  }

  @Test fun differentReferences_neverMatch() {
    val a = tx("a", ref = "111111111111")
    val b = tx("b", source = DataSource.NOTIFICATION, sender = "GPay", ref = "222222222222")
    assertFalse(TransactionDeduper.isSameTransaction(a, b))
  }

  @Test fun outsideTimeWindow_doNotMatch() {
    val a = tx("a")
    val b = tx("b", ts = t0 + TransactionDeduper.WINDOW_MS + 1, source = DataSource.NOTIFICATION, sender = "HDFC Bank")
    assertFalse(TransactionDeduper.isSameTransaction(a, b))
    val c = tx("c", ts = t0 + TransactionDeduper.WINDOW_MS, source = DataSource.NOTIFICATION, sender = "HDFC Bank")
    assertTrue(TransactionDeduper.isSameTransaction(a, c))
  }

  @Test fun amountDirectionAndCategoryMustAgree() {
    val debit = tx("d")
    val other = { id: String -> tx(id, source = DataSource.NOTIFICATION, sender = "HDFC Bank") }
    assertFalse(TransactionDeduper.isSameTransaction(debit, other("x").copy(amount = 2450.5)))
    assertFalse(TransactionDeduper.isSameTransaction(debit, other("y").copy(isCredit = true)))
    assertFalse(TransactionDeduper.isSameTransaction(debit, other("z").copy(amount = null)))
    assertFalse(TransactionDeduper.isSameTransaction(debit, other("w").copy(category = IntelCategory.BILL)))
    assertFalse(TransactionDeduper.isSameTransaction(debit, other("v").copy(currency = "$")))
    // Paise rounding noise is tolerated.
    assertTrue(TransactionDeduper.isSameTransaction(debit, other("u").copy(amount = 2450.001)))
  }

  @Test fun nonFinanceItemsAreNeverMerged() {
    val a = tx("a", category = IntelCategory.BILL)
    val b = tx("b", category = IntelCategory.BILL, source = DataSource.NOTIFICATION, sender = "Airtel")
    val result = TransactionDeduper.merge(listOf(a), listOf(b))
    assertEquals(2, result.items.size)
    assertEquals(0, result.collapsedCount)
  }

  // ------------------------------------------------------------------ merge

  @Test fun threeChannelsCollapseIntoOneItem() {
    val sms = tx("sms", account = "4821", ref = "432918765510", merchant = "swiggy@icici", title = "₹2,450 debited · swiggy@icici")
    val bank = tx("bank", ts = t0 + min, source = DataSource.NOTIFICATION, sender = "HDFC Bank", account = "4821", merchant = "Swiggy")
    val upi = tx("upi", ts = t0 + 30_000, source = DataSource.NOTIFICATION, sender = "Google Pay", ref = "432918765510")

    val result = TransactionDeduper.merge(emptyList(), listOf(sms, bank, upi))
    assertEquals(1, result.items.size)
    assertEquals(2, result.collapsedCount)
    val kept = result.items.single()
    assertEquals("sms", kept.id)
    assertEquals("Swiggy", kept.merchant) // brand name beats the raw UPI handle
    assertEquals("₹2,450 debited · Swiggy", kept.title)
    assertEquals("4821", kept.accountTail)
    assertEquals("432918765510", kept.reference)
  }

  @Test fun duplicateArrivingLater_mergesIntoExistingAndKeepsItsStatus() {
    val existing = tx("sms", status = ItemStatus.DISMISSED)
    val late = tx("app", ts = t0 + min, source = DataSource.NOTIFICATION, sender = "HDFC Bank", merchant = "Swiggy")
    val result = TransactionDeduper.merge(listOf(existing), listOf(late))
    assertEquals(1, result.items.size)
    assertEquals(ItemStatus.DISMISSED, result.items.single().status)
    assertEquals("Swiggy", result.items.single().merchant)
    assertEquals("₹2450 debited · Swiggy", result.items.single().title)
  }

  @Test fun enrichKeepsTheHigherPriority() {
    val a = tx("a")
    val b = tx("b", source = DataSource.NOTIFICATION, sender = "Bank", priority = Priority.HIGH)
    assertEquals(Priority.HIGH, TransactionDeduper.merge(listOf(a), listOf(b)).items.single().priority)
  }

  @Test fun enrichFillsCreditTitleWithFrom() {
    val a = tx("a", credit = true)
    val b = tx("b", credit = true, source = DataSource.NOTIFICATION, sender = "Bank", merchant = "ACME")
    assertEquals("₹2450 credited · from ACME", TransactionDeduper.merge(listOf(a), listOf(b)).items.single().title)
  }

  @Test fun sameIdReplacesInsteadOfMerging() {
    val a = tx("a", merchant = "Old")
    val updated = a.copy(title = "updated", merchant = "New")
    val result = TransactionDeduper.merge(listOf(a), listOf(updated))
    assertEquals(listOf(updated), result.items)
    assertEquals(0, result.collapsedCount)
  }

  @Test fun repeatPaymentsAndDistinctPaymentsAreAllKept() {
    val items = listOf(
      tx("c1", amount = 50.0),
      tx("c2", amount = 50.0, ts = t0 + min),
      tx("c3", amount = 50.0, ts = t0 + 2 * min),
      tx("big", amount = 9000.0, ts = t0 + 3 * min),
    )
    assertEquals(4, TransactionDeduper.merge(emptyList(), items).items.size)
  }

  @Test fun eachIncomingDuplicateMatchesOnlyOnce_perChannel() {
    // Two real ₹50 payments, each announced by SMS and by the bank app → 2 items, not 1 and not 4.
    val items = listOf(
      tx("s1", amount = 50.0, ts = t0, ref = "R00000001"),
      tx("a1", amount = 50.0, ts = t0 + 10_000, source = DataSource.NOTIFICATION, sender = "Bank", ref = "R00000001"),
      tx("s2", amount = 50.0, ts = t0 + 2 * min, ref = "R00000002"),
      tx("a2", amount = 50.0, ts = t0 + 2 * min + 10_000, source = DataSource.NOTIFICATION, sender = "Bank", ref = "R00000002"),
    )
    val result = TransactionDeduper.collapse(items)
    assertEquals(setOf("s1", "s2"), result.items.map { it.id }.toSet())
  }

  @Test fun withoutReferences_eachEchoPairsWithTheClosestPayment() {
    val items = listOf(
      tx("s1", amount = 50.0, ts = t0),
      tx("a1", amount = 50.0, ts = t0 + 10_000, source = DataSource.NOTIFICATION, sender = "Bank"),
      tx("s2", amount = 50.0, ts = t0 + 4 * min),
      tx("a2", amount = 50.0, ts = t0 + 4 * min + 10_000, source = DataSource.NOTIFICATION, sender = "Bank"),
    )
    val result = TransactionDeduper.collapse(items)
    assertEquals(setOf("s1", "s2"), result.items.map { it.id }.toSet())
    assertEquals(2, result.collapsedCount)
  }

  @Test fun collapseCleansAlreadyStoredDuplicates_keepingTheEarliest() {
    val later = tx("app", ts = t0 + min, source = DataSource.NOTIFICATION, sender = "HDFC Bank")
    val earlier = tx("sms", ts = t0)
    val result = TransactionDeduper.collapse(listOf(later, earlier))
    assertEquals(listOf("sms"), result.items.map { it.id })
    assertEquals(1, result.collapsedCount)
  }
}
