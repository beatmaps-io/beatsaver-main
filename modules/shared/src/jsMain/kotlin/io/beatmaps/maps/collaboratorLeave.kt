package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.CollaborationRemoveData
import io.beatmaps.api.MapDetail
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalCallbacks
import io.beatmaps.index.ModalData
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.RefObject
import react.dom.a
import react.dom.i
import react.dom.p
import react.dom.span
import react.fc

external interface CollaboratorLeaveProps : Props {
    var map: MapDetail
    var collaboratorId: Int
    var reloadMap: () -> Unit
    var modal: RefObject<ModalCallbacks>?
}

val collaboratorLeave = fc<CollaboratorLeaveProps>("collaboratorLeave") { props ->
    a("#") {
        val title = "Leave collaboration"
        attrs.title = title
        attrs.attributes["aria-label"] = title
        attrs.onClickFunction = {
            it.preventDefault()

            props.modal?.current?.showDialog?.invoke(
                ModalData(
                    "Leave collaboration",
                    bodyCallback = {
                        p {
                            +"Are you sure you want to leave this collaboration? If you wish to rejoin later, the uploader will need to invite you again."
                        }
                    },
                    buttons = listOf(
                        ModalButton("Leave", "danger") {
                            Axios.post<String>(
                                "${Config.apibase}/collaborations/remove",
                                CollaborationRemoveData(props.map.intId(), props.collaboratorId),
                                generateConfig<CollaborationRemoveData, String>()
                            ).then {
                                props.reloadMap()
                                true
                            }.catch { false }
                        },
                        ModalButton("Cancel")
                    )
                )
            )
        }
        span("dd-text") { +title }
        i("fas fa-users-slash text-danger-light") { }
    }
}
