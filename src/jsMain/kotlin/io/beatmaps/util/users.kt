package io.beatmaps.util

import io.beatmaps.api.UserDetail

fun userTitles(u: UserDetail?) = listOfNotNull(
    if (u?.admin == true) "Admin" else null,
    if (u?.seniorCurator == true) "Senior Curator" else
        { if (u?.curator == true) "Curator" else null },
    if (u?.patreon?.supporting == true) u.patreon.title else null,
    (if (u?.verifiedMapper == true) "Verified " else "") +
        if (u?.stats?.totalMaps != 0) "Mapper" else "User"
)
