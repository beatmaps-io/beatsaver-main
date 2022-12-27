package external

import io.beatmaps.upload.DropInfo
import org.w3c.files.File
import react.ComponentClass
import react.Props
import react.ReactElement

external interface DropzoneProps : Props {
    var onDrop: (Array<File>) -> Unit
    var multiple: Boolean
    var children: (DropInfo) -> ReactElement<*>?
}

@JsModule("react-dropzone")
@JsNonModule
external object Dropzone {
    val default: ComponentClass<DropzoneProps>
}
