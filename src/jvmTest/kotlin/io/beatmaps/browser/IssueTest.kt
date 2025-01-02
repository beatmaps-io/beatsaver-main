package io.beatmaps.browser

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.dbo.Issue
import io.beatmaps.common.dbo.IssueComment
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.Test
import java.lang.Integer.toHexString
import kotlin.test.assertEquals

class IssueTest : BrowserTestBase() {
    @Test
    fun `Can't report maps`() = bmTest {
        val (mapId, userIds) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid, true)
            val (suspendedUserId, _) = createUser(suspended = true)
            val (adminUserId, _) = createUser(admin = true)

            mid to listOf(uid, adminUserId, suspendedUserId)
        }

        // Check option isn't visible as admin/owner/suspended
        userIds.forEach { userId ->
            login(userId)
            navigate("/maps/${toHexString(mapId)}")

            mapPage {
                assertThat(options.report).not().isVisible()
            }
        }
    }

    @Test
    fun `Can report maps`() = bmTest {
        // This test would probably benefit from being broken down into smaller chunks

        val (mapId, userId, adminUserId, reporterId) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid, true)
            val (reporterId, _) = createUser()
            val (adminUserId, _) = createUser(admin = true)

            listOf(mid, uid, adminUserId, reporterId)
        }

        login(reporterId)
        navigate("/maps/${toHexString(mapId)}")

        data class ExpectedComment(val text: String, val public: Boolean, val admin: Boolean)
        val expectedCommentInfo = listOf(
            ExpectedComment(fixture<String>(), public = true, admin = false),
            ExpectedComment(fixture<String>(), public = true, admin = false),
            ExpectedComment(fixture<String>(), public = false, admin = true),
            ExpectedComment(fixture<String>(), public = true, admin = true)
        )
        val (reportText, newText, adminPrivateText, adminText) = expectedCommentInfo.map(ExpectedComment::text)

        mapPage {
            options.report.click()

            modals.confirmModal {
                text.fill(reportText)
                confirm.click()
            }
        }

        issuePage {
            waitForComment()
            assertEquals(1, commentCount())

            newComment.text.fill(newText)
            assertThat(newComment.public).not().isVisible()
            newComment.button.click()

            // Comment element resets
            assertThat(newComment.text).hasText("")

            waitForComment()
            assertEquals(2, commentCount())
        }

        login(adminUserId)
        reload()

        issuePage {
            newComment.text.fill(adminPrivateText)
            newComment.public.uncheck()
            newComment.button.click()
            waitForComment()

            newComment.text.fill(adminText)
            newComment.public.check()
            newComment.button.click()
            waitForComment()

            // Check comments are correct
            assertEquals(4, commentCount())
            expectedCommentInfo.forEachIndexed { index, expectedComment ->
                getComment(index) {
                    assertThat(body).hasText(expectedComment.text)
                    assertVisible(edit, expectedComment.admin)
                    assertVisible(public, expectedComment.public)
                    assertVisible(private, !expectedComment.public)
                }
            }

            screenshot("issue-admin")
        }

        login(reporterId)
        reload()

        issuePage {
            assertEquals(3, commentCount())

            expectedCommentInfo.filter { it.public }.forEachIndexed { index, expectedComment ->
                getComment(index) {
                    assertThat(body).hasText(expectedComment.text)
                    assertVisible(edit, !expectedComment.admin)
                    assertVisible(public, expectedComment.public)
                    assertVisible(private, !expectedComment.public)
                }
            }

            screenshot("issue")
        }

        login(adminUserId)
        reload()

        issuePage {
            info.archive.click()
            waitUntilGone(newComment.elem)

            allComments {
                assertThat(edit).not().isVisible()
            }
        }

        login(reporterId)
        reload()

        issuePage {
            assertThat(newComment.elem).not().isVisible()
            allComments {
                assertThat(edit).not().isVisible()
            }
        }

        login(userId)
        reload()

        assertEquals("/", url(), "Redirected to homepage")

        db {
            val issuesCount = Issue.selectAll().where { Issue.creator eq reporterId }.count()
            assertEquals(1, issuesCount)

            val commentsCount = IssueComment.selectAll().where { IssueComment.userId eq reporterId }.count()
            assertEquals(2, commentsCount)

            val adminCommentsCount = IssueComment.selectAll().where { IssueComment.userId eq adminUserId }.count()
            assertEquals(2, adminCommentsCount)
        }
    }

    @Test
    fun `Can't report playlists`() = bmTest {
        val (playlistId, userIds) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val pid = createPlaylist(uid, EPlaylistType.Public)
            val (suspendedUserId, _) = createUser(suspended = true)
            val (adminUserId, _) = createUser(admin = true)

            pid to listOf(uid, adminUserId, suspendedUserId)
        }

        // Check option isn't visible as admin/owner/suspended
        userIds.forEach { userId ->
            login(userId)
            navigate("/playlists/$playlistId")

            playlistPage {
                assertThat(report).not().isVisible()
            }
        }
    }

    @Test
    fun `Can report playlists`() = bmTest {
        val (playlistId, reporterId) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val pid = createPlaylist(uid, EPlaylistType.Public)
            val (reporterId, _) = createUser()

            pid to reporterId
        }

        login(reporterId)
        navigate("/playlists/$playlistId")

        val reportText = fixture<String>()

        playlistPage {
            report.click()

            modals.confirmModal {
                text.fill(reportText)
                confirm.click()
            }
        }

        issuePage {
            waitForComment()
            assertEquals(1, commentCount())

            getComment(0) {
                assertThat(body).hasText(reportText)
                assertVisible(edit, true)
                assertVisible(public, true)
                assertVisible(private, false)
            }

            screenshot("issue-playlist")
        }

        db {
            val issuesCount = Issue.selectAll().where { Issue.creator eq reporterId }.count()
            assertEquals(1, issuesCount)

            val commentsCount = IssueComment.selectAll().where { IssueComment.userId eq reporterId }.count()
            assertEquals(1, commentsCount)
        }
    }

    @Test
    fun `Can't report users`() = bmTest {
        val (uid, userIds) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (suspendedUserId, _) = createUser(suspended = true)
            val (adminUserId, _) = createUser(admin = true)

            uid to listOf(uid, adminUserId, suspendedUserId)
        }

        // Check option isn't visible as admin/owner/suspended
        userIds.forEach { userId ->
            login(userId)
            navigate("/profile/$uid")

            userPage {
                assertThat(userInfo.report).not().isVisible()
            }
        }
    }

    @Test
    fun `Can report users`() = bmTest {
        val (userId, reporterId) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (reporterId, _) = createUser()

            uid to reporterId
        }

        login(reporterId)
        navigate("/profile/$userId")

        val reportText = fixture<String>()

        userPage {
            userInfo.report.click()

            modals.confirmModal {
                text.fill(reportText)
                confirm.click()
            }
        }

        issuePage {
            waitForComment()
            assertEquals(1, commentCount())

            getComment(0) {
                assertThat(body).hasText(reportText)
                assertVisible(edit, true)
                assertVisible(public, true)
                assertVisible(private, false)
            }

            screenshot("issue-user")
        }

        db {
            val issuesCount = Issue.selectAll().where { Issue.creator eq reporterId }.count()
            assertEquals(1, issuesCount)

            val commentsCount = IssueComment.selectAll().where { IssueComment.userId eq reporterId }.count()
            assertEquals(1, commentsCount)
        }
    }

    @Test
    fun `Can't report reviews`() = bmTest {
        val (mapId, userIds) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid, true)
            val (reviewerId, _) = createUser()
            val (suspendedUserId, _) = createUser(suspended = true)
            val (adminUserId, _) = createUser(admin = true)

            review(reviewerId, mid)

            mid to listOf(reviewerId, adminUserId, suspendedUserId)
        }

        // Check option isn't visible as admin/owner/suspended
        userIds.forEach { userId ->
            login(userId)
            navigate("/maps/${toHexString(mapId)}")

            mapPage {
                tabs.reviews.click()

                reviews.get(0) {
                    assertThat(report).not().isVisible()
                }
            }
        }
    }

    @Test
    fun `Can report reviews`() = bmTest {
        val (mapId, reporterId) = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid, true)
            val (reviewerId, _) = createUser()
            val (reporterId, _) = createUser()

            review(reviewerId, mid)

            mid to reporterId
        }

        login(reporterId)
        navigate("/maps/${toHexString(mapId)}")

        val reportText = fixture<String>()

        mapPage {
            tabs.reviews.click()

            reviews.get(0) {
                report.click()
            }

            modals.confirmModal {
                text.fill(reportText)
                confirm.click()
            }
        }

        issuePage {
            waitForComment()
            assertEquals(1, commentCount())

            getComment(0) {
                assertThat(body).hasText(reportText)
                assertVisible(edit, true)
                assertVisible(public, true)
                assertVisible(private, false)
            }

            screenshot("issue-review")
        }

        db {
            val issuesCount = Issue.selectAll().where { Issue.creator eq reporterId }.count()
            assertEquals(1, issuesCount)

            val commentsCount = IssueComment.selectAll().where { IssueComment.userId eq reporterId }.count()
            assertEquals(1, commentsCount)
        }
    }
}
