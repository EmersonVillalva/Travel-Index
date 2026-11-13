package com.travelindex.app.domain

import com.travelindex.app.data.db.ExpenseEntity

enum class ExpenseCategory { FLIGHTS, LODGING, FOOD, TRANSPORT, ACTIVITIES, SHOPPING, OTHER;
    companion object { fun fromName(n: String) = entries.firstOrNull { it.name == n } ?: OTHER }
}

data class BudgetSummary(
    val budgetMinor: Long?,
    val budgetCurrency: String,
    /** Gasto por moneda. NUNCA se suman monedas distintas entre sí. */
    val spentByCurrency: Map<String, Long>,
    val byCategory: Map<ExpenseCategory, Map<String, Long>>,
    /** Solo se calcula con gastos en la moneda del presupuesto (no hay tipos de cambio). */
    val remainingMinor: Long?,
    val consumedFraction: Float?,
    val hasOtherCurrencies: Boolean,
)

object BudgetCalculator {
    fun summarize(budgetMinor: Long?, budgetCurrency: String, expenses: List<ExpenseEntity>): BudgetSummary {
        val byCur = expenses.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val byCat = expenses
            .groupBy { ExpenseCategory.fromName(it.category) }
            .mapValues { (_, l) -> l.groupBy { it.currency }.mapValues { (_, x) -> x.sumOf { it.amountMinor } } }
        val spentInBudgetCur = byCur[budgetCurrency] ?: 0L
        val remaining = budgetMinor?.let { it - spentInBudgetCur }
        val fraction = budgetMinor?.takeIf { it > 0 }?.let { spentInBudgetCur.toFloat() / it.toFloat() }
        return BudgetSummary(
            budgetMinor, budgetCurrency, byCur, byCat, remaining, fraction,
            hasOtherCurrencies = byCur.keys.any { it != budgetCurrency },
        )
    }
}
