package external

import org.w3c.dom.events.Event
import org.w3c.files.File
import react.ComponentClass
import react.ComponentModule
import react.Props
import react.ReactElement
import react.Ref

external interface DropzoneProps : Props {
    var onDrop: (Array<File>) -> Unit
    var multiple: Boolean
    var children: (DropInfo) -> ReactElement<*>?
}

external interface DropInfo : Props {
    var getRootProps: () -> DropRootProps
    var getInputProps: () -> DropInputProps
}

external interface DropRootProps : Props {
    var ref: Ref<*>
    var onKeyDown: (Event) -> Unit
    var onFocus: (Event) -> Unit
    var onBlur: (Event) -> Unit
    var onClick: (Event) -> Unit
    var onDragEnter: (Event) -> Unit
    var onDragOver: (Event) -> Unit
    var onDragLeave: (Event) -> Unit
    var onDrop: (Event) -> Unit
    var tabIndex: Int?
}

external interface DropInputProps : Props {
    var ref: Ref<*>
    var accept: String?
    var type: String
    var multiple: Boolean
    var onChange: (Event) -> Unit
    var onClick: (Event) -> Unit
    var autoComplete: String?
    var tabIndex: Int
}

@JsModule("react-dropzone")
@JsNonModule
external object Dropzone : ComponentModule<DropzoneProps> {
    override val default: ComponentClass<DropzoneProps>
}
