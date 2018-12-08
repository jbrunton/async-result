package com.jbrunton.async

import kotlin.reflect.KClass

/**
 * DSL builder for error handling - see [AsyncResult.doOnError] for usage.
 */
class ErrorActionHandler<T, E: Throwable>(klass: KClass<E>) : AbstractErrorActionHandler<T, E>(klass) {
    var action: (AsyncResult.Failure<T>) -> Unit = { _ -> }

    infix fun action(action: (AsyncResult.Failure<T>) -> Unit) = apply {
        this.action = action
    }

    internal fun handle(result: AsyncResult<T>): AsyncResult<T> {
        when (result) {
            is AsyncResult.Failure -> {
                if (klass.isInstance(result.error) && filter(result.error as E)) {
                    action(result)
                }
            }
        }
        return result
    }
}
