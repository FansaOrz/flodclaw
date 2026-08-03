package com.foldledger.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.foldledger.domain.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("foldledger_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private object Keys {
        val AUTO_CONFIRM = booleanPreferencesKey("auto_confirm")
        val CLEAR_RAW = booleanPreferencesKey("clear_raw_after_save")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }

    override val autoConfirm: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_CONFIRM] ?: false }

    override val clearRawAfterSave: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CLEAR_RAW] ?: false }

    override val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING] ?: false }

    override suspend fun setAutoConfirm(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CONFIRM] = value }
    }

    override suspend fun setClearRawAfterSave(value: Boolean) {
        context.dataStore.edit { it[Keys.CLEAR_RAW] = value }
    }

    override suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING] = value }
    }
}
