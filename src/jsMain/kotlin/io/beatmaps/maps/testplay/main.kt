package io.beatmaps.maps.testplay

import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.maps.infoTable
import io.beatmaps.maps.mapInfo
import react.Props
import react.fc
import react.ref
import react.useRef

external interface TestplayProps : Props {
    var mapInfo: MapDetail
    var refreshPage: () -> Unit
    var history: History
    var updateMapinfo: (MapDetail) -> Unit
}

val testplay = fc<TestplayProps> { props ->
    val modalRef = useRef<ModalComponent>()

    modal {
        ref = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        mapInfo {
            attrs {
                mapInfo = props.mapInfo
                reloadMap = props.refreshPage
                deleteMap = {
                    props.history.push("/profile")
                }
                updateMapinfo = props.updateMapinfo
            }
        }
        infoTable {
            map = props.mapInfo
            horizontal = true
            selected = null
            changeSelectedDiff = { }
        }
        timeline {
            attrs {
                mapInfo = props.mapInfo
                reloadMap = props.refreshPage
                history = props.history
            }
        }
    }
}
