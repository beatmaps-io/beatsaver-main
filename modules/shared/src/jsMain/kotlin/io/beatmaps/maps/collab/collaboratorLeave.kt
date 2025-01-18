package io.beatmaps.maps.collab

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.CollaborationRemoveData
import io.beatmaps.api.MapDetail
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.ModalData
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface CollaboratorLeaveProps : Props {
    var map: MapDetail
    var collaboratorId: Int
    var reloadMap: () -> Unit
    var modal: RefObject<ModalCallbacks>?
}

val collaboratorLeave = fcmemo<CollaboratorLeaveProps>("collaboratorLeave") { props ->
    a {
        href = "#"

        val title = "Leave collaboration"
        this.title = title
        ariaLabel = title
        onClick = {
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
        span {
            className = ClassName("dd-text")
            +title
        }
        i {
            className = ClassName("fas fa-users-slash text-danger-light")
        }
    }
}
