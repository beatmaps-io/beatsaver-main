package io.beatmaps.api.user

import io.beatmaps.api.AccountType
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserFollowData
import io.beatmaps.api.UserStats
import io.beatmaps.api.toTier
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.UserDao
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.ResultRow

fun UserDetail.Companion.getAvatar(other: UserDao) = other.avatar ?: "https://www.gravatar.com/avatar/${other.hash ?: UserCrypto.md5(other.uniqueName ?: other.name)}?d=retro"

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null, followData: UserFollowData? = null, description: Boolean = false, patreon: Boolean = false) =
    UserDetail(
        other.id.value, other.uniqueName ?: other.name, if (description) other.description else null, other.uniqueName != null, other.hash, if (roles) other.testplay else null,
        getAvatar(other), stats, followData, if (other.discordId != null) AccountType.DISCORD else AccountType.SIMPLE,
        admin = other.admin, curator = other.curator, seniorCurator = other.seniorCurator, curatorTab = other.curatorTab, verifiedMapper = other.verifiedMapper, suspendedAt = other.suspendedAt?.toKotlinInstant(),
        playlistUrl = "${Config.apiBase(true)}/users/id/${other.id.value}/playlist", patreon = if (patreon) other.patreon.toTier() else null
    )

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false, stats: UserStats? = null, followData: UserFollowData? = null, description: Boolean = false, patreon: Boolean = false) =
    from(UserDao.wrapRow(row), roles, stats, followData, description, patreon)
