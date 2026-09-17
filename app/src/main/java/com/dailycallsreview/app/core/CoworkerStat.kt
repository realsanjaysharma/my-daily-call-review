package com.dailycallsreview.app.core

data class CoworkerStat(
    val phoneNumberLast10: String,
    val displayName: String,
    val callCount: Int,
    val totalTalkTimeSeconds: Long
)
