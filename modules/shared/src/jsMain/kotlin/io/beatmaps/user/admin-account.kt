package io.beatmaps.user

import io.beatmaps.api.UserDetail
import react.Props

external interface AdminAccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}