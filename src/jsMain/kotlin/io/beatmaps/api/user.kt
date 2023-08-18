package io.beatmaps.api

actual object UserDetailHelper {
    actual fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean) = "/profile/${userDetail.id}" + (tab?.let { "#$it" } ?: "")
}
