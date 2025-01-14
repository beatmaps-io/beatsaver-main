package io.beatmaps.admin

import io.beatmaps.api.UserDetail
import react.Props
import kotlin.reflect.KClass

external interface ModReviewProps : Props {
    var type: KClass<*>
}

external interface AdminAccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}
