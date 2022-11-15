package io.beatmaps.modreview

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.util.textToContent
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.textarea
import react.setState
import kotlin.js.Promise

external interface EditableTextProps : RProps {
    var buttonText: String?
    var text: String?
    var renderText: Boolean?
    var editing: Boolean?
    var saveText: ((String) -> Promise<AxiosResponse<ActionResponse>>)?
    var stopEditing: ((String) -> Unit)?
}

external interface EditableTextState : RState {
    var loading: Boolean?
}

class EditableText : RComponent<EditableTextProps, EditableTextState>() {
    private val textareaRef = createRef<HTMLTextAreaElement>()

    private fun endLoading(e: Throwable) {
        setState {
            loading = false
        }
    }

    override fun RBuilder.render() {
        val displayText = (props.text ?: "")

        if (props.editing == true) {
            textarea("10", classes = "form-control m-2") {
                attrs.id = "review"
                attrs.disabled = state.loading == true
                +displayText
                ref = textareaRef
            }

            a(classes = "btn btn-primary m-1 float-end") {
                attrs.onClickFunction = {
                    val newReview = textareaRef.current?.asDynamic().value as String

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
                    }, ::endLoading)
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

fun RBuilder.editableText(handler: EditableTextProps.() -> Unit): ReactElement {
    return child(EditableText::class) {
        this.attrs(handler)
    }
}
