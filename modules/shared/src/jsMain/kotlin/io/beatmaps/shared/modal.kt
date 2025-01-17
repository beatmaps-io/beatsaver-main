package io.beatmaps.shared

import io.beatmaps.previewBaseUrl
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement
import react.Props
import react.RBuilder
import react.RefObject
import react.createContext
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.iframe
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
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
    var callbacks: RefObject<ModalCallbacks>?
}

val modal = fcmemo<ModalProps>("Modal") { props ->
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

    div {
        attrs.className = ClassName("modal-backdrop fade")
        ref = backdrop
        attrs.hidden = true
    }
    div {
        attrs.className = ClassName("modal")
        ref = modalRef
        attrs.hidden = true
        attrs.onClick = { hide() }
        div {
            attrs.className = ClassName("modal-dialog modal-dialog-centered rabbit-dialog")
            attrs.hidden = modalData != null
            iframe {
                ref = iframe
                attrs.className = ClassName("modal-content")
                attrs.src = "about:blank"
                attrs.allow = "fullscreen"
            }
        }
        div {
            attrs.className = ClassName("modal-dialog" + if (modalData?.large == true) " modal-lg" else "")
            attrs.hidden = modalData == null
            attrs.onClick = {
                it.stopPropagation()
            }
            div {
                attrs.className = ClassName("modal-content")
                div {
                    attrs.className = ClassName("modal-header")
                    h5 {
                        attrs.className = ClassName("modal-title")
                        +(modalData?.titleText ?: "")
                    }
                    button {
                        attrs.type = ButtonType.button
                        attrs.className = ClassName("btn-close")
                        attrs.onClick = { hide() }
                    }
                }
                div {
                    attrs.className = ClassName("modal-body")
                    modalData?.let { m ->
                        m.bodyCallback?.invoke(this, modalRef.current) ?: textToContent(m.bodyText)
                    }
                }
                div {
                    attrs.className = ClassName("modal-footer")
                    modalData?.buttons?.forEach { b ->
                        button {
                            attrs.type = ButtonType.button
                            attrs.className = ClassName("btn btn-${b.color}")
                            attrs.disabled = loading
                            attrs.onClick = {
                                setLoading(true)
                                b.callback().then({
                                    if (it) hide()
                                }) {
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
