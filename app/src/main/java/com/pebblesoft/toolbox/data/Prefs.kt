package com.pebblesoft.toolbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What happens to a call from a number the user never put on a list. */
enum class DefaultRule {
    /** Record it. The safe default: a victim must not lose the one call that mattered. */
    RECORD_EVERYTHING,

    /** Record only numbers on the whitelist. */
    ONLY_WHITELIST,
}

/** What happens to a call from a number that is not in the phone's contacts at all. */
enum class UnknownRule { RECORD, SKIP }

/**
 * The user's settings — one place, read as flows, written as suspends.
 *
 * THE CONFIG SECTION LAW: every key is declared here, whole, in [Keys]; no part
 * of the app invents a preference key of its own afterwards.
 */
class Prefs(private val context: Context) {

    private object Keys {
        val DEFAULT_RULE = stringPreferencesKey("default_rule")
        val UNKNOWN_RULE = stringPreferencesKey("unknown_rule")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric_unlock")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val DISGUISE = stringPreferencesKey("disguise_look")

        /**
         * What the guided test call proved about each capture route ON THIS
         * PHONE, as one flat "id=VERDICT" line per route. One key rather than a
         * key per route, so THE CONFIG SECTION LAW keeps holding: the table
         * below is the whole list of keys this app will ever write.
         */
        val ROUTE_VERDICTS = stringPreferencesKey("route_verdicts")

        /** Record ordinary calls through the loudspeaker — audible, works anywhere. */
        val SPEAKERPHONE_CALLS = booleanPreferencesKey("speakerphone_calls")

        /** Do the same for calls inside WhatsApp, Viber and their kind. */
        val SPEAKERPHONE_VOIP = booleanPreferencesKey("speakerphone_voip")
    }

    private val Context.store by preferencesDataStore("toolbox_prefs")

    private fun <T> read(block: (Preferences) -> T): Flow<T> = context.store.data.map(block)

    val defaultRule: Flow<DefaultRule> = read { prefs ->
        prefs[Keys.DEFAULT_RULE]?.let(DefaultRule::valueOf) ?: DefaultRule.RECORD_EVERYTHING
    }

    val unknownRule: Flow<UnknownRule> = read { prefs ->
        prefs[Keys.UNKNOWN_RULE]?.let(UnknownRule::valueOf) ?: UnknownRule.RECORD
    }

    val pinHash: Flow<String?> = read { it[Keys.PIN_HASH] }
    val pinSalt: Flow<String?> = read { it[Keys.PIN_SALT] }
    val biometric: Flow<Boolean> = read { it[Keys.BIOMETRIC] ?: false }
    val setupDone: Flow<Boolean> = read { it[Keys.SETUP_DONE] ?: false }
    val disguise: Flow<String?> = read { it[Keys.DISGUISE] }

    val speakerphoneCalls: Flow<Boolean> = read { it[Keys.SPEAKERPHONE_CALLS] ?: false }
    val speakerphoneVoip: Flow<Boolean> = read { it[Keys.SPEAKERPHONE_VOIP] ?: false }

    /** Route id to the verdict word the test call left behind, e.g. "BOTH_PEOPLE". */
    val routeVerdicts: Flow<Map<String, String>> = read { prefs ->
        prefs[Keys.ROUTE_VERDICTS].orEmpty()
            .split('\n')
            .mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) null else line.substring(0, at) to line.substring(at + 1)
            }
            .toMap()
    }

    suspend fun setDefaultRule(value: DefaultRule) = edit { it[Keys.DEFAULT_RULE] = value.name }
    suspend fun setUnknownRule(value: UnknownRule) = edit { it[Keys.UNKNOWN_RULE] = value.name }
    suspend fun setPinHash(value: String?) = edit {
        if (value == null) it.remove(Keys.PIN_HASH) else it[Keys.PIN_HASH] = value
    }
    suspend fun setPinSalt(value: String?) = edit {
        if (value == null) it.remove(Keys.PIN_SALT) else it[Keys.PIN_SALT] = value
    }
    suspend fun setBiometric(value: Boolean) = edit { it[Keys.BIOMETRIC] = value }
    suspend fun setSetupDone(value: Boolean) = edit { it[Keys.SETUP_DONE] = value }
    suspend fun setDisguise(value: String?) = edit {
        if (value == null) it.remove(Keys.DISGUISE) else it[Keys.DISGUISE] = value
    }

    suspend fun setSpeakerphoneCalls(value: Boolean) = edit { it[Keys.SPEAKERPHONE_CALLS] = value }
    suspend fun setSpeakerphoneVoip(value: Boolean) = edit { it[Keys.SPEAKERPHONE_VOIP] = value }

    suspend fun setRouteVerdict(routeId: String, verdict: String) = edit { prefs ->
        val merged = prefs[Keys.ROUTE_VERDICTS].orEmpty()
            .split('\n')
            .filter { it.isNotBlank() && !it.startsWith("$routeId=") }
            .plus("$routeId=$verdict")
        prefs[Keys.ROUTE_VERDICTS] = merged.joinToString("\n")
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit(block)
    }
}
