package external

import js.promise.Promise
import react.ComponentClass
import react.ComponentModule
import react.ExoticComponent
import react.Props

fun <T, U : ComponentClass<V>, V : Props> Promise<T>.component(block: (T) -> U): ExoticComponent<V> =
    react.lazy {
        this.then {
            object : ComponentModule<V> {
                override val default = block(it)
            }
        }
    }
