package external

import react.RClass
import react.RProps

external interface TimeAgoProps: RProps {
    var date: String
}

@JsModule("react-timeago")
@JsNonModule
external object TimeAgo {
    val default: RClass<TimeAgoProps>
}
