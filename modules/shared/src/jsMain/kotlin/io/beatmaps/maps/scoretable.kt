package io.beatmaps.maps

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.invoke
import io.beatmaps.Config
import io.beatmaps.api.LeaderboardData
import io.beatmaps.api.LeaderboardScore
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.fixedStr
import js.objects.jso
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.fc
import react.useEffectOnceWithCleanup
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.window.WindowTarget

external interface ScoreTableProps : Props {
    var mapKey: String
    var selected: MapDifficulty?
    var type: LeaderboardType
}

external interface ScoreTableRef {
    var loading: Boolean
    var page: Int
    var scroll: Double
    var token: CancelTokenSource
}

fun RefObject<ScoreTableRef>.update(block: ScoreTableRef.() -> Unit) = current?.let {
    block(it)
}

val scoreTable = fc<ScoreTableProps>("scoreTable") { props ->
    val (uid, setUid) = useState<String?>(null)
    val state = useRef<ScoreTableRef>(jso())
    val (scores, setScores) = useState(listOf<LeaderboardScore>())

    val myRef = useRef<HTMLElement>()
    val scoresRef = useRef(listOf<LeaderboardScore>())

    fun loadNextPage() {
        if (state.current?.loading != true) {
            state.update {
                loading = true
            }

            Axios.get<LeaderboardData>(
                "${Config.apibase}/scores/${props.mapKey}/${state.current?.page}?difficulty=${props.selected?.difficulty?.idx ?: 9}" +
                    "&gameMode=${props.selected?.characteristic?.ordinal ?: 0}&type=${props.type}",
                generateConfig<String, LeaderboardData>(state.current?.token?.token)
            ).then { res ->
                val newScores = res.data

                if (newScores.scores.isNotEmpty()) {
                    state.update {
                        loading = false
                        page += 1
                    }

                    val newScoreList = (scoresRef.current ?: listOf()).plus(newScores.scores)
                    scoresRef.current = newScoreList
                    setScores(newScoreList)
                    setUid(newScores.uid)

                    myRef.current?.scrollTop = state.current?.scroll ?: 0.0

                    if (state.current?.let { s -> s.page < 3 } == true) {
                        loadNextPage()
                    }
                } else if (newScores.valid) {
                    setUid(newScores.uid)
                }
            }.catch {
                // Cancelled request
            }
        }
    }

    fun scoreColor(accuracy: Float) =
        accuracy.let {
            when {
                it > 0.9 -> "text-info"
                it > 0.8 -> "text-success"
                it > 0.7 -> ""
                it > 0.6 -> "text-warning"
                else -> "text-danger"
            }
        }

    val handleScroll = { _: Event? ->
        val trigger = 100
        if (myRef.current != null) {
            val clientHeight = myRef.current?.clientHeight ?: 0
            val scrollTop = myRef.current?.scrollTop ?: 0.0
            val scrollHeight = myRef.current?.scrollHeight ?: 0

            state.update {
                scroll = scrollTop
            }

            if (scrollHeight - (scrollTop + clientHeight) < trigger) {
                loadNextPage()
            }
        }
    }

    useEffectOnceWithCleanup {
        window.addEventListener("scroll", handleScroll)
        onCleanup {
            window.removeEventListener("scroll", handleScroll)
        }
    }

    useEffectWithCleanup(props.selected, props.type) {
        state.update {
            token = Axios.CancelToken.source()
            loading = false
            scroll = 0.0
            page = 1
        }

        scoresRef.current = listOf()
        setScores(listOf())
        setUid(null)

        loadNextPage()

        onCleanup {
            state.current?.token?.cancel("Another request started")
        }
    }

    div {
        attrs.className = ClassName("scores")
        table {
            attrs.className = ClassName("table table-striped table-dark")
            thead {
                tr {
                    th {
                        attrs.scope = "col"
                        +"#"
                    }
                    th {
                        attrs.scope = "col"
                        +"Player"
                    }
                    th {
                        attrs.scope = "col"
                        +"Score"
                    }
                    th {
                        attrs.scope = "col"
                        +"Mods"
                    }
                    th {
                        attrs.scope = "col"
                        +"%"
                    }
                    th {
                        attrs.scope = "col"
                        +"PP"
                    }
                    th {
                        attrs.scope = "col"
                        uid?.let { uid1 ->
                            a {
                                attrs.href = "${props.type.url}$uid1"
                                attrs.target = WindowTarget._blank
                                img {
                                    attrs.alt = props.type.name
                                    attrs.src = "/static/${props.type.name.lowercase()}.svg"
                                }
                            }
                        }
                    }
                }
            }
            tbody {
                ref = myRef
                attrs.onScroll = {
                    handleScroll(null)
                }
                scores.forEachIndexed { idx, it ->
                    val maxScore = props.selected?.maxScore ?: 0
                    val accuracy = it.accuracy ?: (it.score / maxScore.toFloat())
                    score {
                        attrs.key = idx.toString()
                        attrs.position = idx + 1
                        attrs.playerId = it.playerId
                        attrs.name = it.name
                        attrs.pp = it.pp
                        attrs.score = it.score
                        attrs.scoreColor = scoreColor(accuracy)
                        attrs.mods = it.mods
                        attrs.percentage = (accuracy * 100f).fixedStr(2) + "%"
                    }
                }
            }
        }
    }
}
