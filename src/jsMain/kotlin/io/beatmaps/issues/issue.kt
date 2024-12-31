package io.beatmaps.issues

import external.Axios
import external.IReCAPTCHA
import external.TimeAgo
import external.axiosGet
import external.generateConfig
import external.recaptcha
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.HydratedMapReportData
import io.beatmaps.api.HydratedPlaylistReportData
import io.beatmaps.api.HydratedReviewReportData
import io.beatmaps.api.HydratedUserReportData
import io.beatmaps.api.IssueCommentRequest
import io.beatmaps.api.IssueDetail
import io.beatmaps.api.IssueUpdateRequest
import io.beatmaps.globalContext
import io.beatmaps.maps.testplay.TimelineEntrySectionRenderer
import io.beatmaps.maps.testplay.timelineEntry
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.util.textToContent
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.b
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.span
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect
import react.useRef
import react.useState
import web.html.HTMLInputElement
import kotlin.js.Promise

val issuesPage = fc<Props> {
    val (loading, setLoading) = useState(false)
    val (issue, setIssue) = useState<IssueDetail?>(null)
    val captchaRef = useRef<IReCAPTCHA>()
    val publicRef = useRef<HTMLInputElement>()

    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val params = useParams()
    val id = params["id"]

    val loadIssue = {
        axiosGet<IssueDetail>("${Config.apibase}/issues/$id").then { res ->
            setIssue(res.data)
        }.catch {
            history.push("/")
        }
    }

    useEffect(id) {
        setPageTitle("Issue - $id")
        loadIssue()
    }

    div("timeline") {
        // Watch out, this must be at the top
        div("line text-muted") {}

        issue?.let { i ->
            val open = i.closedAt == null

            timelineEntry {
                attrs.icon = "fa-plus"
                attrs.color = "success"
                attrs.headerCallback = TimelineEntrySectionRenderer {
                    span {
                        if (!open) {
                            b("text-danger-light me-2") {
                                +"[CLOSED]"
                            }
                        }
                        b {
                            +i.type.name
                        }
                        +" - "
                        TimeAgo.default {
                            attrs.date = i.createdAt.toString()
                        }
                    }
                    if (userData?.admin == true) {
                        div("ms-auto link-buttons flex-shrink-0") {
                            a("#") {
                                attrs.title = if (open) "Archive" else "Reopen"
                                attrs.onClickFunction = { ev ->
                                    ev.preventDefault()
                                    Axios.post<ActionResponse>(
                                        "${Config.apibase}/issues/${i.id}",
                                        IssueUpdateRequest(open),
                                        generateConfig<IssueUpdateRequest, ActionResponse>()
                                    ).then {
                                        loadIssue()
                                    }
                                }

                                i("fas text-info fa-${if (open) "archive" else "folder-open"}") { }
                            }
                        }
                    }
                }
                attrs.bodyCallback = TimelineEntrySectionRenderer {
                    p {
                        routeLink(i.creator.profileLink()) {
                            +i.creator.name
                        }
                        +" created a report for:"
                    }
                    when (i.data) {
                        is HydratedMapReportData -> {
                            i.data.map.let { map ->
                                mapTitle {
                                    attrs.title = map.name
                                    attrs.mapKey = map.id
                                }
                                p {
                                    uploaderWithInfo {
                                        attrs.map = map
                                        attrs.version = map.mainVersion()
                                    }
                                }
                            }
                        }
                        is HydratedUserReportData -> {
                            routeLink(i.data.user.profileLink()) {
                                +i.data.user.name
                            }
                        }
                        is HydratedPlaylistReportData -> {
                            routeLink(i.data.playlist.link()) {
                                +i.data.playlist.name
                            }
                        }
                        is HydratedReviewReportData -> {
                            i.data.review.map?.let { map ->
                                mapTitle {
                                    attrs.title = map.name
                                    attrs.mapKey = map.id
                                }
                            }
                            i.data.review.creator?.let { user ->
                                routeLink(user.profileLink()) {
                                    +user.name
                                }
                            }
                            textToContent(i.data.review.text)
                        }
                        null -> {}
                    }
                }
            }

            issue.comments?.forEach { comment ->
                issueComment {
                    attrs.issueOpen = open
                    attrs.issueId = issue.id
                    attrs.comment = comment
                }
            }

            if (open) {
                newIssueComment {
                    attrs.buttonText = "Add comment"
                    attrs.loadingCallback = {
                        setLoading(it)
                    }
                    attrs.saveCallback = { text ->
                        val res = captchaRef.current?.executeAsync()?.then {
                            Axios.put<ActionResponse>(
                                "${Config.apibase}/issues/comments/${issue.id}",
                                IssueCommentRequest(it, text, publicRef.current?.checked),
                                generateConfig<IssueCommentRequest, ActionResponse>()
                            )
                        } ?: Promise.reject(IllegalStateException("Captcha not present"))

                        res.then { it }
                    }
                    attrs.successCallback = {
                        loadIssue()
                    }

                    if (userData?.admin == true) {
                        toggle {
                            ref = publicRef
                            attrs.className = "me-4 mb-2 mt-auto"
                            attrs.id = "new-public"
                            attrs.disabled = loading
                            attrs.text = "Public"
                            attrs.default = true
                        }
                    }

                    recaptcha(captchaRef)
                }
            }
        }
    }
}
