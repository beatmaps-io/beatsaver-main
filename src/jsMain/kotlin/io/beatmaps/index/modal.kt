package io.beatmaps.index

import io.beatmaps.previewBaseUrl
import io.beatmaps.util.textToContent
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.ButtonType
import kotlinx.html.hidden
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement
import react.MutableRefObject
import react.Props
import react.RBuilder
import react.RefObject
import react.createContext
import react.dom.button
import react.dom.div
import react.dom.h5
import react.dom.iframe
import react.fc
import react.useRef
import react.useState
import kotlin.js.Promise

val modalContext = createContext<RefObject<ModalCallbacks>?>(null)

data class ModalData(val titleText: String, val bodyText: String = "", val buttons: List<ModalButton>, val large: Boolean = false, val bodyCallback: (RBuilder.(HTMLDivElement?) -> Unit)? = null)
data class ModalButton(val text: String, val color: String = "secondary", val callback: () -> Promise<Boolean> = { Promise.resolve(true) })
data class ModalCallbacks(
    val hide: () -> Unit,
    val show: (String) -> Unit,
    val showById: (String) -> Unit,
    val showDialog: (ModalData) -> Unit
)

external interface ModalProps : Props {
    var callbacks: MutableRefObject<ModalCallbacks>?
}

val modal = fc<ModalProps> { props ->
    val backdrop = useRef<HTMLDivElement>()
    val modalRef = useRef<HTMLDivElement>()
    val iframe = useRef<HTMLIFrameElement>()
    val (loading, setLoading) = useState(false)

    val (modalData, setModalData) = useState<ModalData?>(null)

    fun show() {
        modalRef.current?.let { md ->
            backdrop.current?.let { bd ->
                bd.hidden = false
                md.hidden = false
                window.setTimeout(
                    {
                        bd.addClass("show")
                        md.addClass("show")
                    },
                    10
                )
            }
        }
    }

    fun hide() {
        iframe.current?.src = "about:blank"
        modalRef.current?.let { md ->
            backdrop.current?.let { bd ->
                md.removeClass("show")
                bd.removeClass("show")
                window.setTimeout(
                    {
                        md.hidden = true
                        bd.hidden = true

                        setModalData(null)
                    },
                    200
                )
            }
        }
    }

    fun showIframe(url: String) {
        setModalData(null)

        iframe.current?.src = url
        show()
    }

    props.callbacks?.current = ModalCallbacks(
        ::hide,
        { hash ->
            showIframe("/maps/viewer/$hash")
        },
        { mapId ->
            showIframe("$previewBaseUrl?id=$mapId")
        },
        { modalLocal ->
            setModalData(modalLocal)
            show()
        }
    )

    div("modal-backdrop fade") {
        ref = backdrop
        attrs.hidden = true
    }
    div("modal") {
        ref = modalRef
        attrs.hidden = true
        attrs.onClickFunction = { hide() }
        div("modal-dialog modal-dialog-centered rabbit-dialog") {
            attrs.hidden = modalData != null
            iframe(classes = "modal-content") {
                ref = iframe
                attrs.src = "about:blank"
                attrs["allow"] = "fullscreen"
            }
        }
        div("modal-dialog" + if (modalData?.large == true) " modal-lg" else "") {
            attrs.hidden = modalData == null
            attrs.onClickFunction = {
                it.stopPropagation()
            }
            div("modal-content") {
                div("modal-header") {
                    h5("modal-title") {
                        +(modalData?.titleText ?: "")
                    }
                    button(type = ButtonType.button, classes = "btn-close") {
                        attrs.onClickFunction = { hide() }
                    }
                }
                div("modal-body") {
                    modalData?.let { m ->
                        m.bodyCallback?.invoke(this, modalRef.current) ?: textToContent(m.bodyText)
                    }
                }
                div("modal-footer") {
                    modalData?.buttons?.forEach { b ->
                        button(type = ButtonType.button, classes = "btn btn-${b.color}") {
                            attrs.disabled = loading
                            attrs.onClickFunction = {
                                setLoading(true)
                                b.callback().then({
                                    if (it) hide()
                                }) {
                                    console.log("MODAL CAUGHT ERROR???")
                                    console.error(it)
                                }.finally {
                                    setLoading(false)
                                }
                            }
                            +b.text
                        }
                    }
                }
            }
        }
    }
}
