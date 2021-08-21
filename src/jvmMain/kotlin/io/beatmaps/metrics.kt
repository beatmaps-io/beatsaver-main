package io.beatmaps

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.userAgent
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.Clock
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import nl.basjes.parse.useragent.UserAgentAnalyzer

fun Application.installMetrics() {
    val config: InfluxConfig = object : InfluxConfig {
        val config = mapOf(
            "autoCreateDb" to "false",
            "batchSize" to "10000",
            "compressed" to "true",
            "connectTimeout" to "5s",
            "consistency" to "one",
            "db" to (System.getenv("INFLUX_DB") ?: "telegraf"),
            "enabled" to (System.getenv("INFLUX_ENABLED") ?: "false"),
            "numThreads" to "2",
            "password" to (System.getenv("INFLUX_PASS") ?: "mysecret"),
            "readTimeout" to "10s",
            "retentionPolicy" to (System.getenv("INFLUX_RP") ?: "two_weeks"),
            "step" to (System.getenv("INFLUX_STEP") ?: "1m"),
            "uri" to (System.getenv("INFLUX_URI") ?: "http://localhost:8086"),
            "userName" to (System.getenv("INFLUX_USER") ?: "myusername")
        )

        override fun prefix() = "influx"
        override fun get(k: String): String? = config[k.removePrefix("influx.")]
    }

    val uaa = UserAgentAnalyzer
        .newBuilder()
        .withCache(10_000)
        .withField("AgentClass")
        .withField("OperatingSystemNameVersionMajor")
        .withField("AgentName")
        .withField("AgentNameVersion")
        .withField("AgentNameVersionMajor")
        .addResources("classpath:agents/*.yaml")
        .build()

    val mods = hashSetOf("BMBF", "QuestSongDownloader", "BeatSaverVoting", "ModAssistant", "Beatlist", "PlaylistManager", "MorePlaylists", "BSDataPuller", "DiTails",
        "Beatsaber", "BeatSaberPlus", "SongRequestManager", "PlaylistDownLoader", "Beatdrop", "SiraUtil", "BeatSyncConsole")

    val appMicrometerRegistry = InfluxMeterRegistry(config, Clock.SYSTEM)
    appMicrometerRegistry.config().commonTags("host", System.getenv("HOSTNAME") ?: "unknown")

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        timers { call, _ ->
            call.attributes[extraTags].forEach {
                tag(it.key, it.value)
            }
        }
    }

    // Request timing header
    intercept(ApplicationCallPipeline.Monitoring) {
        val t = Timings()
        t.begin("req")
        call.attributes.put(reqTime, t)

        val tags = call.request.userAgent()?.let {
            val parsed = uaa.parse(it)
            val agentMajorKey = if (mods.contains(parsed.get("AgentName").value)) "AgentNameVersion" else "AgentNameVersionMajor"

            mutableMapOf(
                "agentClass" to parsed.get("AgentClass").value,
                "agentMajor" to parsed.get(agentMajorKey).value,
                "osMajor" to parsed.get("OperatingSystemNameVersionMajor").value
            )
        } ?: mutableMapOf()

        call.attributes.put(extraTags, tags)
    }
    sendPipeline.intercept(ApplicationSendPipeline.Before) {
        val mk = call.attributes[reqTime]
        mk.end("req")
        context.response.headers.append("Server-Timing", mk.getHeader())
    }
}

private val extraTags = AttributeKey<MutableMap<String, String>>("extraTags")
private val reqTime = AttributeKey<Timings>("serverTiming")
fun <T> ApplicationCall.timeIt(name: String, block: () -> T) = attributes[reqTime].timeIt(name, block)
fun ApplicationCall.tag(name: String, value: String) = attributes[extraTags].put(name, value)

class Timings {
    private val metrics = mutableMapOf<String, Float>()
    private val begins = mutableMapOf<String, Long>()

    fun getHeader() = metrics.map { "${it.key};dur=${it.value}" }.joinToString(", ")

    fun begin(name: String) {
        begins[name] = System.nanoTime()
    }

    fun end(name: String) {
        metrics[name] = ((System.nanoTime() - (begins[name] ?: 0)) / 1000) / 1000f
    }

    fun <T> timeIt(name: String, block: () -> T) =
        begin(name).let {
            block().also {
                end(name)
            }
        }
}
