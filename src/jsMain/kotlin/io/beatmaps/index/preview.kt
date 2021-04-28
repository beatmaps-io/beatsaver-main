package io.beatmaps.index

import io.beatmaps.common.Config
import io.beatmaps.maps.textToContent
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.hidden
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.*
import react.setState

const val previewBaseUrl = "https://skystudioapps.com/bs-viewer/"

data class ModalState(var modal: ModalData? = null) : RState
data class ModalData(val titleText: String, val bodyText: String = "", val buttons: List<ModalButton>, val large: Boolean = false, val bodyCallback: (RDOMBuilder<DIV>.() -> Unit)? = null)
data class ModalButton(val text: String, val color: String = "secondary",  val callback: () -> Unit = {})

@JsExport
class ModalComponent : RComponent<RProps, ModalState>() {
    private val backdrop = createRef<HTMLDivElement>()
    private val modal = createRef<HTMLDivElement>()
    private val iframe = createRef<HTMLIFrameElement>()

    fun show(hash: String) {
        setState {
            modal = null
        }

        iframe.current?.src = "$previewBaseUrl?noProxy=true&url=${Config.cdnbase}/${hash}.zip"
        show()
    }

    fun showDialog(modalLocal: ModalData) {
        setState {
            modal = modalLocal
        }
        show()
    }

    private fun show() {
        modal.current?.let { md ->
            backdrop.current?.let { bd ->
                bd.hidden = false
                md.hidden = false
                window.setTimeout({
                    bd.addClass("show")
                    md.addClass("show")
                }, 10)
            }
        }
    }

    private fun hide() {
        iframe.current?.src = "about:blank"
        modal.current?.let { md ->
            backdrop.current?.let { bd ->
                md.removeClass("show")
                bd.removeClass("show")
                window.setTimeout({
                    md.hidden = true
                    bd.hidden = true
                }, 200)
            }
        }
    }

    override fun RBuilder.render() {
        div("modal-backdrop fade") {
            ref = backdrop
            attrs.hidden = true
        }
        div("modal") {
            ref = modal
            attrs.hidden = true
            attrs.onClickFunction = { hide() }
            div("modal-dialog modal-dialog-centered rabbit-dialog") {
                attrs.hidden = state.modal != null
                iframe(classes = "modal-content") {
                    ref = iframe
                    attrs.src = "about:blank"
                }
            }
            div("modal-dialog" + if (state.modal?.large == true) " modal-lg" else "") {
                attrs.hidden = state.modal == null
                attrs.onClickFunction = {
                    it.stopPropagation()
                }
                div("modal-content") {
                    div("modal-header") {
                        h5("modal-title") {
                            +(state.modal?.titleText ?: "")
                        }
                        button(type = ButtonType.button, classes = "close") {
                            attrs.onClickFunction = { hide() }
                            span {
                                +"×"
                            }
                        }
                    }
                    div("modal-body") {
                        state.modal?.let { m ->
                            m.bodyCallback?.invoke(this) ?: textToContent(m.bodyText)
                        }
                    }
                    div("modal-footer") {
                        state.modal?.buttons?.forEach { b ->
                            button(type = ButtonType.button, classes = "btn btn-${b.color}") {
                                attrs.onClickFunction = {
                                    b.callback()
                                    hide()
                                }
                                +b.text
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.modal(handler: RProps.() -> Unit): ReactElement {
    return child(ModalComponent::class) {
        this.attrs(handler)
    }
}