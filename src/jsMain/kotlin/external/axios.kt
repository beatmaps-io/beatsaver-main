package external

import io.beatmaps.common.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.js.Promise

external interface AxiosTransformer
inline operator fun AxiosTransformer.invoke(data: Any, headers: Any? = null) = asDynamic()(data, headers).unsafeCast<Any>()

external interface AxiosAdapter
inline operator fun AxiosAdapter.invoke(config: AxiosRequestConfig) = asDynamic()(config).unsafeCast<AxiosPromise<Any>>()

external interface AxiosBasicCredentials {
    var username: String
    var password: String
}
external interface `T$0` {
    var username: String
    var password: String
}
external interface AxiosProxyConfig {
    var host: String
    var port: Number
    var auth: `T$0`? get() = definedExternally; set(value) = definedExternally
}
external interface AxiosRequestConfig {
    var url: String? get() = definedExternally; set(value) = definedExternally
    var method: String? get() = definedExternally; set(value) = definedExternally
    var baseURL: String? get() = definedExternally; set(value) = definedExternally
    var transformRequest: dynamic /* external.AxiosTransformer | Array<external.AxiosTransformer> */ get() = definedExternally; set(value) = definedExternally
    var transformResponse: dynamic /* external.AxiosTransformer | Array<external.AxiosTransformer> */ get() = definedExternally; set(value) = definedExternally
    var headers: Any? get() = definedExternally; set(value) = definedExternally
    var params: Any? get() = definedExternally; set(value) = definedExternally
    var paramsSerializer: ((params: Any) -> String)? get() = definedExternally; set(value) = definedExternally
    var data: Any? get() = definedExternally; set(value) = definedExternally
    var timeout: Number? get() = definedExternally; set(value) = definedExternally
    var withCredentials: Boolean? get() = definedExternally; set(value) = definedExternally
    var adapter: AxiosAdapter? get() = definedExternally; set(value) = definedExternally
    var auth: AxiosBasicCredentials? get() = definedExternally; set(value) = definedExternally
    var responseType: String? get() = definedExternally; set(value) = definedExternally
    var xsrfCookieName: String? get() = definedExternally; set(value) = definedExternally
    var xsrfHeaderName: String? get() = definedExternally; set(value) = definedExternally
    var onUploadProgress: ((progressEvent: AxiosProgress) -> Unit)? get() = definedExternally; set(value) = definedExternally
    var onDownloadProgress: ((progressEvent: Any) -> Unit)? get() = definedExternally; set(value) = definedExternally
    var maxContentLength: Number? get() = definedExternally; set(value) = definedExternally
    var validateStatus: ((status: Number) -> Boolean)? get() = definedExternally; set(value) = definedExternally
    var maxRedirects: Number? get() = definedExternally; set(value) = definedExternally
    var httpAgent: Any? get() = definedExternally; set(value) = definedExternally
    var httpsAgent: Any? get() = definedExternally; set(value) = definedExternally
    var proxy: dynamic /* Boolean | external.AxiosProxyConfig */ get() = definedExternally; set(value) = definedExternally
    var cancelToken: CancelToken? get() = definedExternally; set(value) = definedExternally
}
external interface AxiosProgress {
    var loaded: Int
    var total: Int
}
external interface AxiosResponse<T> {
    var data: T
    var status: Number
    var statusText: String
    var headers: Any
    var config: AxiosRequestConfig
    var request: Any? get() = definedExternally; set(value) = definedExternally
}

external interface Error

external interface AxiosError : Error {
    var config: AxiosRequestConfig
    var code: String? get() = definedExternally; set(value) = definedExternally
    var request: Any? get() = definedExternally; set(value) = definedExternally
    var response: AxiosResponse<Any>? get() = definedExternally; set(value) = definedExternally
}
external class AxiosPromise<T> : Promise<AxiosResponse<T>>
external interface CancelStatic
external interface Cancel {
    var message: String
}
external interface Canceler
inline operator fun Canceler.invoke(message: String?) = asDynamic()(message).unsafeCast<Unit>()

external interface CancelTokenStatic {
    fun source(): CancelTokenSource
}
external interface CancelToken {
    var promise: Promise<Cancel>
    var reason: Cancel? get() = definedExternally; set(value) = definedExternally
    fun throwIfRequested()
}
external interface CancelTokenSource {
    var token: CancelToken
    var cancel: Canceler
}
external interface AxiosInterceptorManager<V> {
    fun use(onFulfilled: ((value: V) -> dynamic /* V | Promise<V> */)? = definedExternally /* null */, onRejected: ((error: Any) -> Any)? = definedExternally /* null */): Number
    fun eject(id: Number)
}
external interface `T$1` {
    var request: AxiosInterceptorManager<AxiosRequestConfig>
    var response: AxiosInterceptorManager<AxiosResponse<Any>>
}
external interface AxiosInstance {
    var defaults: AxiosRequestConfig
    var interceptors: `T$1`
    fun <T> request(config: AxiosRequestConfig): AxiosPromise<T>
    fun <T> get(url: String, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<T>
    fun delete(url: String, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<Any>
    fun head(url: String, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<Any>
    fun <T> post(url: String, data: Any? = definedExternally /* null */, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<T>
    fun <T> put(url: String, data: Any? = definedExternally /* null */, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<T>
    fun <T> patch(url: String, data: Any? = definedExternally /* null */, config: AxiosRequestConfig? = definedExternally /* null */): AxiosPromise<T>
}
inline operator fun AxiosInstance.invoke(config: AxiosRequestConfig) = asDynamic()(config).unsafeCast<AxiosPromise<Any>>()
inline operator fun AxiosInstance.invoke(url: String, config: AxiosRequestConfig?) = asDynamic()(url, config).unsafeCast<AxiosPromise<Any>>()

external interface AxiosStatic : AxiosInstance {
    fun create(config: AxiosRequestConfig? = definedExternally /* null */): AxiosInstance
    var Cancel: CancelStatic
    var CancelToken: CancelTokenStatic
    fun isCancel(value: Any): Boolean
    fun <T> all(values: Array<dynamic /* T | Promise<T> */>): Promise<Array<T>>
    fun <T, R> spread(callback: (args: T) -> R): (array: Array<T>) -> R
}

@JsModule("axios")
@JsNonModule
external val Axios: AxiosStatic = definedExternally

inline fun <reified T, reified U> generateConfig(ct: CancelToken? = null) = object : AxiosRequestConfig {
    override var cancelToken = ct
    override var transformRequest: Array<(T, dynamic) -> String> = arrayOf(
        { data, headers -> transformRequest(data, headers) }
    )
    override var transformResponse: (String) -> U = {
        if (it is U) {
            it
        } else {
            json.decodeFromString(it)
        }
    }
}

inline fun <reified T> axiosDelete(url: String, body: T) = Axios.delete(
    url,
    object : AxiosRequestConfig {
        override var data: Any? = body
        override var transformRequest: Array<(T, dynamic) -> String> = arrayOf(
            { data, headers -> transformRequest(data, headers) }
        )
    }
)

inline fun <reified T> transformRequest(data: T, headers: dynamic) =
    when {
        data === undefined -> ""
        data is String -> data
        else -> {
            headers["Content-Type"] = "application/json"
            json.encodeToString(data)
        }
    }

inline fun <reified T> axiosGet(url: String) = Axios.get<T>(url, generateConfig<String, T>())
