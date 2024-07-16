package io.beatmaps.api

import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistTest : ApiTestBase() {
    data class PlaylistBatchCase(val hashes: List<String>, val keys: List<String>, val ignoreUnknown: Boolean, val status: HttpStatusCode, val rowsInTable: Set<Int>, val message: String)

    @Test
    fun testBatchAdd() = testApplication {
        val client = setup()

        val (playlistId, mapOne, mapTwo) = transaction {
            val pId = createPlaylist(1)

            val maps = Beatmap.joinVersions().selectAll().where {
                Beatmap.deletedAt.isNull()
            }.limit(2).complexToBeatmap().map {
                MapDetail.from(it, "")
            }

            Triple(pId, maps[0], maps[1])
        }

        val validHash = mapOne.mainVersion()?.hash ?: ""
        val validKey = mapTwo.id

        val invalidHash = "z0f4c5dca2ea0e753abc453bed032a61758263bf"
        val invalidKey = "z"
        val notFoundHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val notFoundKey = "aaaaaaa"

        val cases = listOf(
            PlaylistBatchCase(listOf(validHash, invalidHash), listOf(validKey, invalidKey), true, HttpStatusCode.OK, setOf(mapOne.intId(), mapTwo.intId()), "can insert multiple maps"),
            PlaylistBatchCase(listOf(validHash), listOf(), false, HttpStatusCode.OK, setOf(mapOne.intId()), "can insert a valid hash"),
            PlaylistBatchCase(listOf(), listOf(validKey), false, HttpStatusCode.OK, setOf(mapTwo.intId()), "can insert a valid key"),
            PlaylistBatchCase(listOf(invalidHash), listOf(), false, HttpStatusCode.NotFound, setOf(), "rejects invalid hash"),
            PlaylistBatchCase(listOf(), listOf(invalidKey), false, HttpStatusCode.BadRequest, setOf(), "rejects invalid key"),
            PlaylistBatchCase(listOf(invalidHash), listOf(validKey), true, HttpStatusCode.OK, setOf(mapTwo.intId()), "ignores invalid hash"),
            PlaylistBatchCase(listOf(validHash), listOf(invalidKey), true, HttpStatusCode.OK, setOf(mapOne.intId()), "ignores invalid key"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(validKey), false, HttpStatusCode.NotFound, setOf(), "rejects unknown hash, does not insert others"),
            PlaylistBatchCase(listOf(validHash), listOf(notFoundKey), false, HttpStatusCode.NotFound, setOf(), "rejects unknown key, does not insert others"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(), false, HttpStatusCode.NotFound, setOf(), "rejects unknown hash"),
            PlaylistBatchCase(listOf(), listOf(notFoundKey), false, HttpStatusCode.NotFound, setOf(), "rejects unknown key"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(validKey), true, HttpStatusCode.OK, setOf(mapTwo.intId()), "ignores unknown hash"),
            PlaylistBatchCase(listOf(validHash), listOf(notFoundKey), true, HttpStatusCode.OK, setOf(mapOne.intId()), "ignores unknown key")
        )

        cases.forEach { case ->
            transaction {
                PlaylistMap.deleteWhere {
                    PlaylistMap.playlistId eq playlistId
                }
            }

            // Adds valid hash
            val response = client.post("/api/playlists/id/$playlistId/batch") {
                contentType(ContentType.Application.Json)
                setBody(PlaylistBatchRequest(case.hashes, case.keys, true, case.ignoreUnknown))
            }

            val maps = transaction {
                PlaylistMap.select(PlaylistMap.mapId).where {
                    PlaylistMap.playlistId eq playlistId
                }.map { row -> row[PlaylistMap.mapId].value }.toSet()
            }

            assertEquals(case.status, response.status, "Status should match expected value when checking if playlist endpoint ${case.message}")
            assertEquals(case.rowsInTable, maps, "Maps in playlist should match expected set when checking if playlist endpoint ${case.message}")
        }

        transaction {
            PlaylistMap.deleteWhere { PlaylistMap.playlistId eq playlistId }
            Playlist.update({ Playlist.id eq playlistId }) {
                it[deletedAt] = NowExpression(deletedAt)
            }
        }
    }

    @Test
    fun testBatchDelete() = testApplication {
        val client = setup()

        val (plId, mapOne, mapTwo) = transaction {
            val pId = createPlaylist(1)

            val maps = Beatmap.joinVersions().selectAll().where {
                Beatmap.deletedAt.isNull()
            }.limit(2).complexToBeatmap().map {
                MapDetail.from(it, "")
            }

            Triple(pId, maps[0], maps[1])
        }

        val validHash = mapOne.mainVersion()?.hash ?: ""
        val validKey = mapTwo.id

        val invalidHash = "z0f4c5dca2ea0e753abc453bed032a61758263bf"
        val invalidKey = "z"
        val notFoundHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val notFoundKey = "aaaaaaa"

        val cases = listOf(
            PlaylistBatchCase(listOf(validHash, invalidHash), listOf(validKey, invalidKey), true, HttpStatusCode.OK, setOf(), "can remove multiple maps"),
            PlaylistBatchCase(listOf(validHash), listOf(), false, HttpStatusCode.OK, setOf(mapTwo.intId()), "can remove a valid hash"),
            PlaylistBatchCase(listOf(), listOf(validKey), false, HttpStatusCode.OK, setOf(mapOne.intId()), "can remove a valid key"),
            PlaylistBatchCase(listOf(invalidHash), listOf(), false, HttpStatusCode.NotFound, setOf(mapOne.intId(), mapTwo.intId()), "rejects invalid hash"),
            PlaylistBatchCase(listOf(), listOf(invalidKey), false, HttpStatusCode.BadRequest, setOf(mapOne.intId(), mapTwo.intId()), "rejects invalid key"),
            PlaylistBatchCase(listOf(invalidHash), listOf(validKey), true, HttpStatusCode.OK, setOf(mapOne.intId()), "ignores invalid hash"),
            PlaylistBatchCase(listOf(validHash), listOf(invalidKey), true, HttpStatusCode.OK, setOf(mapTwo.intId()), "ignores invalid key"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(validKey), false, HttpStatusCode.NotFound, setOf(mapOne.intId(), mapTwo.intId()), "rejects unknown hash, does not remove others"),
            PlaylistBatchCase(listOf(validHash), listOf(notFoundKey), false, HttpStatusCode.NotFound, setOf(mapOne.intId(), mapTwo.intId()), "rejects unknown key, does not remove others"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(), false, HttpStatusCode.NotFound, setOf(mapOne.intId(), mapTwo.intId()), "rejects unknown hash"),
            PlaylistBatchCase(listOf(), listOf(notFoundKey), false, HttpStatusCode.NotFound, setOf(mapOne.intId(), mapTwo.intId()), "rejects unknown key"),
            PlaylistBatchCase(listOf(notFoundHash), listOf(validKey), true, HttpStatusCode.OK, setOf(mapOne.intId()), "ignores unknown hash"),
            PlaylistBatchCase(listOf(validHash), listOf(notFoundKey), true, HttpStatusCode.OK, setOf(mapTwo.intId()), "ignores unknown key")
        )

        cases.forEach { case ->
            transaction {
                PlaylistMap.insertIgnore {
                    it[playlistId] = plId
                    it[mapId] = mapOne.intId()
                    it[order] = 1f
                }
                PlaylistMap.insertIgnore {
                    it[playlistId] = plId
                    it[mapId] = mapTwo.intId()
                    it[order] = 2f
                }
            }

            // Adds valid hash
            val response = client.post("/api/playlists/id/$plId/batch") {
                contentType(ContentType.Application.Json)
                setBody(PlaylistBatchRequest(case.hashes, case.keys, false, case.ignoreUnknown))
            }

            val maps = transaction {
                PlaylistMap.select(PlaylistMap.mapId).where {
                    PlaylistMap.playlistId eq plId
                }.map { row -> row[PlaylistMap.mapId].value }.toSet()
            }

            assertEquals(case.status, response.status, "Status should match expected value when checking if playlist endpoint ${case.message}")
            assertEquals(case.rowsInTable, maps, "Maps in playlist should match expected set when checking if playlist endpoint ${case.message}")
        }

        transaction {
            PlaylistMap.deleteWhere { playlistId eq plId }
            Playlist.update({ Playlist.id eq plId }) {
                it[deletedAt] = NowExpression(deletedAt)
            }
        }
    }
}
