package external

import js.import.importAsync
import js.promise.Promise
import react.Context
import react.ExoticComponent
import react.Props
import react.ReactElement

external interface DateRangePickeProps : Props {
    var startDate: Moment?
    var startDateId: String
    var endDate: Moment?
    var endDateId: String
    var onFocusChange: (String) -> Unit
    var onDatesChange: (DateRange) -> Unit
    var focusedInput: String?
    var isOutsideRange: (Moment) -> Boolean
    var renderCalendarInfo: () -> ReactElement<*>?
    var displayFormat: String
    var small: Boolean
    var numberOfMonths: Int
}

external class DateRange {
    val startDate: Moment?
    val endDate: Moment?
}

external interface ReactDatesInit

external interface ReactDatesDefaultTheme {
    val default: dynamic
}

external interface WithStylesContext {
    val default: Context<*>
}

external interface ThemedStyleSheet {
    val default: ThemedStyleSheetObj
}

external class ThemedStyleSheetObj {
    fun registerInterface(inf: dynamic)
    fun registerTheme(theme: dynamic)
}

external interface ReactWithStylesCSS

data class ReactDatesExotics(val dateRangePicker: ExoticComponent<DateRangePickeProps>)

val dates = ReactDatesExotics(
    react.lazy {
        // Init first then pull in component
        Promise.all(
            arrayOf(
                importAsync<ReactDatesInit>("react-dates/initialize"),
                importAsync<ReactDatesDefaultTheme>("react-dates/lib/theme/DefaultTheme"),
                importAsync<WithStylesContext>("react-with-styles/lib/WithStylesContext"),
                importAsync<ThemedStyleSheet>("react-with-styles/lib/ThemedStyleSheet"),
                importAsync<ReactWithStylesCSS>("react-with-styles-interface-css")
            )
        ).flatThen {
            importAsync("react-dates/lib/components/DateRangePicker")
        }
    }
)
