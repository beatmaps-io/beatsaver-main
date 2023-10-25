package io.beatmaps.modreview

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.util.textToContent
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.span
import react.dom.textarea
import react.setState
import kotlin.js.Promise

external interface EditableTextProps : Props {
    var buttonText: String?
    var text: String?
    var renderText: Boolean?
    var editing: Boolean?
    var saveText: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var stopEditing: ((String) -> Unit)?
    var maxLength: Int?
}

external interface EditableTextState : State {
    var loading: Boolean?
    var textLength: Int?
}

class EditableText : RComponent<EditableTextProps, EditableTextState>() {
    private val textareaRef = createRef<HTMLTextAreaElement>()

    override fun componentWillMount() {
        setState {
            textLength = props.text?.length
        }
    }

    private val endLoading = { _: Throwable ->
        setState {
            loading = false
        }
    }

    override fun RBuilder.render() {
        val displayText = (props.text ?: "")

        if (props.editing == true) {
            textarea("10", classes = "form-control mt-2") {
                attrs.id = "review"
                attrs.disabled = state.loading == true
                +displayText
                ref = textareaRef
                props.maxLength?.let { max ->
                    attrs.maxLength = "$max"
                }
                attrs.onChangeFunction = {
                    setState {
                        textLength = (it.target as HTMLTextAreaElement).value.length
                    }
                }
            }
            props.maxLength?.let {
                val currentLength = state.textLength ?: 0
                span("badge badge-" + if (currentLength > it - 20) "danger" else "dark") {
                    attrs.id = "count_message"
                    +"$currentLength / $it"
                }
            }

            a(classes = "btn btn-primary mt-1 float-end") {
                attrs.onClickFunction = {
                    val newReview = textareaRef.current?.value ?: ""

                    setState {
                        loading = true
                    }

                    props.saveText?.invoke(newReview)?.then({
                        setState {
                            loading = false
                        }

                        if (it.data.success) {
                            props.stopEditing?.invoke(newReview)
                        }
                    }, endLoading)
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
}

fun RBuilder.editableText(handler: EditableTextProps.() -> Unit) =
    child(EditableText::class) {
        this.attrs(handler)
    }
