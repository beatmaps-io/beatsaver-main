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
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object DBTokenStore : TokenStore {
    private val codes = mutableMapOf<String, CodeToken>()

    private fun createIdentity(username: Int?, user: UserDao) =
        Identity(username.toString(), mapOf("object" to user))

    override fun accessToken(token: String) =
        transaction {
            AccessTokenTable
                .join(RefreshTokenTable, JoinType.INNER, AccessTokenTable.refreshToken, RefreshTokenTable.id)
                .join(OauthClient, JoinType.INNER, AccessTokenTable.clientId, OauthClient.clientId)
                .join(User, JoinType.INNER, AccessTokenTable.userName, User.id)
                .select {
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
            createIdentity(row[AccessTokenTable.userName], UserDao.wrapRow(row)),
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
                .select {
                    RefreshTokenTable.id eq token
                }.singleOrNull()?.let {
                    refreshToken(it)
                }
        }

    private fun refreshToken(row: ResultRow) = RefreshToken(
        row[RefreshTokenTable.id].value,
        row[RefreshTokenTable.expiration],
        createIdentity(row[RefreshTokenTable.userName], UserDao.wrapRow(row)),
        row[RefreshTokenTable.clientId],
        row[RefreshTokenTable.scope].split(",").toSet()
    )

    override fun revokeAccessToken(token: String) {
        transaction {
            AccessTokenTable.deleteWhere {
                AccessTokenTable.id eq token
            }
        }
    }

    override fun revokeRefreshToken(token: String) {
        transaction {
            RefreshTokenTable.deleteWhere {
                RefreshTokenTable.id eq token
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
                .select {
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
                    AccessTokenTable.userName eq userId
                }

            RefreshTokenTable
                .deleteWhere {
                    RefreshTokenTable.userName eq userId
                }
        }
    }
}
