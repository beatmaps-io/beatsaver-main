package io.beatmaps.playlist

import external.component
import js.import.import
import react.ComponentClass
import react.ExoticComponent
import react.Props

external interface PlaylistsModule {
    val edit: ComponentClass<Props>
    val multiAdd: ComponentClass<Props>
    val feed: ComponentClass<Props>
    val page: ComponentClass<Props>
    val addTo: ComponentClass<AddToPlaylistProps>
    val table: ComponentClass<PlaylistTableProps>
}

data class PlaylistsExotics(
    val edit: ExoticComponent<Props>,
    val multiAdd: ExoticComponent<Props>,
    val feed: ExoticComponent<Props>,
    val page: ExoticComponent<Props>,
    val addTo: ExoticComponent<AddToPlaylistProps>,
    val table: ExoticComponent<PlaylistTableProps>
)

val playlists = import<PlaylistsModule>("./BeatMaps-playlists").let { promise ->
    PlaylistsExotics(
        promise.component { it.edit },
        promise.component { it.multiAdd },
        promise.component { it.feed },
        promise.component { it.page },
        promise.component { it.addTo },
        promise.component { it.table }
    )
}