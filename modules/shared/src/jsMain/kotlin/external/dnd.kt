package external

import io.beatmaps.shared.search.ExtraContentRenderer
import io.beatmaps.shared.search.invokeECR
import io.beatmaps.util.fcmemo
import js.import.importAsync
import js.objects.Object
import js.objects.jso
import react.ChildrenBuilder
import react.ComponentType
import react.ExoticComponent
import react.Props
import react.PropsWithChildren
import react.ReactElement
import react.Ref
import react.createElement
import react.dom.events.DragEventHandler
import react.dom.events.TransitionEventHandler
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import web.html.HTMLDivElement

external interface DroppableProvided {
    var droppableProps: Any
    var innerRef: Ref<HTMLDivElement>
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
    var innerRef: Ref<HTMLDivElement>
    var draggableProps: DraggableProvidedProps
    var dragHandleProps: DragHandleProps
}

external interface DraggableProps : Props {
    var draggableId: String
    var index: Int
    var children: (DraggableProvided) -> ReactElement<*>?
}

fun ChildrenBuilder.draggable(id: String, idx: Int, cb: ExtraContentRenderer) {
    dndExotics.draggable {
        key = id
        draggableId = id
        index = idx
        children = { dragProvided ->
            createElement(
                dragChild,
                jso {
                    this.dragProvided = dragProvided
                    callback = cb
                }
            )
        }
    }
}

external interface DragChildProps : Props {
    var dragProvided: DraggableProvided
    var callback: ExtraContentRenderer
}

val dragChild = fcmemo<DragChildProps>("DragChild") { props ->
    div {
        ref = props.dragProvided.innerRef
        key = id

        onDragStart = props.dragProvided.dragHandleProps.onDragStart
        onTransitionEnd = props.dragProvided.draggableProps.onTransitionEnd

        style = props.dragProvided.draggableProps.style

        copyProps(props.dragProvided.draggableProps)
        copyProps(props.dragProvided.dragHandleProps)

        invokeECR(props.callback)
    }
}

fun ChildrenBuilder.droppable(id: String, cb: ExtraContentRenderer) {
    dndExotics.droppable {
        droppableId = id
        isDropDisabled = false
        isCombineEnabled = false
        ignoreContainerClipping = false
        direction = "vertical"
        children = { provided ->
            createElement(
                dropChild,
                jso {
                    this.provided = provided
                    callback = cb
                }
            )
        }
    }
}

external interface DropChildProps : Props {
    var provided: DroppableProvided
    var callback: ExtraContentRenderer
}

val dropChild = fcmemo<DropChildProps>("DropChild") { props ->
    div {
        ref = props.provided.innerRef

        copyProps(props.provided.droppableProps)

        invokeECR(props.callback)

        +props.provided.placeholder
    }
}

@Suppress("USELESS_CAST")
fun HTMLAttributes<*>.copyProps(obj: Any) {
    val dynAttrs = asDynamic()
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
    val DragDropContext: ComponentType<DragDropContextProps>
    val Droppable: ComponentType<DroppableProps>
    val Draggable: ComponentType<DraggableProps>
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
