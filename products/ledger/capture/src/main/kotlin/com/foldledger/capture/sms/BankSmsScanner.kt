package com.foldledger.capture.sms

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.data.parse.BankSmsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SmsScanResult(
    val scanned: Int,
    val matched: Int,
    val submitted: Int,
)

@Singleton
class BankSmsScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: BankSmsParser,
    private val pipeline: CapturePipeline,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Scan inbox SMS from the last [days] days and submit bank payment candidates.
     */
    fun scanInbox(days: Int = 90): SmsScanResult {
        if (!hasPermission()) return SmsScanResult(0, 0, 0)
        val since = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        var scanned = 0
        var matched = 0
        var submitted = 0

        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(since.toString())

        context.contentResolver.query(
            uri,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                scanned++
                val address = cursor.str(Telephony.Sms.ADDRESS)
                val body = cursor.str(Telephony.Sms.BODY)
                val date = cursor.long(Telephony.Sms.DATE)
                val parsed = parser.parse(address, body, date) ?: continue
                matched++
                pipeline.submit(parsed)
                submitted++
            }
        }
        return SmsScanResult(scanned, matched, submitted)
    }

    private fun Cursor.str(column: String): String =
        getString(getColumnIndexOrThrow(column)).orEmpty()

    private fun Cursor.long(column: String): Long =
        getLong(getColumnIndexOrThrow(column))
}
