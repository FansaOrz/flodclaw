package com.foldclaw

import android.app.Application
import com.foldclaw.device.lifecycle.TaskLifecycleMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FoldClawApp : Application() {

    @Inject lateinit var lifecycleMonitor: TaskLifecycleMonitor

    override fun onCreate() {
        super.onCreate()
        lifecycleMonitor.start()
    }
}
