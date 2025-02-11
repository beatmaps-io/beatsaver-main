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
import io.beatmaps.index.beatmapInfo
import io.beatmaps.maps.testplay.TimelineEntrySectionRenderer
import io.beatmaps.maps.testplay.timelineEntry
import io.beatmaps.playlist.playlists
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.loadingElem
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.review.reviewItem
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import io.beatmaps.util.useAudio
import react.Props
import react.Suspense
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
    val modalRef = useRef<ModalCallbacks>()

    val audio = useAudio()
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

    modal {
        callbacks = modalRef
    }

    modalContext.Provider {
        value = modalRef
        div {
            className = ClassName("timeline rdetail")
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
                        globalContext.Provider {
                            // Render sub-items as if logged out
                            value = null
                            p {
                                routeLink(i.creator.profileLink()) {
                                    +i.creator.name
                                }
                                +" created a report for:"
                            }
                            when (i.data) {
                                is HydratedMapReportData -> {
                                    i.data.detail()?.let { snapshot ->
                                        b {
                                            +"Snapshot view:"
                                        }
                                        beatmapInfo {
                                            obj = snapshot
                                            version = snapshot.latestVersion()
                                            this.audio = audio
                                        }

                                        b {
                                            +"Description: "
                                        }
                                        p {
                                            className = ClassName("text-break")
                                            textToContent(snapshot.description)
                                        }
                                    }

                                    i.data.map?.let { map ->
                                        b {
                                            +"Current view:"
                                        }
                                        beatmapInfo {
                                            obj = map
                                            version = map.mainVersion()
                                            this.audio = audio
                                        }

                                        b {
                                            +"Description: "
                                        }
                                        p {
                                            className = ClassName("text-break")
                                            textToContent(map.description)
                                        }
                                    }
                                }

                                is HydratedUserReportData -> {
                                    i.data.detail()?.let { snapshot ->
                                        b {
                                            +"Snapshot view:"
                                        }
                                        p {
                                            routeLink(snapshot.profileLink()) {
                                                +snapshot.name
                                            }
                                        }
                                        b {
                                            +"Description: "
                                        }
                                        p {
                                            className = ClassName("text-break")
                                            textToContent(snapshot.description ?: "")
                                        }
                                    }

                                    i.data.user?.let { user ->
                                        b {
                                            +"Current view:"
                                        }
                                        p {
                                            routeLink(user.profileLink()) {
                                                +user.name
                                            }
                                        }
                                        b {
                                            +"Description: "
                                        }
                                        p {
                                            className = ClassName("text-break")
                                            textToContent(user.description ?: "")
                                        }
                                    }
                                }

                                is HydratedPlaylistReportData -> {
                                    i.data.detail()?.let { snapshot ->
                                        b {
                                            +"Snapshot view:"
                                        }
                                        playlists.info {
                                            playlist = snapshot
                                            small = true
                                        }

                                        b {
                                            +"Description: "
                                        }
                                        p {
                                            textToContent(snapshot.description)
                                        }
                                    }

                                    i.data.playlist?.let { pl ->
                                        Suspense {
                                            fallback = loadingElem
                                            b {
                                                +"Current view:"
                                            }
                                            playlists.info {
                                                playlist = pl
                                                small = true
                                            }

                                            b {
                                                +"Description: "
                                            }
                                            p {
                                                textToContent(pl.description)
                                            }
                                        }
                                    }
                                }

                                is HydratedReviewReportData -> {
                                    div {
                                        className = ClassName("reviews")

                                        i.data.review?.map?.let { map ->
                                            b {
                                                +"Map:"
                                            }

                                            beatmapInfo {
                                                obj = map
                                                version = map.mainVersion()
                                                this.audio = audio
                                            }
                                        }

                                        i.data.detail()?.let { snapshot ->
                                            b {
                                                +"Snapshot view:"
                                            }

                                            reviewItem {
                                                obj = snapshot
                                                userId = snapshot.creator?.id ?: -1
                                                map = snapshot.map
                                            }
                                        }

                                        i.data.review?.let { review ->
                                            b {
                                                +"Current view:"
                                            }

                                            reviewItem {
                                                obj = review
                                                userId = review.creator?.id ?: -1
                                                map = review.map
                                            }
                                        }
                                    }
                                }

                                null -> {}
                            }
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
}
