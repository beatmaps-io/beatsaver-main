package io.beatmaps

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.Clock
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry

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

    val appMicrometerRegistry = InfluxMeterRegistry(config, Clock.SYSTEM)
    appMicrometerRegistry.config().commonTags("host", System.getenv("HOSTNAME") ?: "unknown")

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}