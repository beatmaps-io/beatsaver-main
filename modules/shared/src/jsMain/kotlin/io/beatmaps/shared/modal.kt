package io.beatmaps.shared

import io.beatmaps.previewBaseUrl
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import react.ChildrenBuilder
import react.Props
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
import web.html.HTMLDivElement
import web.html.HTMLIFrameElement
import web.timers.setTimeout
import kotlin.js.Promise

val modalContext = createContext<RefObject<ModalCallbacks>?>(null)

data class ModalData(val titleText: String, val bodyText: String = "", val buttons: List<ModalButton>, val large: Boolean = false, val bodyCallback: (ChildrenBuilder.(HTMLDivElement?) -> Unit)? = null)
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
                setTimeout(
                    {
                        bd.classList.add("show")
                        md.classList.add("show")
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
                bd.classList.remove("show")
                md.classList.remove("show")
                setTimeout(
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
        className = ClassName("modal-backdrop fade")
        ref = backdrop
        hidden = true
    }
    div {
        className = ClassName("modal")
        ref = modalRef
        hidden = true
        onClick = { hide() }
        div {
            className = ClassName("modal-dialog modal-dialog-centered rabbit-dialog")
            hidden = modalData != null
            iframe {
                ref = iframe
                className = ClassName("modal-content")
                src = "about:blank"
                allow = "fullscreen"
            }
        }
        div {
            className = ClassName("modal-dialog" + if (modalData?.large == true) " modal-lg" else "")
            hidden = modalData == null
            onClick = {
                it.stopPropagation()
            }
            div {
                className = ClassName("modal-content")
                div {
                    className = ClassName("modal-header")
                    h5 {
                        className = ClassName("modal-title")
                        +(modalData?.titleText ?: "")
                    }
                    button {
                        type = ButtonType.button
                        className = ClassName("btn-close")
                        onClick = { hide() }
                    }
                }
                div {
                    className = ClassName("modal-body")
                    modalData?.let { m ->
                        m.bodyCallback?.invoke(this, modalRef.current) ?: textToContent(m.bodyText)
                    }
                }
                div {
                    className = ClassName("modal-footer")
                    modalData?.buttons?.forEach { b ->
                        button {
                            type = ButtonType.button
                            className = ClassName("btn btn-${b.color}")
                            disabled = loading
                            onClick = {
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
