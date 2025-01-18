package io.beatmaps.maps.collab

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.CollaborationDetail
import io.beatmaps.api.CollaborationRemoveData
import io.beatmaps.api.CollaborationRequestData
import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface CollaboratorPickerProps : Props {
    var classes: String?
    var map: MapDetail
    var disabled: Boolean
}

val collaboratorPicker = fcmemo<CollaboratorPickerProps>("collaboratorPicker") { props ->
    val (collaborators, setCollaborators) = useState(listOf<CollaborationDetail>())

    fun updateCollaborators() {
        Axios.get<List<CollaborationDetail>>(
            "${Config.apibase}/collaborations/map/${props.map.id}",
            generateConfig<String, List<CollaborationDetail>>()
        ).then {
            setCollaborators(it.data)
        }
    }

    useEffect(props.map) {
        updateCollaborators()
    }

    div {
        className = ClassName("collaborators " + (props.classes ?: ""))
        h4 {
            +"Collaborators"
        }

        if (collaborators.isNotEmpty()) {
            div {
                className = ClassName("collaborator-cards")
                collaborators.forEach { c ->
                    if (c.collaborator == null) return@forEach

                    collaboratorCard {
                        user = c.collaborator
                        accepted = c.accepted
                        callback = {
                            Axios.post<String>(
                                "${Config.apibase}/collaborations/remove",
                                CollaborationRemoveData(c.mapId, c.collaborator.id),
                                generateConfig<CollaborationRemoveData, String>()
                            ).then {
                                setCollaborators(collaborators - c)
                            }
                        }
                    }
                }
            }
        }

        userSearch {
            excludeUsers = collaborators.mapNotNull { it.collaborator?.id }.plus(props.map.uploader.id)
            disabled = props.disabled
            callback = { newUser ->
                Axios.post<String>(
                    "${Config.apibase}/collaborations/request",
                    CollaborationRequestData(props.map.intId(), newUser.id),
                    generateConfig<CollaborationRequestData, String>()
                ).then {
                    updateCollaborators()
                }
            }
        }
    }
}
