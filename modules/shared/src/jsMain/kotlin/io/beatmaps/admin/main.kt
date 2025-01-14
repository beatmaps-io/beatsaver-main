package io.beatmaps.admin

import external.component
import js.import.import
import react.ComponentClass
import react.ExoticComponent
import react.Props

external interface AdminModule {
    val modLog: ComponentClass<Props>
    val modReview: ComponentClass<ModReviewProps>
    val issueList: ComponentClass<Props>
    val adminAccount: ComponentClass<AdminAccountComponentProps>
}

data class AdminExotics(
    val modLog: ExoticComponent<Props>,
    val modReview: ExoticComponent<ModReviewProps>,
    val issueList: ExoticComponent<Props>,
    val adminAccount: ExoticComponent<AdminAccountComponentProps>
)

val admin = import<AdminModule>("./BeatMaps-admin").let { promise ->
    AdminExotics(
        promise.component { it.modLog },
        promise.component { it.modReview },
        promise.component { it.issueList },
        promise.component { it.adminAccount }
    )
}
