package io.beatmaps.modreview

import react.Props
import kotlin.reflect.KClass

external interface ModReviewProps : Props {
    var type: KClass<*>
}