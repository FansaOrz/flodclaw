package com.foldpods

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.foldpods.domain.AirPodsRepository
import com.foldpods.presentation.AirPodsHomeScreen
import com.foldpods.presentation.media.EarPauseController
import com.foldpods.presentation.service.FoldPodsBleService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var earPauseController: EarPauseController
    @Inject lateinit var repository: AirPodsRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) startBleService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBlePermissions()
        earPauseController.start(repository.observeEarDetection())
        setContent {
            AirPodsHomeScreen()
        }
    }

    override fun onDestroy() {
        earPauseController.stop()
        super.onDestroy()
    }

    private fun requestBlePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // AirPods 邻近广播在去掉 neverForLocation 后需要定位权限才能完整扫到
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startBleService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startBleService() {
        val intent = Intent(this, FoldPodsBleService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
