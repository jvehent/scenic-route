package com.senikroute.data.model

enum class Visibility {
    PRIVATE,
    UNLISTED,
    PUBLIC;

    companion object {
        fun fromStored(s: String) = entries.firstOrNull { it.name == s } ?: PRIVATE
    }
}
