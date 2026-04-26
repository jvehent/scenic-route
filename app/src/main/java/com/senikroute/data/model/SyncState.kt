package com.senikroute.data.model

enum class SyncState {
    LOCAL,
    SYNCING,
    SYNCED,
    DIRTY;

    companion object {
        fun fromStored(s: String) = entries.firstOrNull { it.name == s } ?: LOCAL
    }
}
