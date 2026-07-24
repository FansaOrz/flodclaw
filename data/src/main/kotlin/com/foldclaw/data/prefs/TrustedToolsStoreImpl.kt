package com.foldclaw.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldclaw.domain.agent.TrustedToolsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trustedToolsDataStore by preferencesDataStore(name = "foldclaw_trusted_tools")

@Singleton
class TrustedToolsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrustedToolsStore {

    private val key = stringSetPreferencesKey("trusted_tools")

    override suspend fun isTrusted(toolName: String): Boolean =
        trustedTools().contains(toolName)

    override suspend fun trust(toolName: String) {
        context.trustedToolsDataStore.edit { prefs ->
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur + toolName
        }
    }

    override suspend fun revoke(toolName: String) {
        context.trustedToolsDataStore.edit { prefs ->
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur - toolName
        }
    }

    override suspend fun trustedTools(): Set<String> =
        context.trustedToolsDataStore.data.map { it[key] ?: emptySet() }.first()
}
