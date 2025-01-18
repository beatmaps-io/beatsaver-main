package external

import react.ComponentModule
import react.ComponentType
import react.Props
import react.ReactElement
import react.Ref
import react.dom.events.ChangeEventHandler
import react.dom.events.DragEventHandler
import react.dom.events.FocusEventHandler
import react.dom.events.KeyboardEventHandler
import react.dom.events.MouseEventHandler
import web.autofill.AutoFill
import web.file.File
import web.html.HTMLElement
import web.html.InputType

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
    var ref: Ref<HTMLElement>
    var onKeyDown: KeyboardEventHandler<*>
    var onFocus: FocusEventHandler<*>
    var onBlur: FocusEventHandler<*>
    var onClick: MouseEventHandler<*>
    var onDragEnter: DragEventHandler<*>
    var onDragOver: DragEventHandler<*>
    var onDragLeave: DragEventHandler<*>
    var onDrop: DragEventHandler<*>
    var tabIndex: Int?
}

external interface DropInputProps : Props {
    var ref: Ref<HTMLElement>
    var accept: String?
    var type: InputType
    var multiple: Boolean
    var onChange: ChangeEventHandler<*>
    var onClick: MouseEventHandler<*>
    var autoComplete: AutoFill
    var tabIndex: Int
}

@JsModule("react-dropzone")
@JsNonModule
external object Dropzone : ComponentModule<DropzoneProps> {
    override val default: ComponentType<DropzoneProps>
}
