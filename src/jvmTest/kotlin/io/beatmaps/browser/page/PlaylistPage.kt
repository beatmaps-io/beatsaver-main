package io.beatmaps.browser.page

import com.microsoft.playwright.Page

class PlaylistPage(page: Page) : PageBase(page) {
    private fun maps() = element(".beatmap").all()

    val report = element("#report")
}
