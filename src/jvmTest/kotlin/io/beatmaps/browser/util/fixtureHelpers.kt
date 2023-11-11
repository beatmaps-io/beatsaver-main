package io.beatmaps.browser.util

import com.appmattus.kotlinfixture.kotlinFixture
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.random.Random

abstract class FixtureHelpers {
    val fixture = kotlinFixture()

    fun createUser(): Pair<Int, String> {
        val now = Clock.System.now().epochSeconds
        val fuzz = fixture(1..100000)

        val username = "test-$now-$fuzz"

        return transaction {
            User.insertAndGetId {
                it[name] = username
                it[email] = "$username@beatsaver.com"
                it[password] = null
                it[verifyToken] = null
                it[uniqueName] = username
                it[active] = true
            }.value to username
        }
    }

    fun createMap(userId: Int, published: Boolean = true): Pair<Int, String> = transaction {
        fixture<MapDetail>().let { map ->
            val mId = Beatmap.insertAndGetId {
                it[name] = map.name
                it[description] = map.description
                it[uploader] = userId

                it[bpm] = map.metadata.bpm
                it[duration] = map.metadata.duration
                it[songName] = map.metadata.songName
                it[songSubName] = map.metadata.songSubName
                it[levelAuthorName] = map.metadata.levelAuthorName
                it[songAuthorName] = map.metadata.songAuthorName

                it[automapper] = false
                it[ai] = false
                it[plays] = map.stats.plays
            }.value

            val digest = randomHash()
            val vId = Versions.insertAndGetId {
                it[mapId] = mId
                it[key64] = null
                it[hash] = digest
                it[state] = if (published) EMapState.Published else EMapState.Uploaded
                it[sageScore] = 0
            }

            val diff = fixture<MapDifficulty>()
            Difficulty.insert {
                it[mapId] = mId
                it[versionId] = vId

                it[njs] = diff.njs
                it[offset] = diff.offset

                it[pReset] = diff.paritySummary.resets
                it[pError] = diff.paritySummary.errors
                it[pWarn] = diff.paritySummary.warns

                it[schemaVersion] = "2.2.0"
                it[notes] = diff.notes
                it[bombs] = diff.bombs
                it[arcs] = 0
                it[chains] = 0
                it[obstacles] = diff.obstacles
                it[events] = diff.events
                it[length] = diff.length.toBigDecimal()
                it[seconds] = diff.seconds.toBigDecimal()
                it[maxScore] = diff.maxScore
                it[label] = diff.label

                it[nps] = diff.nps.toBigDecimal()
                it[chroma] = diff.chroma
                it[ne] = diff.ne
                it[me] = diff.me
                it[cinema] = diff.cinema

                it[characteristic] = ECharacteristic.Standard
                it[difficulty] = EDifficulty.ExpertPlus
            }

            mId to digest
        }
    }

    private val charPool: List<Char> = ('a'..'f') + ('0'..'9')
    private fun randomHash() = (1..40).joinToString("") { charPool[Random.nextInt(0, charPool.size)].toString() }
}
