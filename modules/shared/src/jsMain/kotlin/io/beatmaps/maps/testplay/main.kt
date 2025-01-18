package io.beatmaps.maps.testplay

import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.maps.infoTable
import io.beatmaps.maps.mapInfo
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.useRef

external interface TestplayProps : Props {
    var mapInfo: MapDetail
    var refreshPage: () -> Unit
    var history: History
    var updateMapinfo: (MapDetail) -> Unit
}

val testplay = fcmemo<TestplayProps>("testplay") { props ->
    val modalRef = useRef<ModalCallbacks>()

    modal {
        callbacks = modalRef
    }

    modalContext.Provider {
        value = modalRef

        mapInfo {
            mapInfo = props.mapInfo
            reloadMap = props.refreshPage
            updateMapinfo = props.updateMapinfo
        }
        infoTable {
            map = props.mapInfo
            horizontal = true
            selected = null
        }
        timeline {
            mapInfo = props.mapInfo
            reloadMap = props.refreshPage
            history = props.history
        }
    }
}
