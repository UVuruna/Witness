package com.pebblesoft.toolbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the user decided about one number.
 *
 * ONE KIND, ONE CLASS: the whitelist and the blacklist are not two tables and
 * not two screens' worth of duplicated code — they are this one table with
 * [mode] deciding which list a row belongs to. A future third list is a new
 * enum entry, never a copied block.
 */
enum class RuleMode {
    /** Whitelist: this number is always recorded. */
    ALWAYS_RECORD,

    /** Blacklist: this number is never recorded, and nothing about it is kept. */
    NEVER_RECORD,
}

@Entity(tableName = "number_rules")
data class NumberRule(
    /** Normalized by [normalizeNumber] so "+381 64 123" and "064123" are one row. */
    @PrimaryKey val number: String,

    /** What the user knows this number as — shown instead of digits. */
    val label: String? = null,

    val mode: RuleMode,
    val addedAt: Long,
)

/**
 * Strip a number down to what can be compared.
 *
 * Carriers, contacts and call logs deliver the same person as "+381641234567",
 * "0641234567" and "064 123 4567". Comparison uses the last [SIGNIFICANT_DIGITS]
 * digits, which is what survives every one of those forms; a shorter number
 * (a short code) is compared whole.
 */
fun normalizeNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return if (digits.length <= SIGNIFICANT_DIGITS) digits
    else digits.takeLast(SIGNIFICANT_DIGITS)
}

private const val SIGNIFICANT_DIGITS = 9
