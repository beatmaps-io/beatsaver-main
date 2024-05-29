package io.beatmaps.login.server

import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.AccessTokenTable
import io.beatmaps.common.dbo.RefreshTokenTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Timer
import java.util.TimerTask

class TokenStoreCleaner : TimerTask() {
    override fun run() {
        try {
            transaction {
                AccessTokenTable.deleteWhere {
                    expiration less NowExpression(expiration)
                }

                RefreshTokenTable.deleteWhere {
                    expiration less NowExpression(expiration)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Exception while running task" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun scheduleTokenCleanup() {
            Timer("Token Cleanup").scheduleAtFixedRate(TokenStoreCleaner(), 120 * 1000L, 6 * 60 * 60 * 1000L)
        }
    }
}
