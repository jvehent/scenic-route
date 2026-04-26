package com.senikroute.ui.nav

object Destinations {
    const val WELCOME = "welcome"
    const val EXPLORE = "explore"
    const val SIGN_IN = "signin"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val RECORDING = "recording"
    const val RECORD_FROM_EARLIER = "record_from_earlier"

    const val ARG_DRIVE_ID = "driveId"
    const val DRIVE_REVIEW = "drive/{driveId}/review"
    const val DRIVE_DETAIL = "drive/{driveId}"
    const val PUBLIC_DRIVE = "public/{driveId}"

    const val ARG_PHOTO_PATH = "path"
    const val PHOTO_VIEWER = "photo/{path}"

    const val ARG_UID = "uid"
    const val MY_PROFILE = "me/profile"
    const val OTHER_PROFILE = "user/{uid}"
    fun otherProfile(uid: String) = "user/$uid"

    const val MY_DRIVES = "me/drives"
    const val TRASH = "me/trash"

    const val DEEP_LINK_PATTERN_HTTPS = "https://senikroute.com/d/{driveId}"
    const val DEEP_LINK_PATTERN_SCHEME = "senikroute://drive/{driveId}"

    fun shareUrlFor(id: String): String = "senikroute://drive/$id"
    fun webUrlFor(id: String): String = "https://senikroute.com/d/$id"

    fun driveReview(id: String) = "drive/$id/review"
    fun driveDetail(id: String) = "drive/$id"
    fun publicDrive(id: String) = "public/$id"
    fun photoViewer(path: String): String = "photo/${android.net.Uri.encode(path)}"
}
