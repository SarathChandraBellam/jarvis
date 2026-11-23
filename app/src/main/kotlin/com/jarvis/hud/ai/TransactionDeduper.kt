package com.jarvis.hud.ai

import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.OrganizedItem
import kotlin.math.abs

/**
 * Collapses one money movement reported through several channels into a single item.
 *
 * Indian banks usually announce a payment twice or more: a bank SMS, the bank app's notification,
 * and often the UPI app's notification (GPay, PhonePe, Paytm). Each arrives as a separate event
 * with its own content hash, so without this step every rupee would be counted two or three times.
 *
 * Two FINANCE items are the same transaction when:
 *  - they move the same amount in the same direction (debit vs credit), and
 *  - if both carry a UPI / bank reference, the references are equal (then time does not matter,
 *    within [REFERENCE_WINDOW_MS]); if both carry different references they are different payments;
 *  - otherwise they arrived within [WINDOW_MS] of each other through *different* channels
 *    (different source, or different sender), and do not name different accounts/cards.
 *
 * Two identical amounts through the *same* channel (two ₹50 chai payments from the same bank SMS
 * sender) are genuine repeat payments and are kept.
 */
object TransactionDeduper {

  /** Max gap between two channels' reports of the same payment. */
  const val WINDOW_MS: Long = 10 * 60 * 1000L

  /** Matching references are trusted across a wider gap (delayed SMS, late sync). */
  const val REFERENCE_WINDOW_MS: Long = 3 * 24 * 60 * 60 * 1000L

  private const val AMOUNT_EPSILON = 0.005

  fun isSameTransaction(a: OrganizedItem, b: OrganizedItem): Boolean {
    if (a.id == b.id) return true
    if (a.category != IntelCategory.FINANCE || b.category != IntelCategory.FINANCE) return false
    val amountA = a.amount ?: return false
    val amountB = b.amount ?: return false
    if (abs(amountA - amountB) > AMOUNT_EPSILON) return false
    if (a.isCredit != b.isCredit) return false
    if (a.currency != b.currency) return false

    val gap = abs(a.timestamp - b.timestamp)
    val refA = a.reference?.let(::normalizeRef)
    val refB = b.reference?.let(::normalizeRef)
    if (refA != null && refB != null) return refA == refB && gap <= REFERENCE_WINDOW_MS

    if (gap > WINDOW_MS) return false
    if (a.accountTail != null && b.accountTail != null && a.accountTail != b.accountTail) return false
    return channelOf(a) != channelOf(b)
  }

  /**
   * Merges [incoming] into [existing]. Returns the full resulting list: every existing item (some
   * enriched with details from their duplicates) plus incoming items that are not duplicates.
   * An incoming item with the same id as an existing one replaces it (normal upsert semantics);
   * the caller is responsible for preserving status on such replacements.
   */
  fun merge(existing: List<OrganizedItem>, incoming: List<OrganizedItem>): MergeResult {
    val result = existing.toMutableList()
    val indexById = HashMap<String, Int>()
    result.forEachIndexed { i, item -> indexById[item.id] = i }
    var collapsed = 0

    for (item in incoming) {
      val sameId = indexById[item.id]
      if (sameId != null) {
        result[sameId] = item
        continue
      }
      // Several candidates can qualify (two real ₹50 payments, each announced twice); the report
      // closest in time is the one this message is echoing.
      val dupIndex = if (item.category == IntelCategory.FINANCE) {
        result.indices
          .filter { result[it].category == IntelCategory.FINANCE && isSameTransaction(result[it], item) }
          .minByOrNull { abs(result[it].timestamp - item.timestamp) } ?: -1
      } else {
        -1
      }
      if (dupIndex >= 0) {
        result[dupIndex] = enrich(result[dupIndex], item)
        collapsed++
      } else {
        indexById[item.id] = result.size
        result += item
      }
    }
    return MergeResult(result, collapsed)
  }

  /** Collapses duplicates already present in one list (e.g. data persisted before this fix). */
  fun collapse(items: List<OrganizedItem>): MergeResult =
    merge(emptyList(), items.sortedBy { it.timestamp })

  data class MergeResult(val items: List<OrganizedItem>, val collapsedCount: Int)

  /**
   * Keeps [kept] (first seen; its id, status and timestamp stay) and fills any details it lacks
   * from [duplicate]. A named merchant beats a bare UPI handle.
   */
  internal fun enrich(kept: OrganizedItem, duplicate: OrganizedItem): OrganizedItem {
    val merchant = when {
      kept.merchant == null -> duplicate.merchant
      duplicate.merchant != null && isVpa(kept.merchant) && !isVpa(duplicate.merchant) -> duplicate.merchant
      else -> kept.merchant
    }
    val title = when {
      merchant == null || merchant == kept.merchant || kept.title.contains(merchant) -> kept.title
      kept.merchant != null && kept.title.contains(kept.merchant) -> kept.title.replace(kept.merchant, merchant)
      else -> kept.title + (if (kept.isCredit) " · from " else " · ") + merchant
    }
    return kept.copy(
      title = title,
      merchant = merchant,
      accountTail = kept.accountTail ?: duplicate.accountTail,
      reference = kept.reference ?: duplicate.reference,
      priority = if (duplicate.priority.ordinal < kept.priority.ordinal) duplicate.priority else kept.priority,
    )
  }

  private fun channelOf(item: OrganizedItem): String =
    item.source.name + "|" + (item.sender?.let(::normalizeSender) ?: "")

  /** "VM-HDFCBK", "AD-HDFCBK-S" and "HDFCBK" are the same SMS sender. */
  internal fun normalizeSender(sender: String): String {
    val parts = sender.trim().uppercase().split('-').filter { it.isNotBlank() }
    val core = when {
      parts.size >= 2 && parts[0].length == 2 -> parts[1]
      parts.isNotEmpty() -> parts[0]
      else -> ""
    }
    return core.filter { it.isLetterOrDigit() }
  }

  private fun normalizeRef(ref: String): String = ref.filter { it.isLetterOrDigit() }.uppercase()

  private fun isVpa(s: String) = '@' in s
}
