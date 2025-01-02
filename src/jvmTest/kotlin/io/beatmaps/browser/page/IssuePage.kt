package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class IssuePage(page: Page) : PageBase(page) {
    val newComment = NewComment(element(".timeline #new-comment"))
    val info = Info(element(".timeline #issue-info"))

    class Info(element: Locator) : ElementBase(element) {
        val archive = element(".fa-archive")
        val unArchive = element(".fa-folder-open")

        // val action = element(".fa-archive|.fa-folder-open")
    }

    class NewComment(element: Locator) : ElementBase(element) {
        val text: Locator = element("textarea")
        val public: Locator = element("#new-public")
        val button: Locator = element("button")
    }

    private fun comments() = element(".timeline .comment").all()
    fun waitForComment() = page.waitForCondition {
        newComment.button.getAttribute("data-loading") == "false"
    }

    fun commentCount() = comments().size
    fun getComment(i: Int, block: Comment.() -> Unit) {
        block(Comment(comments()[i]))
    }
    fun allComments(block: Comment.() -> Unit) = comments().forEach {
        block(Comment(it))
    }

    class Comment(element: Locator) : ElementBase(element) {
        val body = element(".card-body")
        val public = element(".fa-unlock")
        val private = element(".fa-lock")
        val edit = element(".fa-pen")

        // val publicOrPrivate = element(".fa-lock|.fa-unlock")
    }
}
