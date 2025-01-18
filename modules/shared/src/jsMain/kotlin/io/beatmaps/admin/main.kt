package io.beatmaps.admin

import external.component
import js.import.importAsync
import react.ComponentType
import react.ExoticComponent
import react.Props

external interface AdminModule {
    val modLog: ComponentType<Props>
    val modReview: ComponentType<ModReviewProps>
    val issueList: ComponentType<Props>
    val adminAccount: ComponentType<AdminAccountComponentProps>
}

data class AdminExotics(
    val modLog: ExoticComponent<Props>,
    val modReview: ExoticComponent<ModReviewProps>,
    val issueList: ExoticComponent<Props>,
    val adminAccount: ExoticComponent<AdminAccountComponentProps>
)

val admin = importAsync<AdminModule>("./BeatMaps-admin").let { promise ->
    AdminExotics(
        promise.component { it.modLog },
        promise.component { it.modReview },
        promise.component { it.issueList },
        promise.component { it.adminAccount }
    )
}
