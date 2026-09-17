package com.dailycallsreview.app.core

data class CallRecord(
    val phoneNumberLast10: String,
    val contactName: String?,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val type: CallType
)
