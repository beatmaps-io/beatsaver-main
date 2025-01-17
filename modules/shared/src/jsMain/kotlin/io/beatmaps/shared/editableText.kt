package io.beatmaps.shared

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.util.setData
import io.beatmaps.util.textToContent
import js.objects.jso
import org.w3c.dom.HTMLTextAreaElement
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.fc
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.Flex
import web.cssom.None
import web.html.ButtonType
import kotlin.js.Promise

external interface EditableTextProps : PropsWithChildren {
    var buttonText: String?
    var text: String?
    var renderText: Boolean?
    var editing: Boolean?
    var saveText: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var onError: ((List<String>) -> Unit)?
    var stopEditing: ((String) -> Unit)?
    var maxLength: Int?
    var rows: Int?
    var btnClass: String?
    var flex: Flex?
    var placeholder: String?
    var textClass: ClassName?
}

val editableText = fc<EditableTextProps>("editableText") { props ->
    val (loading, setLoading) = useState(false)
    val (textLength, setTextLength) = useState(props.text?.length ?: 0)

    val textareaRef = useRef<HTMLTextAreaElement>()

    val displayText = (props.text ?: "")

    if (props.editing == true) {
        div {
            attrs.className = props.textClass
            textarea {
                attrs.rows = props.rows ?: 10
                attrs.className = ClassName("form-control")
                attrs.id = "review"
                attrs.disabled = loading == true
                attrs.placeholder = props.placeholder ?: ""
                attrs.defaultValue = displayText
                ref = textareaRef
                attrs.maxLength = props.maxLength
                attrs.onChange = {
                    setTextLength(it.target.value.length)
                }
            }
            props.maxLength?.let {
                span {
                    attrs.className = ClassName("badge badge-" + if (textLength > it - 20) "danger" else "dark")
                    attrs.id = "count_message"
                    +"$textLength / $it"
                }
            }
        }

        div {
            attrs.className = ClassName("d-flex flex-row-reverse")
            button {
                attrs.className = ClassName("text-nowrap btn " + (props.btnClass ?: "btn-primary mt-1"))
                attrs.type = ButtonType.submit
                attrs.disabled = loading || textLength < 1 || props.maxLength?.let { textLength > it } ?: false
                attrs.setData("loading", loading)

                attrs.style = jso {
                    flex = props.flex ?: None.none
                }
                attrs.onClick = {
                    val newReview = textareaRef.current?.value ?: ""
                    if (!loading) {
                        setLoading(true)

                        val promise = props.saveText?.invoke(newReview) ?: Promise.reject(IllegalStateException("Captcha not present"))
                        promise.then({
                            setLoading(false)

                            if (it.data.success) {
                                textareaRef.current?.value = ""
                                setTextLength(0)
                                props.stopEditing?.invoke(newReview)
                            } else {
                                props.onError?.invoke(it.data.errors)
                            }
                        }, {
                            props.onError?.invoke(listOfNotNull(it.message))
                            setLoading(false)
                        })
                    }
                }
                +(props.buttonText ?: "Save")
            }

            props.children()
        }
    } else if (props.renderText == true) {
        div {
            textToContent(displayText)
        }
    } else {
        +displayText
    }
}
