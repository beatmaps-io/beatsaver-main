package io.beatmaps.modreview

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.globalContext
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.review.CommentsInfiniteScroll
import io.beatmaps.util.useDidUpdateEffect
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.button
import react.dom.form
import react.dom.input
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.fc
import react.ref
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

val modReview = fc<Props> {
    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val resultsTable = useRef<HTMLElement>()
    val modalRef = useRef<ModalComponent>()
    val userRef = useRef<HTMLInputElement>()

    val userLocal = URLSearchParams(location.search).let { u ->
        u.get("user") ?: ""
    }

    val (user, setUser) = useState(userLocal)

    useEffectOnce {
        setPageTitle("Review Moderation")

        if (userData?.curator != true) {
            history.push("/")
        }
    }

    useEffect(location) {
        userRef.current?.value = user

        setUser(userLocal)
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<ReviewsResponse>(
            "${Config.apibase}/review/latest/$toLoad" + urlExtension(),
            generateConfig<String, ReviewsResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    modal {
        ref = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        form {
            table("table table-dark table-striped-3 modreview") {
                thead {
                    tr {
                        th { +"User" }
                        th { +"Map" }
                        th { +"Sentiment" }
                        th { +"Time" }
                        th { +"" }
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

                                    history.push("/modreview" + urlExtension())
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
                        attrs.resultsKey = user
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
                                attrs.setUser = { userStr ->
                                    userRef.current?.value = userStr
                                    history.push("/modreview" + urlExtension())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
