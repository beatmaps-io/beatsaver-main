package external

import js.promise.Promise
import react.ComponentModule
import react.ComponentType
import react.ExoticComponent
import react.Props

fun <T, U : ComponentType<V>, V : Props> Promise<T>.component(block: (T) -> U): ExoticComponent<V> =
    react.lazy {
        this.then {
            object : ComponentModule<V> {
                override val default = block(it)
            }
        }
    }
