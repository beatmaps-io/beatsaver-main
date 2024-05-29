package io.beatmaps.login.server

import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.AccessTokenTable
import io.beatmaps.common.dbo.OauthClient
import io.beatmaps.common.dbo.OauthClientDao
import io.beatmaps.common.dbo.RefreshTokenTable
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import nl.myndocs.oauth2.identity.Identity
import nl.myndocs.oauth2.identity.TokenInfo
import nl.myndocs.oauth2.token.AccessToken
import nl.myndocs.oauth2.token.CodeToken
import nl.myndocs.oauth2.token.RefreshToken
import nl.myndocs.oauth2.token.TokenStore
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun UserDao.toIdentity() =
    Identity(id.value.toString(), mapOf("object" to this))

object DBTokenStore : TokenStore {
    private val codes = mutableMapOf<String, CodeToken>()

    override fun accessToken(token: String) =
        transaction {
            AccessTokenTable
                .join(RefreshTokenTable, JoinType.INNER, AccessTokenTable.refreshToken, RefreshTokenTable.id)
                .join(OauthClient, JoinType.INNER, AccessTokenTable.clientId, OauthClient.clientId)
                .join(User, JoinType.INNER, AccessTokenTable.userName, User.id)
                .selectAll()
                .where {
                    AccessTokenTable.id eq token
                }
                .singleOrNull()?.let {
                    OauthClientDao.wrapRow(it)

                    accessTokenFromResult(it)
                }
        }

    private fun accessTokenFromResult(row: ResultRow) =
        AccessToken(
            row[AccessTokenTable.id].value,
            row[AccessTokenTable.type],
            row[AccessTokenTable.expiration],
            UserDao.wrapRow(row).toIdentity(),
            row[AccessTokenTable.clientId],
            row[AccessTokenTable.scope].split(",").toSet(),
            refreshToken(row)
        )

    override fun codeToken(token: String): CodeToken? {
        var code = codes[token]

        if (code != null && code.expired()) {
            codes.remove(token)

            code = null
        }

        return code
    }

    override fun consumeCodeToken(token: String): CodeToken? = codes.remove(token)

    override fun refreshToken(token: String) =
        transaction {
            RefreshTokenTable
                .join(User, JoinType.INNER, RefreshTokenTable.userName, User.id)
                .selectAll()
                .where {
                    RefreshTokenTable.id eq token
                }.singleOrNull()?.let {
                    refreshToken(it)
                }
        }

    private fun refreshToken(row: ResultRow) = RefreshToken(
        row[RefreshTokenTable.id].value,
        row[RefreshTokenTable.expiration],
        UserDao.wrapRow(row).toIdentity(),
        row[RefreshTokenTable.clientId],
        row[RefreshTokenTable.scope].split(",").toSet()
    )

    override fun revokeAccessToken(token: String) {
        transaction {
            AccessTokenTable.deleteWhere {
                id eq token
            }
        }
    }

    override fun revokeRefreshToken(token: String) {
        transaction {
            RefreshTokenTable.deleteWhere {
                id eq token
            }
        }
    }

    override fun storeAccessToken(accessToken: AccessToken) {
        transaction {
            AccessTokenTable.insert {
                it[id] = accessToken.accessToken
                it[type] = accessToken.tokenType
                it[expiration] = accessToken.expireTime
                it[scope] = accessToken.scopes.joinToString(",")
                it[userName] = accessToken.identity?.username?.toIntOrNull()
                it[clientId] = accessToken.clientId
                it[refreshToken] = accessToken.refreshToken?.refreshToken
            }

            if (accessToken.refreshToken != null) {
                storeRefreshToken(accessToken.refreshToken!!)
            }
        }
    }

    override fun storeCodeToken(codeToken: CodeToken) {
        // Remove expired codes
        codes.entries.removeAll { it.value.expired() }
        codes[codeToken.codeToken] = codeToken
    }

    override fun storeRefreshToken(refreshToken: RefreshToken) {
        transaction {
            RefreshTokenTable.upsert(RefreshTokenTable.id) {
                it[id] = refreshToken.refreshToken
                it[expiration] = refreshToken.expireTime
                it[scope] = refreshToken.scopes.joinToString(",")
                it[userName] = refreshToken.identity?.username?.toIntOrNull()
                it[clientId] = refreshToken.clientId
            }
        }
    }

    override fun tokenInfo(token: String) =
        transaction {
            AccessTokenTable
                .join(RefreshTokenTable, JoinType.INNER, AccessTokenTable.refreshToken, RefreshTokenTable.id)
                .join(OauthClient, JoinType.INNER, AccessTokenTable.clientId, OauthClient.clientId)
                .join(User, JoinType.INNER, AccessTokenTable.userName, User.id)
                .selectAll()
                .where {
                    AccessTokenTable.id eq token
                }
                .singleOrNull()?.let {
                    val accessToken = accessTokenFromResult(it)

                    TokenInfo(
                        accessToken.identity,
                        DBClientService.convertToClient(OauthClientDao.wrapRow(it)),
                        accessToken.scopes
                    )
                }
        }

    fun deleteForUser(userId: Int) {
        transaction {
            AccessTokenTable
                .deleteWhere {
                    userName eq userId
                }

            RefreshTokenTable
                .deleteWhere {
                    userName eq userId
                }
        }
    }
}
