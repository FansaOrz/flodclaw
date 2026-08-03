package com.foldledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldledger.domain.repo.SettingsRepository
import com.foldledger.presentation.FoldLedgerApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val onboardingDone by settingsRepository.onboardingDone.collectAsStateWithLifecycle(initialValue = false)
            FoldLedgerApp(
                settingsRepository = settingsRepository,
                onboardingDoneInitial = onboardingDone,
            )
        }
    }
}
