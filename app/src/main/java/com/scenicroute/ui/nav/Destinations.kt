package com.scenicroute.ui.nav

object Destinations {
    const val EXPLORE = "explore"
    const val SIGN_IN = "signin"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val RECORDING = "recording"
    const val RECORD_FROM_EARLIER = "record_from_earlier"
    const val DRIVE_DETAIL = "drive/{driveId}"
    fun driveDetail(id: String) = "drive/$id"
}
