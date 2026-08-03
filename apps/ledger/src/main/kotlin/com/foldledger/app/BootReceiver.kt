package com.foldledger.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.foldledger.capture.fgs.CaptureKeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            CaptureKeepAliveService.start(context)
        }
    }
}
