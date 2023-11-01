package io.beatmaps.modreview

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.util.textToContent
import kotlinx.html.ButtonType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.jsStyle
import react.dom.span
import react.dom.textarea
import react.fc
import react.useRef
import react.useState
import kotlin.js.Promise

external interface EditableTextProps : Props {
    var buttonText: String?
    var text: String?
    var renderText: Boolean?
    var editing: Boolean?
    var saveText: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var stopEditing: ((String) -> Unit)?
    var maxLength: Int?
    var rows: Int?
    var btnClass: String?
    var justify: String?
}

val editableText = fc<EditableTextProps> { props ->
    val (loading, setLoading) = useState(false)
    val (textLength, setTextLength) = useState(props.text?.length ?: 0)

    val textareaRef = useRef<HTMLTextAreaElement>()

    val displayText = (props.text ?: "")

    if (props.editing == true) {
        textarea((props.rows ?: 10).toString(), classes = "form-control mt-2") {
            attrs.id = "review"
            attrs.disabled = loading == true
            +displayText
            ref = textareaRef
            props.maxLength?.let { max ->
                attrs.maxLength = "$max"
            }
            attrs.onChangeFunction = {
                setTextLength((it.target as HTMLTextAreaElement).value.length)
            }
        }
        props.maxLength?.let {
            span("badge badge-" + if (textLength > it - 20) "danger" else "dark") {
                attrs.id = "count_message"
                +"$textLength / $it"
            }
        }

        div("d-grid") {
            button(classes = "btn " + (props.btnClass ?: "btn-primary mt-1"), type = ButtonType.submit) {
                attrs.jsStyle {
                    justifySelf = props.justify ?: "end"
                }
                attrs.onClickFunction = {
                    val newReview = textareaRef.current?.value ?: ""
                    if (!loading) {
                        setLoading(true)

                        props.saveText?.invoke(newReview)?.then({
                            setLoading(false)

                            if (it.data.success) {
                                props.stopEditing?.invoke(newReview)
                            }
                        }, {
                            setLoading(false)
                        })
                    }
                }
                +(props.buttonText ?: "Save")
            }
        }
    } else if (props.renderText == true) {
        div {
            textToContent(displayText)
        }
    } else {
        +displayText
    }
}
