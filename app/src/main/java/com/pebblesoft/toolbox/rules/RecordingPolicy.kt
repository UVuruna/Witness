package com.pebblesoft.toolbox.rules

import com.pebblesoft.toolbox.data.DefaultRule
import com.pebblesoft.toolbox.data.NumberRuleDao
import com.pebblesoft.toolbox.data.Prefs
import com.pebblesoft.toolbox.data.RuleMode
import com.pebblesoft.toolbox.data.UnknownRule
import com.pebblesoft.toolbox.data.normalizeNumber
import kotlinx.coroutines.flow.first

/** The answer, with the reason attached so the UI can explain itself to the user. */
data class Decision(val record: Boolean, val because: Reason) {
    enum class Reason {
        /** The number sits on the whitelist. */
        ON_WHITELIST,

        /** The number sits on the blacklist. */
        ON_BLACKLIST,

        /** Not in the phone's contacts, and the user set a rule for unknown callers. */
        UNKNOWN_CALLER,

        /** No rule touched it — the default applies. */
        DEFAULT,

        /** No number arrived at all (withheld / not permitted). The default applies. */
        NO_NUMBER,
    }
}

/**
 * THE one place that decides whether a call is recorded.
 *
 * Every caller — the call monitor, the settings preview, the tests — asks this
 * class; nothing duplicates the reasoning. The order is deliberate and the
 * user's explicit choice always beats a default:
 *
 *  1. an explicit rule for this number (whitelist / blacklist)
 *  2. the rule for callers who are not in the phone's contacts
 *  3. the default rule
 *
 * The shipped default is RECORD_EVERYTHING, because a victim must not lose the
 * one call that mattered to a list she forgot to update.
 */
class RecordingPolicy(
    private val rules: NumberRuleDao,
    private val prefs: Prefs,
) {

    suspend fun decide(rawNumber: String?, isInContacts: Boolean): Decision {
        val number = rawNumber?.let(::normalizeNumber).orEmpty()
        if (number.isEmpty()) {
            return Decision(byDefault(), Decision.Reason.NO_NUMBER)
        }

        rules.find(number)?.let { rule ->
            return when (rule.mode) {
                RuleMode.ALWAYS_RECORD -> Decision(true, Decision.Reason.ON_WHITELIST)
                RuleMode.NEVER_RECORD -> Decision(false, Decision.Reason.ON_BLACKLIST)
            }
        }

        if (!isInContacts) {
            val unknown = prefs.unknownRule.first()
            return Decision(unknown == UnknownRule.RECORD, Decision.Reason.UNKNOWN_CALLER)
        }

        return Decision(byDefault(), Decision.Reason.DEFAULT)
    }

    private suspend fun byDefault(): Boolean =
        prefs.defaultRule.first() == DefaultRule.RECORD_EVERYTHING
}
