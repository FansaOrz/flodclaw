package com.foldpods.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldpods.domain.FoldPodsPreferences
import com.foldpods.domain.FoldPodsPrefsStore
import com.foldpods.domain.ListeningMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.foldPodsStore by preferencesDataStore("foldpods_prefs")

@Singleton
class DataStoreFoldPodsPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : FoldPodsPrefsStore {

    private val pauseOnRemove = booleanPreferencesKey("pause_on_remove")
    private val conversational = booleanPreferencesKey("conversational")
    private val headGestures = booleanPreferencesKey("head_gestures")
    private val advanced = booleanPreferencesKey("advanced_mode")
    private val lastBond = stringPreferencesKey("last_bond")
    private val linkedBle = stringPreferencesKey("linked_ble")
    private val modes = stringPreferencesKey("listening_modes")

    override fun observe(): Flow<FoldPodsPreferences> =
        context.foldPodsStore.data.map { p ->
            FoldPodsPreferences(
                pauseOnRemove = p[pauseOnRemove] ?: true,
                conversationalAwareness = p[conversational] ?: false,
                headGestures = p[headGestures] ?: false,
                advancedModeEnabled = p[advanced] ?: false,
                lastBondAddress = p[lastBond],
                linkedBleAddress = p[linkedBle],
                preferredListeningModes = (p[modes] ?: DEFAULT_MODES)
                    .split(",")
                    .mapNotNull { runCatching { ListeningMode.valueOf(it) }.getOrNull() }
                    .ifEmpty {
                        listOf(
                            ListeningMode.NOISE_CANCELLATION,
                            ListeningMode.TRANSPARENCY,
                            ListeningMode.OFF,
                        )
                    },
            )
        }

    override suspend fun update(transform: (FoldPodsPreferences) -> FoldPodsPreferences) {
        context.foldPodsStore.edit { prefs ->
            val current = FoldPodsPreferences(
                pauseOnRemove = prefs[pauseOnRemove] ?: true,
                conversationalAwareness = prefs[conversational] ?: false,
                headGestures = prefs[headGestures] ?: false,
                advancedModeEnabled = prefs[advanced] ?: false,
                lastBondAddress = prefs[lastBond],
                linkedBleAddress = prefs[linkedBle],
                preferredListeningModes = (prefs[modes] ?: DEFAULT_MODES)
                    .split(",")
                    .mapNotNull { runCatching { ListeningMode.valueOf(it) }.getOrNull() },
            )
            val next = transform(current)
            prefs[pauseOnRemove] = next.pauseOnRemove
            prefs[conversational] = next.conversationalAwareness
            prefs[headGestures] = next.headGestures
            prefs[advanced] = next.advancedModeEnabled
            val bond = next.lastBondAddress
            if (bond != null) prefs[lastBond] = bond
            else prefs.remove(lastBond)
            val ble = next.linkedBleAddress
            if (ble != null) prefs[linkedBle] = ble
            else prefs.remove(linkedBle)
            prefs[modes] = next.preferredListeningModes.joinToString(",") { it.name }
        }
    }

    companion object {
        private const val DEFAULT_MODES = "NOISE_CANCELLATION,TRANSPARENCY,OFF"
    }
}
