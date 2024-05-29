package io.beatmaps.api

enum class OauthScope(val tag: String, val description: String) {
    IDENTITY("identity", "Access your id, username and avatar"),
    BOOKMARKS("bookmarks", "Read and update your bookmarks"),
    TESTPLAY("testplay", "Submit testplay feedback on your behalf"),
    PLAYLISTS("playlists", "Access your private playlists"),
    ADMIN_PLAYLISTS("playlists.admin", "Create/edit/delete your public and private playlists"),
    MANAGE_PLAYLISTS("playlists.manage", "Update maps in your public and private playlists"),
    ALERTS("alerts", "Read your alerts"),
    MARK_ALERTS("alerts.mark", "Mark your alerts as read/unread");

    companion object {
        private val map = entries.associateBy(OauthScope::tag)
        fun fromTag(tag: String) = map[tag]
    }
}
