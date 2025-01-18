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
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.router.useParams
import react.use
import react.useEffect
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLInputElement

val issuesPage = fcmemo<Props>("issuesPage") {
    val (loading, setLoading) = useState(false)
    val (issue, setIssue) = useState<IssueDetail?>(null)
    val captchaRef = useRef<ICaptchaHandler>()
    val publicRef = useRef<HTMLInputElement>()

    val userData = use(globalContext)
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

    div {
        className = ClassName("timeline")
        // Watch out, this must be at the top
        div {
            className = ClassName("line text-muted")
        }

        issue?.let { i ->
            val open = i.closedAt == null

            timelineEntry {
                this.id = "issue-info"
                icon = "fa-plus"
                color = "success"
                headerClass = "d-flex"
                headerCallback = TimelineEntrySectionRenderer {
                    span {
                        if (!open) {
                            b {
                                className = ClassName("text-danger-light me-2")
                                +"[CLOSED]"
                            }
                        }
                        b {
                            +i.type.name
                        }
                        +" - "
                        TimeAgo.default {
                            date = i.createdAt.toString()
                        }
                    }
                    if (userData?.curator == true) {
                        div {
                            className = ClassName("link-buttons")
                            a {
                                href = "#"

                                title = if (open) "Archive" else "Reopen"
                                onClick = { ev ->
                                    ev.preventDefault()
                                    Axios.post<ActionResponse>(
                                        "${Config.apibase}/issues/${i.id}",
                                        IssueUpdateRequest(open),
                                        generateConfig<IssueUpdateRequest, ActionResponse>()
                                    ).then {
                                        loadIssue()
                                    }
                                }

                                i {
                                    className = ClassName("fas text-info fa-${if (open) "archive" else "folder-open"}")
                                }
                            }
                        }
                    }
                }
                bodyCallback = TimelineEntrySectionRenderer {
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
                                    title = map.name
                                    mapKey = map.id
                                }
                                p {
                                    uploaderWithInfo {
                                        this.map = map
                                        version = map.mainVersion()
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
                                        title = map.name
                                        mapKey = map.id
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
                    issueOpen = open
                    issueId = issue.id
                    this.comment = comment
                }
            }

            if (open) {
                newIssueComment {
                    buttonText = "Add comment"
                    loadingCallback = {
                        setLoading(it)
                    }
                    saveCallback = { text ->
                        captchaRef.current?.execute()?.then {
                            Axios.put<ActionResponse>(
                                "${Config.apibase}/issues/comments/${issue.id}",
                                IssueCommentRequest(it, text, publicRef.current?.checked),
                                generateConfig<IssueCommentRequest, ActionResponse>()
                            )
                        }?.then { it }
                    }
                    successCallback = {
                        loadIssue()
                    }

                    if (userData?.curator == true) {
                        toggle {
                            toggleRef = publicRef
                            className = "me-4 mb-2 mt-auto"
                            this.id = "new-public"
                            disabled = loading
                            text = "Public"
                            default = true
                        }
                    }

                    captcha {
                        key = "captcha"
                        this.captchaRef = captchaRef
                        page = "issues"
                    }
                }
            }
        }
    }
}
