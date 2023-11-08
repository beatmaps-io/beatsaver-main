package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class Modals(page: Page) : PageBase(page) {
    fun addToPlaylistModal(block: AddToPlaylistModal.() -> Unit) {
        block(AddToPlaylistModal(element(".modal-content")))
    }

    class AddToPlaylistModal(element: Locator) : ElementBase(element) {
        private val checkboxes = element("input").all()
        val save = element(".modal-footer .btn-primary")
        val cancel = element(".modal-footer .btn-secondary")

        fun getCheckbox(i: Int): Locator = checkboxes[i]
        fun getStatuses() = checkboxes.map { it.isChecked }
    }
}
