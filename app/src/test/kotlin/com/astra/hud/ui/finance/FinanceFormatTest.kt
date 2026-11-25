package com.astra.hud.ui.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceFormatTest {
  @Test fun niceCeil_givesThreeRoundSteps() {
    assertEquals(30_000.0, niceCeil(26_020.0), 0.0)
    assertEquals(7_500.0, niceCeil(6_100.0), 0.0)
    assertEquals(3.0, niceCeil(2.2), 1e-9)
    assertEquals(1.0, niceCeil(0.0), 0.0)
    listOf(1.0, 99.0, 1234.0, 98_765.0, 4_56_789.0).forEach { v ->
      val top = niceCeil(v)
      assertTrue("$v -> $top", top >= v && top < v * 3.5)
    }
  }

  @Test fun shortRupees() {
    assertEquals("₹980", rupeesShort(980.0))
    assertEquals("₹12.4K", rupeesShort(12_400.0))
    assertEquals("₹30K", rupeesShort(30_000.0))
    assertEquals("₹3.2L", rupeesShort(3_20_000.0))
  }
}
