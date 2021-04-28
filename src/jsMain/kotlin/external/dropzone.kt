package external

import org.w3c.files.File
import react.RClass
import react.RProps

external interface DropzoneProps: RProps {
    var onDrop : (Array<File>) -> Unit
    var multiple: Boolean
}

@JsModule("react-dropzone")
@JsNonModule
external object Dropzone {
    val default: RClass<DropzoneProps>
}
