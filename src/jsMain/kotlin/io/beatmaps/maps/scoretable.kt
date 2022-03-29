package io.beatmaps.maps

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.invoke
import io.beatmaps.api.LeaderboardData
import io.beatmaps.api.LeaderboardScore
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.Config
import kotlinx.browser.window
import kotlinx.html.TBODY
import kotlinx.html.ThScope
import kotlinx.html.js.onScrollFunction
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.img
import react.dom.table
import react.dom.tbody
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.setState

external interface ScoreTableProps : RProps {
    var mapKey: String
    var selected: MapDifficulty?
}

external interface ScoreTableState : RState {
    var page: Int
    var loading: Boolean
    var scores: List<LeaderboardScore>
    var scroll: Int
    var uid: Int
    var token: CancelTokenSource
}

class ScoreTable : RComponent<ScoreTableProps, ScoreTableState>() {

    private val myRef = createRef<TBODY>()

    override fun componentWillMount() {
        setState {
            page = 1
            loading = false
            scores = listOf()
            scroll = 0
            uid = 0
            token = Axios.CancelToken.source()
        }
    }

    override fun componentDidMount() {
        window.addEventListener("scroll", ::handleScroll)

        loadNextPage()
    }

    override fun componentWillUpdate(nextProps: ScoreTableProps, nextState: ScoreTableState) {
        if (nextProps.selected != props.selected) {
            state.token.cancel.invoke("Another request started")

            nextState.scores = listOf()
            nextState.page = 1
            nextState.loading = false
            nextState.scroll = 0
            nextState.uid = 0
            nextState.token = Axios.CancelToken.source()

            window.setTimeout(::loadNextPage, 0)
        }
    }

    private fun loadNextPage() {
        if (state.loading)
            return

        setState {
            loading = true
        }

        Axios.get<LeaderboardData>(
            "${Config.apibase}/scores/${props.mapKey}/${state.page}?difficulty=${props.selected?.difficulty?.idx ?: 9}&gameMode=${props.selected?.characteristic?.ordinal ?: 0}",
            generateConfig<String, LeaderboardData>(state.token.token)
        ).then {
            val newScores = it.data

            if (newScores.scores.isNotEmpty()) {
                setState {
                    page += 1
                    loading = false
                    scores = scores.plus(newScores.scores)
                    uid = newScores.uid
                }

                myRef.current?.asDynamic().scrollTop = state.scroll

                if (state.page < 3) {
                    loadNextPage()
                }
            }
        }.catch {
            // Cancelled request
        }
    }

    override fun RBuilder.render() {
        div("scores col-lg-8") {
            table("table table-striped table-dark") {
                thead {
                    tr {
                        th(scope = ThScope.col) { +"#" }
                        th(scope = ThScope.col) { +"Player" }
                        th(scope = ThScope.col) { +"Score" }
                        th(scope = ThScope.col) { +"Mods" }
                        th(scope = ThScope.col) { +"%" }
                        th(scope = ThScope.col) { +"PP" }
                        th(scope = ThScope.col) {
                            if (state.uid > 0) {
                                a("https://scoresaber.com/leaderboard/${state.uid}", "_blank") {
                                    img("ScoreSaber", src = "/static/scoresaber.svg") { }
                                }
                            }
                        }
                    }
                }
                tbody {
                    ref = myRef
                    attrs.onScrollFunction = ::handleScroll
                    state.scores.forEachIndexed { idx, it ->
                        val maxScore = props.selected?.maxScore ?: 0
                        score {
                            key = idx.toString()
                            position = idx + 1
                            playerId = it.playerId
                            name = it.name
                            pp = it.pp
                            score = it.score
                            scoreColor = scoreColor(it.score, maxScore)
                            mods = it.mods
                            percentage = ((it.score * 100) / maxScore.toFloat()).fixed(2) + "%"
                        }
                    }
                }
            }
        }
    }

    override fun componentWillUnmount() {
        window.removeEventListener("scroll", ::handleScroll)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleScroll(e: Event) {
        val trigger = 100
        if (myRef.current == null) return

        val clientHeight = myRef.current.asDynamic().clientHeight as Int
        val scrollTop = myRef.current.asDynamic().scrollTop as Int
        val scrollHeight = myRef.current.asDynamic().scrollHeight as Int

        setState {
            scroll = scrollTop
        }

        if (scrollHeight - (scrollTop + clientHeight) < trigger) {
            loadNextPage()
        }
    }

    private fun scoreColor(score: Int, maxScore: Int) =
        (score / maxScore.toFloat()).let {
            when {
                it > 0.9 -> "text-info"
                it > 0.8 -> "text-success"
                it > 0.7 -> ""
                it > 0.6 -> "text-warning"
                else -> "text-danger"
            }
        }
}

fun RBuilder.scoreTable(handler: ScoreTableProps.() -> Unit): ReactElement {
    return child(ScoreTable::class) {
        this.attrs(handler)
    }
}
