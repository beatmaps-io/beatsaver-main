package io.beatmaps.modreview

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.UserData
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.common.Config
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.review.CommentsInfiniteScroll
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTableSectionElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.form
import react.dom.input
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface ModReviewProps : RProps {
    var history: RouteResultHistory
    var userData: UserData?
    var user: String?
}

external interface ModReviewState : RState {
    var resultsKey: Any
}

class ModReview : RComponent<ModReviewProps, ModReviewState>() {
    private val resultsTable = createRef<HTMLTableSectionElement>()
    private val modalRef = createRef<ModalComponent>()
    private val userRef = createRef<HTMLInputElement>()

    override fun componentDidMount() {
        setPageTitle("Review Moderation")

        if (props.userData?.curator != true) {
            props.history.push("/")
        }

        userRef.current?.value = props.user ?: ""
    }

    override fun componentWillReceiveProps(nextProps: ModReviewProps) {
        userRef.current?.value = nextProps.user ?: ""

        if (props.user != nextProps.user) {
            setState {
                resultsKey = Any()
            }
        }
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<ReviewsResponse>(
            "${Config.apibase}/review/latest/$toLoad" + urlExtension(),
            generateConfig<String, ReviewsResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    override fun RBuilder.render() {
        modal {
            ref = modalRef
        }

        form {
            table("table table-dark table-striped-3 modreview") {
                thead {
                    tr {
                        th { +"User" }
                        th { +"Map" }
                        th { +"Sentiment" }
                        th { +"Time" }
                        th { + "" }
                    }
                    tr {
                        td {
                            input(InputType.text, classes = "form-control") {
                                attrs.placeholder = "User"
                                attrs.attributes["aria-label"] = "User"
                                ref = userRef
                            }
                        }
                        td {
                            attrs.colSpan = "4"
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                attrs.onClickFunction = {
                                    it.preventDefault()

                                    props.history.push("/modreview" + urlExtension())
                                }

                                +"Filter"
                            }
                        }
                    }
                }
                tbody {
                    ref = resultsTable
                    key = "modreviewTable"

                    child(CommentsInfiniteScroll::class) {
                        attrs.resultsKey = state.resultsKey
                        attrs.rowHeight = 95.5
                        attrs.itemsPerPage = 20
                        attrs.container = resultsTable
                        attrs.loadPage = loadPage
                        attrs.childFilter = {
                            !it.hasClass("hiddenRow")
                        }
                        attrs.renderElement = InfiniteScrollElementRenderer {
                            modReviewEntryRenderer {
                                attrs.entry = it
                                attrs.modal = modalRef
                                attrs.setUser = { userStr ->
                                    userRef.current?.value = userStr
                                    props.history.push("/modreview" + urlExtension())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun urlExtension(): String {
        val params = listOfNotNull(
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }
}

fun RBuilder.modreview(handler: ModReviewProps.() -> Unit): ReactElement {
    return child(ModReview::class) {
        this.attrs(handler)
    }
}
