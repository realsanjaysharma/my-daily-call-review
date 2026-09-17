package com.dailycallsreview.app.data.calllog

import android.content.Context
import android.provider.CallLog
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.CallType
import com.dailycallsreview.app.core.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    suspend fun getCallsBetween(startMillis: Long, endMillis: Long, taggedNumbers: Set<String>): List<CallRecord> =
        withContext(Dispatchers.IO) {
            if (taggedNumbers.isEmpty()) return@withContext emptyList()

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} < ?"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

            val records = mutableListOf<CallRecord>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} ASC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val rawNumber = cursor.getString(numberIndex) ?: continue
                    val normalized = PhoneNumberNormalizer.last10Digits(rawNumber)
                    if (normalized !in taggedNumbers) continue

                    val callType = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> continue
                        else -> CallType.ANSWERED
                    }

                    records.add(
                        CallRecord(
                            phoneNumberLast10 = normalized,
                            contactName = cursor.getString(nameIndex),
                            timestampMillis = cursor.getLong(dateIndex),
                            durationSeconds = cursor.getInt(durationIndex),
                            type = callType
                        )
                    )
                }
            }
            records
        }
}
