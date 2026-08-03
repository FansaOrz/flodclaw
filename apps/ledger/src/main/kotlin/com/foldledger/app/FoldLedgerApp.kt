package com.foldledger.app

import android.app.Application
import com.foldledger.capture.fgs.CaptureKeepAliveService
import com.foldledger.capture.overlay.ConfirmOverlayController
import com.foldledger.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class FoldLedgerApp : Application() {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var overlay: ConfirmOverlayController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 不依赖前台服务是否已起来，尽早订阅确认事件，避免漏弹窗
        runCatching { overlay.start() }
        scope.launch {
            seeder.seedIfNeeded()
            delay(2_500)
            runCatching { seeder.reclassifyIfNeeded() }
        }
        runCatching { CaptureKeepAliveService.start(this) }
    }
}
