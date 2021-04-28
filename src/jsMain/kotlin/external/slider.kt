package external

import react.RClass
import react.RProps

external interface ReactSliderProps: RProps {
    var min: Int
    var max: Int
    var step: Int
    var pageFn: (Int) -> Int
    var minDistance: Int
    var defaultValue: Array<Int>
    var value: Array<Int>
    var orientation: String
    var className: String
    var thumbClassName: String
    var thumbActiveClassName: String
    var withTracks: Boolean
    var trackClassName: String
    var pearling: Boolean
    var disabled: Boolean
    var snapDragDisabled: Boolean
    var invert: Boolean
    var marks: Boolean
    var markClassName: String
    var onSliderClick: (Int) -> Unit
    var ariaLabel: Array<String>
    var ariaValuetext: (ThumbState) -> String
    var onChange: (Array<Int>) -> Unit
    var onBeforeChange: (Array<Int>) -> Unit
    var onAfterChange: (Array<Int>) -> Unit
}

external interface ThumbState {
    var index: Int
    var value: Array<Int>
    var valueNow: Int
}

@JsModule("react-slider")
@JsNonModule
external object ReactSlider {
    val default: RClass<ReactSliderProps>
}