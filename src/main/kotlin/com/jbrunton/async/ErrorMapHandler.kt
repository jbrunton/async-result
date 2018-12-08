package com.jbrunton.async

import kotlin.reflect.KClass

/**
 * DSL builder for error handling - see [AsyncResult.onError] for usage.
 */
class ErrorMapHandler<T, E: Throwable>(klass: KClass<E>): AbstractErrorActionHandler<T, E>(klass) {
    lateinit var transform: ((AsyncResult.Failure<T>) -> T)

    infix fun map(transform: (AsyncResult.Failure<T>) -> T) = apply {
        this.transform = transform
    }

    internal fun handle(result: AsyncResult<T>): AsyncResult<T> {
        when (result) {
            is AsyncResult.Failure -> {
                if (klass.isInstance(result.error) && filter(result.error as E)) {
                    return AsyncResult.success(transform(result))
                } else {
                    return result
                }
            }
            else -> return result
        }
    }
}
