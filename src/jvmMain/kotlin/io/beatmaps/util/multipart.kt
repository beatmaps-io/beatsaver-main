package io.beatmaps.util

import io.beatmaps.cloudflare.CaptchaVerifier
import io.beatmaps.common.json
import io.beatmaps.controllers.UploadException
import io.ktor.client.HttpClient
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class MultipartRequest(val dataMap: JsonElement, private val recaptchaSuccess: Boolean) {
    inline fun <reified T> get() = json.decodeFromJsonElement<T>(dataMap)
    fun validRecaptcha(authType: AuthType) = authType == AuthType.Oauth || recaptchaSuccess
}

suspend fun PipelineContext<*, ApplicationCall>.handleMultipart(client: HttpClient, cb: suspend (PartData.FileItem) -> Unit): MultipartRequest {
    val multipart = call.receiveMultipart()
    val dataMap = mutableMapOf<String, JsonElement>()
    var recaptchaSuccess = false

    multipart.forEachPart { part ->
        if (part is PartData.FormItem) {
            // Process recaptcha immediately as it is time-critical
            if (part.name.toString() == "recaptcha") {
                recaptchaSuccess = captchaProvider { provider ->
                    val verifyResponse = withContext(Dispatchers.IO) {
                        CaptchaVerifier.verify(client, provider, part.value, call.request.origin.remoteHost)
                    }

                    verifyResponse.success || throw UploadException("Could not verify user [${verifyResponse.errorCodes.joinToString(", ")}]")
                }
            } else {
                dataMap[part.name.toString()] = try {
                    if (part.value.startsWith("{") && part.value.endsWith("}")) {
                        json.parseToJsonElement(part.value)
                    } else {
                        null
                    }
                } catch (e: SerializationException) {
                    null
                } ?: JsonPrimitive(part.value)
            }
        } else if (part is PartData.FileItem) {
            cb(part)
        }
    }

    return MultipartRequest(JsonObject(dataMap), recaptchaSuccess)
}
