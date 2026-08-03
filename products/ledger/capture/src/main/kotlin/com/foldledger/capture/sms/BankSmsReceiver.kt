package com.foldledger.capture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.data.parse.BankSmsParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var parser: BankSmsParser
    @Inject lateinit var pipeline: CapturePipeline

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val byAddress = linkedMapOf<String, StringBuilder>()
        var latest = System.currentTimeMillis()
        messages.forEach { msg ->
            val addr = msg.displayOriginatingAddress.orEmpty()
            byAddress.getOrPut(addr) { StringBuilder() }.append(msg.displayMessageBody.orEmpty())
            latest = maxOf(latest, msg.timestampMillis)
        }
        byAddress.forEach { (addr, body) ->
            val parsed = parser.parse(addr, body.toString(), latest) ?: return@forEach
            pipeline.submit(parsed)
        }
    }
}
