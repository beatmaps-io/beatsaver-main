package io.beatmaps.util

import io.beatmaps.api.UserDetail

fun userTitles(u: UserDetail?) = listOfNotNull(
    if (u?.admin == true) "Admin" else null,
    if (u?.curator == true) "Curator" else null,
    if (u?.patreon != null) u.patreon.title else null,
    (if (u?.verifiedMapper == true) "Verified " else "") +
        if (u?.stats?.totalMaps != 0) "Mapper" else "User"
)
