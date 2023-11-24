package io.beatmaps.browser

import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.lang.Integer.toHexString
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MapCardTest : BrowserTestBase() {
    @Test
    fun `Can add maps to playlist from search`() = bmTest {
        val (userId, playlistId) = transaction {
            val (uid, username) = createUser()

            val pid = Playlist.insertAndGetId {
                it[name] = "Add from search for $username"
                it[description] = ""
                it[owner] = uid
                it[type] = EPlaylistType.Public
            }

            uid to pid
        }

        login(userId)

        navigate("/")
        homePage {
            getMap(0) {
                addToPlaylist.click()
            }

            modals.addToPlaylistModal {
                assertContentEquals(listOf(false), getStatuses())
                getCheckbox(0).isChecked = true
                save.click()
            }

            getMap(1) {
                addToPlaylist.click()
            }

            modals.addToPlaylistModal {
                assertContentEquals(listOf(false), getStatuses())
                save.click()
            }

            getMap(2) {
                addToPlaylist.click()
            }

            modals.addToPlaylistModal {
                assertContentEquals(listOf(false), getStatuses())
                getCheckbox(0).isChecked = true
                cancel.click()
            }
        }

        transaction {
            val count = PlaylistMap.select { PlaylistMap.playlistId eq playlistId }.count()
            assertEquals(1, count)
        }
    }

    @Test
    fun `Can add maps to bookmarks from search`() = bmTest {
        val userId = transaction {
            val (uid, _) = createUser()

            uid
        }

        fun countBookmarks() = transaction {
            Playlist
                .joinMaps(JoinType.INNER)
                .select { Playlist.type eq EPlaylistType.System and (Playlist.owner eq userId) }
                .count()
        }

        login(userId)

        navigate("/")
        homePage {
            getMap(0) {
                bookmark.click()
                assertEquals(1, countBookmarks())

                bookmark.click()
                assertEquals(0, countBookmarks())
            }
        }
    }

    @Test
    fun `Can copy bsr code from search`() = bmTest {
        val (username, mapId) = transaction {
            val (userId, username) = createUser()
            val (mapId, _) = createMap(userId)

            username to mapId
        }

        navigate("/")
        homePage {
            search("mapper:$username")

            getMap(0) {
                bsr.click()
            }
        }

        val bsr = clipboard()
        assertEquals("!bsr ${toHexString(mapId)}", bsr)
    }

    @Test
    fun `Has correct download links in search`() = bmTest {
        val (username, mapId, hash) = transaction {
            val (userId, username) = createUser()
            val (mapId, hash) = createMap(userId)

            Triple(username, mapId, hash)
        }

        navigate("/")
        homePage {
            search("mapper:$username")

            getMap(0) {
                assertEquals("beatsaver://${toHexString(mapId)}", oneClick.getAttribute("href"))
                assertEquals("https://beatsaver.com/cdn/$hash.zip", download.getAttribute("href"))
            }
        }
    }
}
