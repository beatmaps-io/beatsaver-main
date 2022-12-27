package io.beatmaps.index
import kotlinx.html.title
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.dom.a
import react.dom.i
import kotlin.collections.set

external interface OneClickProps : Props {
    var mapId: String
    var modal: RefObject<ModalComponent>
}

class OneClick : RComponent<OneClickProps, State>() {
    override fun RBuilder.render() {
        a("beatsaver://${props.mapId}") {
            attrs.title = "One-Click"
            attrs.attributes["aria-label"] = "One-Click"
            i("fas fa-cloud-download-alt text-info") { }
        }
    }
}

fun RBuilder.oneclick(handler: OneClickProps.() -> Unit) =
    child(OneClick::class) {
        this.attrs(handler)
    }
