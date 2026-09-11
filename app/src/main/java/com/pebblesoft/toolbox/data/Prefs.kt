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
        val BIOMETRIC = booleanPreferencesKey("biometric_unlock")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val DISGUISE = stringPreferencesKey("disguise_look")
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
    val biometric: Flow<Boolean> = read { it[Keys.BIOMETRIC] ?: false }
    val setupDone: Flow<Boolean> = read { it[Keys.SETUP_DONE] ?: false }
    val disguise: Flow<String?> = read { it[Keys.DISGUISE] }

    suspend fun setDefaultRule(value: DefaultRule) = edit { it[Keys.DEFAULT_RULE] = value.name }
    suspend fun setUnknownRule(value: UnknownRule) = edit { it[Keys.UNKNOWN_RULE] = value.name }
    suspend fun setPinHash(value: String?) = edit {
        if (value == null) it.remove(Keys.PIN_HASH) else it[Keys.PIN_HASH] = value
    }
    suspend fun setBiometric(value: Boolean) = edit { it[Keys.BIOMETRIC] = value }
    suspend fun setSetupDone(value: Boolean) = edit { it[Keys.SETUP_DONE] = value }
    suspend fun setDisguise(value: String?) = edit {
        if (value == null) it.remove(Keys.DISGUISE) else it[Keys.DISGUISE] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit(block)
    }
}
