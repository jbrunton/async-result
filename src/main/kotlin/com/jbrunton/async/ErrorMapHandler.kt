package com.jbrunton.async

import kotlin.reflect.KClass

/**
 * DSL builder for error handling - see [AsyncResult.onError] for usage.
 */
class ErrorMapHandler<T, E: Throwable>(klass: KClass<E>): AbstractErrorActionHandler<T, E>(klass) {
    private lateinit var onFailure: ((AsyncResult.Failure<T>) -> AsyncResult<T>)

    infix fun use(onFailure: (AsyncResult.Failure<T>) -> T) = apply {
        this.onFailure = { AsyncResult.success(onFailure(it)) }
    }

    infix fun map(onFailure: (AsyncResult.Failure<T>) -> AsyncResult<T>) = apply {
        this.onFailure = onFailure
    }

    internal fun handle(result: AsyncResult<T>): AsyncResult<T> {
        when (result) {
            is AsyncResult.Failure -> {
                if (klass.isInstance(result.error) && filter(result.error as E)) {
                    return onFailure(result)
                } else {
                    return result
                }
            }
            else -> return result
        }
    }
}
