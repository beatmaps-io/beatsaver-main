package io.beatmaps.api

import io.beatmaps.common.api.SuspensionType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Suspensions
import org.jetbrains.exposed.sql.and

fun isSuspended(userId: Int, type: SuspensionType) =
    !Suspensions
        .select(Suspensions.id)
        .where {
            (Suspensions.userId eq userId) and Suspensions.revokedAt.isNull() and (Suspensions.type eq type) and
                (Suspensions.expireAt greater NowExpression(Suspensions.expireAt))
        }
        .empty()
