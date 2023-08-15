package external

import react.ComponentClass
import react.Props

external interface TimeAgoProps : Props {
    var date: String
    var minPeriod: Int
}

@JsModule("react-timeago")
@JsNonModule
external object TimeAgo {
    val default: ComponentClass<TimeAgoProps>
}
