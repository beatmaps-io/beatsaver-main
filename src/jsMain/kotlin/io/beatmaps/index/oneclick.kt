package io.beatmaps.index
import io.beatmaps.common.Config
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.get
import org.w3c.dom.set
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.*

external interface OneClickProps : RProps {
    var mapId: Int
    var modal: RReadableRef<ModalComponent>
}

@JsExport
class OneClick : RComponent<OneClickProps, RState>() {
    override fun RBuilder.render() {
        a("bmio://${props.mapId}") {
            attrs.title = "One-Click"
            attrs.attributes["aria-label"] = "One-Click"
            attrs.onClickFunction = {
                if (localStorage["oneclick"] == null) {
                    it.preventDefault()

                    props.modal.current?.showDialog(ModalData(
                        "BeatMaps.io One-Click installer required for this to work",
                        bodyCallback =  {
                            +"Don't have the installer? "
                            a("${Config.basename}/static/BeatMapsioInstaller.exe") {
                                +"Get it now"
                            }
                        },
                        buttons = listOf(ModalButton("Don't show again", "primary") {
                            localStorage["oneclick"] = "true"
                            window.location.href = "bmio://${props.mapId}"
                        }, ModalButton("Continue") {
                            window.location.href = "bmio://${props.mapId}"
                        })
                    ))
                }
            }
            i("fas fa-cloud-download-alt text-info") { }
        }
    }
}

fun RBuilder.oneclick(handler: OneClickProps.() -> Unit): ReactElement {
    return child(OneClick::class) {
        this.attrs(handler)
    }
}