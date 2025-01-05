package io.beatmaps.issues

import external.Axios
import external.TimeAgo
import external.axiosGet
import external.generateConfig
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
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
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
import org.w3c.dom.HTMLInputElement
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

val issuesPage = fc<Props>("issuesPage") {
    val (loading, setLoading) = useState(false)
    val (issue, setIssue) = useState<IssueDetail?>(null)
    val captchaRef = useRef<ICaptchaHandler>()
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
                attrs.id = "issue-info"
                attrs.icon = "fa-plus"
                attrs.color = "success"
                attrs.headerClass = "d-flex"
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
                    if (userData?.curator == true) {
                        div("link-buttons") {
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
                                p {
                                    mapTitle {
                                        attrs.title = map.name
                                        attrs.mapKey = map.id
                                    }
                                }
                            }
                            i.data.review.creator?.let { user ->
                                p {
                                    routeLink(user.profileLink()) {
                                        +user.name
                                    }
                                }
                            }
                            p {
                                textToContent(i.data.review.text)
                            }
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
                        captchaRef.current?.execute()?.then {
                            Axios.put<ActionResponse>(
                                "${Config.apibase}/issues/comments/${issue.id}",
                                IssueCommentRequest(it, text, publicRef.current?.checked),
                                generateConfig<IssueCommentRequest, ActionResponse>()
                            )
                        }?.then { it }
                    }
                    attrs.successCallback = {
                        loadIssue()
                    }

                    if (userData?.curator == true) {
                        toggle {
                            attrs.toggleRef = publicRef
                            attrs.className = "me-4 mb-2 mt-auto"
                            attrs.id = "new-public"
                            attrs.disabled = loading
                            attrs.text = "Public"
                            attrs.default = true
                        }
                    }

                    captcha(captchaRef)
                }
            }
        }
    }
}
