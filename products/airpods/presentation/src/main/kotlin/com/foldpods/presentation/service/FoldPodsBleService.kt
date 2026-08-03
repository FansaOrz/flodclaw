package com.foldpods.presentation.service

import android.app.Service
import android.content.Intent
import android.app.PendingIntent
import android.os.IBinder
import com.foldpods.domain.AirPodsRepository
import com.foldpods.presentation.notify.BatteryNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FoldPodsBleService : Service() {

    @Inject lateinit var repository: AirPodsRepository
    @Inject lateinit var notifications: BatteryNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(BatteryNotificationManager.ID_SCAN, notifications.scanServiceNotification(pi))

        scope.launch { repository.startBleScan() }
        job?.cancel()
        job = scope.launch {
            repository.observePrimary().collectLatest { snap ->
                notifications.publishBattery(snap, launch)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.launch { repository.stopBleScan() }
        scope.cancel()
        super.onDestroy()
    }
}
