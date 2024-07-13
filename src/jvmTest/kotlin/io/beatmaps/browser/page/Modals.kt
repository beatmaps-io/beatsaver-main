package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class Modals(page: Page) : PageBase(page) {
    fun addToPlaylistModal(block: AddToPlaylistModal.() -> Unit) {
        block(AddToPlaylistModal(element("div.modal-content").also { it.waitFor() }))
    }

    class AddToPlaylistModal(element: Locator) : ElementBase(element) {
        private val checkboxes = element("input").all()
        val save = element(".modal-footer .btn-primary")
        val cancel = element(".modal-footer .btn-secondary")

        fun getCheckbox(i: Int): Locator = checkboxes[i]
        fun getStatuses() = checkboxes.map { it.isChecked }
    }

    fun confirmModal(block: ConfirmModal.() -> Unit) {
        block(ConfirmModal(element("div.modal-content").also { it.waitFor() }))
    }

    class ConfirmModal(element: Locator) : ElementBase(element) {
        val text = element("textarea")

        val confirm = element(".modal-footer .btn-primary, .modal-footer .btn-danger")
        val cancel = element(".modal-footer .btn-secondary")
    }
}
