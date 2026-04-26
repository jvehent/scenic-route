package com.senikroute.data.model

enum class DriveStatus {
    RECORDING,
    DRAFT,
    PUBLISHED;

    companion object {
        fun fromStored(s: String) = entries.firstOrNull { it.name == s } ?: DRAFT
    }
}
