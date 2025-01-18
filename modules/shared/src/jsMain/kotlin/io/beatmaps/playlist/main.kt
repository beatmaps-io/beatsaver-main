package io.beatmaps.playlist

import external.component
import js.import.importAsync
import react.ComponentType
import react.ExoticComponent
import react.Props

external interface PlaylistsModule {
    val edit: ComponentType<Props>
    val multiAdd: ComponentType<Props>
    val feed: ComponentType<Props>
    val page: ComponentType<Props>
    val addTo: ComponentType<AddToPlaylistProps>
    val table: ComponentType<PlaylistTableProps>
}

data class PlaylistsExotics(
    val edit: ExoticComponent<Props>,
    val multiAdd: ExoticComponent<Props>,
    val feed: ExoticComponent<Props>,
    val page: ExoticComponent<Props>,
    val addTo: ExoticComponent<AddToPlaylistProps>,
    val table: ExoticComponent<PlaylistTableProps>
)

val playlists = importAsync<PlaylistsModule>("./BeatMaps-playlists").let { promise ->
    PlaylistsExotics(
        promise.component { it.edit },
        promise.component { it.multiAdd },
        promise.component { it.feed },
        promise.component { it.page },
        promise.component { it.addTo },
        promise.component { it.table }
    )
}
