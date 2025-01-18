package io.beatmaps.user

import external.component
import js.import.importAsync
import react.ComponentType
import react.ExoticComponent
import react.Props

external interface UserModule {
    val changeEmail: ComponentType<Props>
    val forgot: ComponentType<Props>
    val login: ComponentType<Props>
    val register: ComponentType<Props>
    val username: ComponentType<Props>
    val reset: ComponentType<Props>
    val quest: ComponentType<Props>
    val authorize: ComponentType<Props>
    val userList: ComponentType<Props>
    val followList: ComponentType<FollowListProps>
}

data class UserExotics(
    val changeEmail: ExoticComponent<Props>,
    val forgot: ExoticComponent<Props>,
    val login: ExoticComponent<Props>,
    val register: ExoticComponent<Props>,
    val username: ExoticComponent<Props>,
    val reset: ExoticComponent<Props>,
    val quest: ExoticComponent<Props>,
    val authorize: ExoticComponent<Props>,
    val userList: ExoticComponent<Props>,
    val followList: ExoticComponent<FollowListProps>
)

val user = importAsync<UserModule>("./BeatMaps-user").let { promise ->
    UserExotics(
        promise.component { it.changeEmail },
        promise.component { it.forgot },
        promise.component { it.login },
        promise.component { it.register },
        promise.component { it.username },
        promise.component { it.reset },
        promise.component { it.quest },
        promise.component { it.authorize },
        promise.component { it.userList },
        promise.component { it.followList }
    )
}
