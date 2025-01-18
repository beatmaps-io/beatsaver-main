package external

import react.ComponentModule
import react.ComponentType
import react.Props

external interface TimeAgoProps : Props {
    var date: String
    var minPeriod: Int
}

@JsModule("react-timeago")
@JsNonModule
external object TimeAgo : ComponentModule<TimeAgoProps> {
    override val default: ComponentType<TimeAgoProps>
}
