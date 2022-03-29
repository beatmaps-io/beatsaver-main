package io.beatmaps.index
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.a
import react.dom.i
import kotlin.collections.set

external interface OneClickProps : RProps {
    var mapId: String
    var modal: RReadableRef<ModalComponent>
}

class OneClick : RComponent<OneClickProps, RState>() {
    override fun RBuilder.render() {
        a("beatsaver://${props.mapId}") {
            attrs.title = "One-Click"
            attrs.attributes["aria-label"] = "One-Click"
            i("fas fa-cloud-download-alt text-info") { }
        }
    }
}

fun RBuilder.oneclick(handler: OneClickProps.() -> Unit): ReactElement {
    return child(OneClick::class) {
        this.attrs(handler)
    }
}
