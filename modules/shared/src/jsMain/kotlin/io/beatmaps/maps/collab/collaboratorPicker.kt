package io.beatmaps.maps.collab

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.CollaborationDetail
import io.beatmaps.api.CollaborationRemoveData
import io.beatmaps.api.CollaborationRequestData
import io.beatmaps.api.MapDetail
import react.Props
import react.dom.div
import react.dom.h4
import react.fc
import react.useEffect
import react.useState

external interface CollaboratorPickerProps : Props {
    var classes: String?
    var map: MapDetail
    var disabled: Boolean
}

val collaboratorPicker = fc<CollaboratorPickerProps>("collaboratorPicker") { props ->
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

    div("collaborators " + (props.classes ?: "")) {
        h4 {
            +"Collaborators"
        }

        if (collaborators.isNotEmpty()) {
            div("collaborator-cards") {
                collaborators.forEach { c ->
                    if (c.collaborator == null) return@forEach

                    collaboratorCard {
                        attrs.user = c.collaborator
                        attrs.accepted = c.accepted
                        attrs.callback = {
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
            attrs.excludeUsers = collaborators.mapNotNull { it.collaborator?.id }.plus(props.map.uploader.id)
            attrs.disabled = props.disabled
            attrs.callback = { newUser ->
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
