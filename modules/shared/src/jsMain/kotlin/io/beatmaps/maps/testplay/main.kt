package io.beatmaps.maps.testplay

import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.maps.infoTable
import io.beatmaps.maps.mapInfo
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import react.Props
import react.fc
import react.useRef

external interface TestplayProps : Props {
    var mapInfo: MapDetail
    var refreshPage: () -> Unit
    var history: History
    var updateMapinfo: (MapDetail) -> Unit
}

val testplay = fc<TestplayProps>("testplay") { props ->
    val modalRef = useRef<ModalCallbacks>()

    modal {
        attrs.callbacks = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        mapInfo {
            attrs {
                mapInfo = props.mapInfo
                reloadMap = props.refreshPage
                updateMapinfo = props.updateMapinfo
            }
        }
        infoTable {
            attrs.map = props.mapInfo
            attrs.horizontal = true
            attrs.selected = null
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
