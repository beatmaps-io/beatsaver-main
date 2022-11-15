package io.beatmaps.modreview

import io.beatmaps.util.textToContent
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.RProps
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.textarea
import react.functionComponent
import react.useState
import kotlin.js.Promise

external interface EditableTextProps : RProps {
    var buttonText: String?
    var text: String?
    var renderText: Boolean?
    var editing: Boolean?
    var saveText: ((String) -> Promise<*>)?
    var stopEditing: (() -> Unit)?
}

val editableText = functionComponent<EditableTextProps> { props ->
    val textareaRef = createRef<HTMLTextAreaElement>()
    val (loading, setLoading) = useState(false)
    val (text, setText) = useState(null as String?)

    val displayText = (text ?: props.text ?: "")

    if (props.editing == true) {
        textarea("10", classes = "form-control m-2") {
            attrs.id = "review"
            attrs.disabled = loading
            +displayText
            ref = textareaRef
        }

        a(classes = "btn btn-primary m-1 float-end") {
            attrs.onClickFunction = {
                val newReview = textareaRef.current?.asDynamic().value as String

                setLoading(true)

                props.saveText?.invoke(newReview)?.then({
                    setLoading(false)
                    props.stopEditing?.invoke()
                    setText(newReview)
                }) {
                    setLoading(false)
                }
            }
            +(props.buttonText ?: "Save")
        }
    } else if (props.renderText == true) {
        div {
            textToContent(displayText)
        }
    } else {
        +displayText
    }
}
