package io.beatmaps.modreview

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.UserData
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.review.CommentsInfiniteScroll
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import kotlinx.browser.window
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTableSectionElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.RBuilder
import react.RComponent
import react.State
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
import react.router.dom.History
import react.setState

external interface ModReviewProps : Props {
    var history: History
    var userData: UserData?
}

external interface ModReviewState : State {
    var resultsKey: Any
    var user: String?
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

        updateFromURL()
    }

    override fun componentDidUpdate(prevProps: ModReviewProps, prevState: ModReviewState, snapshot: Any) {
        updateFromURL()
    }

    private fun updateFromURL() {
        val user = URLSearchParams(window.location.search).let { u ->
            u.get("user") ?: ""
        }

        userRef.current?.value = user

        if (user != state.user) {
            setState {
                this.user = user
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

fun RBuilder.modreview(handler: ModReviewProps.() -> Unit) =
    child(ModReview::class) {
        this.attrs(handler)
    }
