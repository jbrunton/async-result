package com.jbrunton.async

import kotlin.reflect.KClass

/**
 * DSL builder for error handling - see [AsyncResult.onError] for usage.
 */
class ErrorMapHandler<T, E: Throwable>(klass: KClass<E>): AbstractErrorActionHandler<T, E>(klass) {
    var transform: (AsyncResult.Failure<T>) -> AsyncResult<T> = { it }

    infix fun map(transform: (AsyncResult.Failure<T>) -> AsyncResult<T>) = apply {
        this.transform = transform
    }

    fun handle(result: AsyncResult<T>): AsyncResult<T> {
        when (result) {
            is AsyncResult.Failure -> {
                if (klass.isInstance(result.error) && filter(result.error as E)) {
                    return transform(result)
                } else {
                    return result
                }
            }
            else -> return result
        }
    }
}
