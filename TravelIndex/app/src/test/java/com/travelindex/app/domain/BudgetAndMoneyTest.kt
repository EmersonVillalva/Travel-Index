package com.travelindex.app.domain

import com.travelindex.app.data.db.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetAndMoneyTest {
    private fun exp(amount: Long, cur: String, cat: String = "FOOD") =
        ExpenseEntity(tripId = 1, concept = "x", category = cat, amountMinor = amount, currency = cur, date = LocalDate.of(2027, 6, 12))

    @Test fun `no mezcla monedas distintas`() {
        val s = BudgetCalculator.summarize(100_000, "EUR", listOf(exp(20_000, "EUR"), exp(5_000, "USD")))
        assertEquals(20_000L, s.spentByCurrency["EUR"]); assertEquals(5_000L, s.spentByCurrency["USD"])
        assertEquals(80_000L, s.remainingMinor)          // solo descuenta EUR
        assertEquals(0.2f, s.consumedFraction!!, 0.0001f)
        assertTrue(s.hasOtherCurrencies)
    }

    @Test fun `sin presupuesto no hay porcentaje ni restante`() {
        val s = BudgetCalculator.summarize(null, "EUR", listOf(exp(1_000, "EUR")))
        assertNull(s.remainingMinor); assertNull(s.consumedFraction)
    }

    @Test fun `agrupa por categoria y moneda`() {
        val s = BudgetCalculator.summarize(null, "EUR", listOf(exp(100, "EUR", "FOOD"), exp(300, "EUR", "FOOD"), exp(50, "EUR", "FLIGHTS")))
        assertEquals(400L, s.byCategory[ExpenseCategory.FOOD]!!["EUR"])
    }

    @Test fun `parseToMinor acepta formatos europeos y anglosajones`() {
        assertEquals(123_450L, Money.parseToMinor("1.234,50", "EUR"))
        assertEquals(123_450L, Money.parseToMinor("1,234.50", "EUR"))
        assertEquals(1_000L, Money.parseToMinor("10", "EUR"))
        assertEquals(1_000L, Money.parseToMinor("1000", "JPY"))   // JPY no tiene decimales
        assertNull(Money.parseToMinor("abc", "EUR")); assertNull(Money.parseToMinor("-5", "EUR")); assertNull(Money.parseToMinor("", "EUR"))
    }

    @Test fun `estado y duracion del viaje`() {
        val s = LocalDate.of(2027, 6, 12); val e = LocalDate.of(2027, 6, 18)
        assertEquals(TripStatus.UPCOMING, tripStatus(s, e, false, s.minusDays(1)))
        assertEquals(TripStatus.ONGOING, tripStatus(s, e, false, e))
        assertEquals(TripStatus.COMPLETED, tripStatus(s, e, false, e.plusDays(1)))
        assertEquals(TripStatus.CANCELLED, tripStatus(s, e, true, s))
        assertEquals(7, tripDurationDays(s, e)); assertEquals(1, tripDurationDays(s, s))
    }
}
