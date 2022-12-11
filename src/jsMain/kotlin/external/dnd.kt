package external

import io.beatmaps.playlist.onTransitionEndFunction
import kotlinext.js.asJsObject
import kotlinext.js.getOwnPropertyNames
import kotlinx.html.DIV
import kotlinx.html.js.onDragStartFunction
import org.w3c.dom.events.Event
import react.ComponentClass
import react.Props
import react.PropsWithChildren
import react.RBuilder
import react.RElementBuilder
import react.ReactElement
import react.RefObject
import react.createElement
import react.dom.RDOMBuilder
import react.dom.div
import react.dom.jsStyle

external interface DroppableProvided {
    var droppableProps: Any
    var innerRef: RefObject<*>
    var placeholder: ReactElement<*>
}

external interface DroppableProps : Props {
    var droppableId: String
    var children: (DroppableProvided) -> ReactElement<*>?
}

external interface DraggableProvidedProps {
    var style: dynamic
    var onTransitionEnd: (Event) -> Unit
}

external interface DragHandleProps {
    var onDragStart: (Event) -> Unit
}

external interface DraggableProvided {
    var innerRef: RefObject<*>
    var draggableProps: DraggableProvidedProps
    var dragHandleProps: DragHandleProps
}

external interface DraggableProps : Props {
    var draggableId: String
    var index: Int
    var children: (DraggableProvided) -> ReactElement<*>?
}

fun RBuilder.draggable(id: String, idx: Int, cb: RDOMBuilder<DIV>.() -> Unit) {
    DragAndDrop.Draggable {
        key = id
        attrs.draggableId = id
        attrs.index = idx
        draggableContainer {
            key = id
            cb()
        }
    }
}

fun RBuilder.droppable(id: String, cb: RDOMBuilder<DIV>.() -> Unit) {
    DragAndDrop.Droppable {
        attrs.droppableId = id
        droppableContainer {
            cb()
        }
    }
}

@Suppress("USELESS_CAST")
fun RDOMBuilder<*>.copyProps(obj: Any) {
    val jsObj = obj.asJsObject()
    jsObj.getOwnPropertyNames().forEach { key ->
        when (val it = jsObj.asDynamic()[key]) {
            is String -> attrs.attributes[key] = it as String
            is Int -> attrs.attributes[key] = (it as Int).toString()
            is Boolean -> attrs.attributes[key] = (it as Boolean).toString()
        }
    }
}

fun RElementBuilder<DraggableProps>.draggableContainer(cb: RDOMBuilder<DIV>.() -> Unit) {
    attrs.children = { dragProvided ->
        createElement<Props> {
            div {
                ref = dragProvided.innerRef

                attrs.onDragStartFunction = dragProvided.dragHandleProps.onDragStart
                attrs.onTransitionEndFunction = dragProvided.draggableProps.onTransitionEnd

                attrs.jsStyle = dragProvided.draggableProps.style

                copyProps(dragProvided.draggableProps)
                copyProps(dragProvided.dragHandleProps)

                cb()
            }
        }
    }
}

fun RElementBuilder<DroppableProps>.droppableContainer(cb: RDOMBuilder<DIV>.() -> Unit) {
    attrs.children = { provided ->
        createElement<Props> {
            div {
                ref = provided.innerRef

                copyProps(provided.droppableProps)

                cb()

                child(provided.placeholder)
            }
        }
    }
}

external enum class DropReason {
    DROP, CANCEL
}

external enum class MovementMode {
    FLUID, SNAP
}

external interface Combine {
    var draggableId: String
    var droppableId: String
}

external interface DraggableLocation {
    var droppableId: String
    var index: Int
}

external interface DragEndEvent {
    var reason: DropReason
    var combine: Combine?
    var destination: DraggableLocation?
    var type: String
    var mode: MovementMode
    var source: DraggableLocation
    var draggableId: String
}

external interface DragDropContextProps : PropsWithChildren {
    var onDragEnd: (DragEndEvent) -> Unit
}

@JsModule("react-beautiful-dnd")
@JsNonModule
external object DragAndDrop {
    val DragDropContext: ComponentClass<DragDropContextProps>
    val Droppable: ComponentClass<DroppableProps>
    val Draggable: ComponentClass<DraggableProps>
}
