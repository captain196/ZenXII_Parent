package com.schoolsync.parent.data.model.rtdb

data class PresenceRtdb(
    val online: Boolean = false,
    val lastSeen: Long = 0L
)
