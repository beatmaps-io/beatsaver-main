package io.beatmaps.shared

import external.ReactSlider
import external.TimeAgo
import external.reactFor
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.api.UserDetail
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.oneclick
import io.beatmaps.maps.diffImg
import io.beatmaps.previewBaseUrl
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.datetime.Instant
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import react.Props
import react.PropsWithChildren
import react.PropsWithRef
import react.RefObject
import react.dom.a
import react.dom.div
import react.dom.h4
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.p
import react.dom.small
import react.dom.span
import react.fc
import react.forwardRef
import kotlin.collections.set
import kotlin.math.log
import kotlin.math.pow

external interface BotInfoProps : Props {
    var automapper: Boolean?
    var version: MapVersion?
    var marginLeft: Boolean?
}

val botInfo = fc<BotInfoProps> { props ->
    val score = (props.version?.sageScore ?: 0)
    val margin = if (props.marginLeft != false) "ms-2" else "me-2"

    fun renderBadge(color: String, title: String, text: String) =
        span("badge rounded-pill badge-$color $margin") {
            attrs.title = title
            +text
        }

    if (score < -4 || props.automapper == true) {
        renderBadge("danger", "Made by a bot", "Bot")
    } else if (score < 0) {
        renderBadge("unsure", "Could be a bot", "Unsure")
    }
}

external interface DiffIconsProps : Props {
    var diffs: List<MapDifficulty>?
}

val diffIcons = fc<DiffIconsProps> { props ->
    props.diffs?.forEach { d ->
        span("badge rounded-pill badge-${d.difficulty.color}") {
            diffImg(d)
            +d.difficulty.human()
        }
    }
}

external interface DownloadProps : Props {
    var map: MapDetail
    var version: MapVersion
}

val downloadZip = fc<DownloadProps> { props ->
    a(props.version.downloadURL) {
        attrs.rel = "noopener"
        attrs.title = "Download zip"
        attrs.attributes["aria-label"] = "Download zip"
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

external interface CopyBSProps : Props {
    var map: MapDetail
}

val copyBsr = fc<CopyBSProps> { props ->
    a("#") {
        attrs.title = "Copy BSR"
        attrs.attributes["aria-label"] = "Copy BSR"
        attrs.onClickFunction = {
            it.preventDefault()
            setClipboard("!bsr ${props.map.id}")
        }
        i("fab fa-twitch text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

fun setClipboard(str: String) {
    val tempElement = document.createElement("span")
    tempElement.textContent = str
    document.body?.appendChild(tempElement)
    val selection = window.asDynamic().getSelection()
    val range = window.document.createRange()
    selection.removeAllRanges()
    range.selectNode(tempElement)
    selection.addRange(range)
    window.document.execCommand("copy")
    selection.removeAllRanges()
    window.document.body?.removeChild(tempElement)
}

external interface LinksProps : Props {
    var map: MapDetail
    var version: MapVersion?
    var modal: RefObject<ModalComponent>
}

val links = fc<LinksProps> { props ->
    copyBsr {
        attrs.map = props.map
    }
    val version = props.version
    val altLink = if (version?.state == EMapState.Published) {
        "$previewBaseUrl?id=${props.map.id}"
    } else if (version != null) {
        "/maps/viewer/${version.hash}"
    } else {
        "#"
    }
    a(altLink) {
        attrs.title = "Preview"
        attrs.attributes["aria-label"] = "Preview"
        attrs.onClickFunction = {
            it.preventDefault()

            if (props.version?.state == EMapState.Published) {
                props.modal.current?.showById(props.map.id)
            } else {
                props.version?.hash?.let { hash ->
                    props.modal.current?.show(hash)
                }
            }
        }
        i("fas fa-play text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    oneclick {
        attrs.mapId = props.map.id
    }
    props.version?.let { v ->
        downloadZip {
            attrs.map = props.map
            attrs.version = v
        }
    }
}

external interface UploaderProps : Props {
    var map: MapDetail
    var version: MapVersion?
}

val uploader = fc<UploaderProps> { props ->
    (listOf(props.map.uploader) + (props.map.collaborators ?: listOf())).let {
        it.forEachIndexed { idx, u ->
            routeLink(u.profileLink()) {
                +u.name
            }
            if (idx < it.lastIndex) +", "
        }
    }
}

val uploaderWithInfo = fc<UploaderProps> { props ->
    uploader {
        attrs.map = props.map
    }
    botInfo {
        attrs.version = props.version
        attrs.automapper = props.map.automapper
    }
    if (props.version?.state == EMapState.Published) {
        +" - "
        TimeAgo.default {
            attrs.date = props.map.uploaded.toString()
        }
    }
}

external interface PlaylistOwnerProps : Props {
    var owner: UserDetail?
    var time: Instant
}

val playlistOwner = fc<PlaylistOwnerProps> { props ->
    props.owner?.let { owner ->
        routeLink(owner.profileLink("playlists")) {
            +owner.name
        }
        +" - "
    }
    TimeAgo.default {
        attrs.date = props.time.toString()
    }
}

external interface ReviewerProps : Props {
    var reviewer: UserDetail?
    var map: MapDetail?
    var time: Instant
}

val reviewer = fc<ReviewerProps> { props ->
    props.reviewer?.let { owner ->
        routeLink(owner.profileLink("reviews")) {
            +owner.name
        }
        +" - "
    }
    props.map?.let { map ->
        +" on "
        routeLink("/maps/${map.id}") {
            +map.name
        }
        +" - "
    }
    TimeAgo.default {
        attrs.date = props.time.toString()
    }
}

external interface ColoredCardProps : PropsWithChildren {
    var color: String
    var icon: String?
    var title: String?
    var extra: ((DIV) -> Unit)?
    var classes: String?
}

val coloredCard = fc<ColoredCardProps> {
    div("card colored " + (it.classes ?: "")) {
        it.extra?.invoke(attrs)

        div("color ${it.color}") {
            if (it.title != null) {
                attrs.title = it.title ?: ""
            }

            if (it.icon != null) {
                i("fas ${it.icon} icon") {
                    attrs.attributes["aria-hidden"] = "true"
                }
            }
        }
        div("content") {
            it.children()
        }
    }
}

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fc<MapTitleProps> {
    routeLink("/maps/${it.mapKey}") {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}

external interface RatingProps : Props {
    var up: Int
    var down: Int
    var rating: Float
}

val rating = fc<RatingProps> {
    val totalVotes = (it.up + it.down).toDouble()
    var uncertainty = 2.0.pow(-log(totalVotes / 2 + 1, 3.0))
    val weightedRange = 25.0
    val weighting = 2
    if ((totalVotes + weighting) < weightedRange) {
        uncertainty += (1 - uncertainty) * (1 - (totalVotes + weighting) * (1 / weightedRange))
    }

    div {
        small("text-center vote") {
            div("u") {
                attrs.jsStyle {
                    flex = it.up
                }
            }
            div("o") {
                attrs.jsStyle {
                    flex = if (totalVotes < 1) 1 else (uncertainty * totalVotes / (1 - uncertainty))
                }
            }
            div("d") {
                attrs.jsStyle {
                    flex = it.down
                }
            }
        }
        div("percentage") {
            attrs.title = "${it.up}/${it.down}"
            +"${it.rating}%"
        }
    }
}

external interface ToggleProps : PropsWithRef<HTMLInputElement> {
    var id: String
    var text: String
    var disabled: Boolean?
    var block: ((Boolean) -> Unit)?
    var default: Boolean?
    var className: String?
}

val toggle = forwardRef<HTMLInputElement, ToggleProps> { props, refPassed ->
    div("form-check form-switch ${props.className ?: ""}") {
        input(InputType.checkBox, classes = "form-check-input") {
            attrs.id = props.id
            attrs.defaultChecked = props.default ?: false
            attrs.disabled = props.disabled ?: false
            ref = refPassed
            attrs.onChangeFunction = { ev ->
                props.block?.invoke((ev.target as HTMLInputElement).checked)
            }
        }
        label("form-check-label") {
            attrs.reactFor = props.id
            +props.text
        }
    }
}

external interface SliderProps : Props {
    var text: String
    var currentMin: Float
    var currentMax: Float
    var max: Int?
    var block: (Array<Int>) -> Unit
    var className: String?
}

val slider = fc<SliderProps> { props ->
    val max = props.max ?: 16

    div(props.className ?: "") {
        val maxSlider = max * 10
        ReactSlider.default {
            attrs.ariaLabel = arrayOf("Min ${props.text}", "Max ${props.text}")
            attrs.value = arrayOf((props.currentMin * 10).toInt(), (props.currentMax * 10).toInt())
            attrs.max = maxSlider
            attrs.defaultValue = arrayOf(0, maxSlider)
            attrs.minDistance = 5
            attrs.onChange = props.block
        }
        div("row") {
            div("col") {
                +props.text
            }
            div("col text-end") {
                val maxStr = if (props.currentMax >= max) "∞" else props.currentMax.toString()
                +"${props.currentMin} - $maxStr"
            }
        }
    }
}

external interface UserCardProps : Props {
    var id: Int
    var avatar: String
    var username: String
    var titles: List<String>
}

var userCard = fc<UserCardProps> {
    div("d-flex align-items-center my-2") {
        img("Profile Image", it.avatar, classes = "rounded-circle me-3") {
            attrs.width = "50"
            attrs.height = "50"
        }
        div("d-inline") {
            routeLink("/profile/${it.id}") {
                h4("mb-1") {
                    +it.username
                }
            }
            p("text-muted mb-1") {
                +it.titles.joinToString(", ")
            }
        }
    }
}

external interface BookmarkButtonProps : Props {
    var bookmarked: Boolean
    var onClick: (Event, Boolean) -> Unit
}

var bookmarkButton = fc<BookmarkButtonProps> { props ->
    a("#", classes = "text-warning me-1") {
        val title = if (props.bookmarked) "Remove Bookmark" else "Add Bookmark"
        attrs.title = title
        attrs.attributes["aria-label"] = title
        attrs.onClickFunction = {
            it.preventDefault()
            props.onClick(it, props.bookmarked)
        }
        i((if (props.bookmarked) "fas" else "far") + " fa-bookmark text-warning") { }
    }
}
