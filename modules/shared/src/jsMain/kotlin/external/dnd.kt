package external

import js.import.importAsync
import js.objects.Object
import react.ComponentClass
import react.ExoticComponent
import react.Props
import react.PropsWithChildren
import react.RBuilder
import react.RElementBuilder
import react.ReactElement
import react.RefObject
import react.createElement
import react.dom.events.DragEventHandler
import react.dom.events.TransitionEventHandler
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import web.html.HTMLDivElement

external interface DroppableProvided {
    var droppableProps: Any
    var innerRef: RefObject<*>
    var placeholder: ReactElement<*>
}

external interface DroppableProps : Props {
    var droppableId: String
    var direction: String
    var isCombineEnabled: Boolean
    var isDropDisabled: Boolean
    var ignoreContainerClipping: Boolean
    var mode: String
    var type: String
    var children: (DroppableProvided) -> ReactElement<*>?
}

external interface DraggableProvidedProps {
    var style: dynamic
    var onTransitionEnd: TransitionEventHandler<*>
}

external interface DragHandleProps {
    var onDragStart: DragEventHandler<*>
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

fun RBuilder.draggable(id: String, idx: Int, cb: RElementBuilder<HTMLAttributes<HTMLDivElement>>.() -> Unit) {
    dndExotics.draggable {
        key = id
        attrs.draggableId = id
        attrs.index = idx
        attrs.children = { dragProvided ->
            createElement<Props> {
                div {
                    ref = dragProvided.innerRef
                    key = id

                    attrs.onDragStart = dragProvided.dragHandleProps.onDragStart
                    attrs.onTransitionEnd = dragProvided.draggableProps.onTransitionEnd

                    attrs.style = dragProvided.draggableProps.style

                    copyProps(dragProvided.draggableProps)
                    copyProps(dragProvided.dragHandleProps)

                    cb()
                }
            }
        }
    }
}

fun RBuilder.droppable(id: String, cb: RElementBuilder<HTMLAttributes<HTMLDivElement>>.() -> Unit) {
    dndExotics.droppable {
        attrs.droppableId = id
        attrs.isDropDisabled = false
        attrs.isCombineEnabled = false
        attrs.ignoreContainerClipping = false
        attrs.direction = "vertical"
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
}

@Suppress("USELESS_CAST")
fun RElementBuilder<HTMLAttributes<*>>.copyProps(obj: Any) {
    val dynAttrs = attrs.asDynamic()
    Object.getOwnPropertyNames(obj).forEach { key ->
        when (val it = obj.asDynamic()[key]) {
            is String -> dynAttrs[key] = it as String
            is Int -> dynAttrs[key] = (it as Int).toString()
            is Boolean -> dynAttrs[key] = (it as Boolean).toString()
        }
    }
}

sealed external class DropReason {
    object DROP : DropReason
    object CANCEL : DropReason
}

sealed external class MovementMode {
    object FLUID : DropReason
    object SNAP : DropReason
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

external interface DragAndDrop {
    val DragDropContext: ComponentClass<DragDropContextProps>
    val Droppable: ComponentClass<DroppableProps>
    val Draggable: ComponentClass<DraggableProps>
}

data class DragAndDropExotics(
    val dragDropContext: ExoticComponent<DragDropContextProps>,
    val droppable: ExoticComponent<DroppableProps>,
    val draggable: ExoticComponent<DraggableProps>
)

val dndExotics = importAsync<DragAndDrop>("react-beautiful-dnd").let { mainPromise ->
    DragAndDropExotics(
        mainPromise.component { it.DragDropContext },
        mainPromise.component { it.Droppable },
        mainPromise.component { it.Draggable }
    )
}
