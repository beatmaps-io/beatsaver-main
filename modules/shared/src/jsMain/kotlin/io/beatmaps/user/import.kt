package io.beatmaps.user

import external.component
import js.import.import
import react.ComponentClass
import react.ExoticComponent
import react.Props

external interface UserModule {
    val changeEmail: ComponentClass<Props>
    val forgot: ComponentClass<Props>
    val login: ComponentClass<Props>
    val register: ComponentClass<Props>
    val username: ComponentClass<Props>
    val reset: ComponentClass<Props>
    val quest: ComponentClass<Props>
    val authorize: ComponentClass<Props>
}

data class UserExotics(
    val changeEmail: ExoticComponent<Props>,
    val forgot: ExoticComponent<Props>,
    val login: ExoticComponent<Props>,
    val register: ExoticComponent<Props>,
    val username: ExoticComponent<Props>,
    val reset: ExoticComponent<Props>,
    val quest: ExoticComponent<Props>,
    val authorize: ExoticComponent<Props>
)

val user = import<UserModule>("./BeatMaps-user").let { promise ->
    UserExotics(
        promise.component { it.changeEmail },
        promise.component { it.forgot },
        promise.component { it.login },
        promise.component { it.register },
        promise.component { it.username },
        promise.component { it.reset },
        promise.component { it.quest },
        promise.component { it.authorize }
    )
}