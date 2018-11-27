package com.jbrunton.async

import kotlin.reflect.KClass

sealed class AsyncResult<out T> {
    data class Success<T>(val value: T): AsyncResult<T>()
    data class Failure<T>(val error: Throwable, val cachedValue: T? = null): AsyncResult<T>()
    data class Loading<T>(val cachedValue: T? = null): AsyncResult<T>()
}

fun <T> AsyncResult<T>.get(): T {
    return when (this) {
        is AsyncResult.Success -> this.value
        is AsyncResult.Loading -> this.cachedValue ?: throw NullPointerException()
        is AsyncResult.Failure -> this.cachedValue ?: throw this.error
    }
}

fun <T> AsyncResult<T>.getOr(defaultValue: T): T {
    return when (this) {
        is AsyncResult.Success -> this.value
        is AsyncResult.Loading -> this.cachedValue ?: defaultValue
        is AsyncResult.Failure -> this.cachedValue ?: defaultValue
    }
}

fun <T> AsyncResult<T>.getOrNull(): T? {
    return getOr(null)
}

fun <S, T, U> AsyncResult<S>.zipWith(other: AsyncResult<T>, transform: (S, T) -> U): AsyncResult<U> {
    if (this is AsyncResult.Success && other is AsyncResult.Success) {
        return AsyncResult.Success(transform(this.value, other.value))
    }

    val cachedValue = this.getOrNull()?.let { thisValue ->
        other.getOrNull()?.let { otherValue -> transform(thisValue, otherValue) }
    }

    return if (this is AsyncResult.Failure) {
        AsyncResult.Failure(this.error, cachedValue)
    } else if (other is AsyncResult.Failure) {
        AsyncResult.Failure(other.error, cachedValue)
    } else {
        AsyncResult.Loading(cachedValue)
    }
}

fun <S, T> AsyncResult<S>.map(transform: (S) -> T): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Success -> AsyncResult.Success(transform(this.value))
        is AsyncResult.Loading -> AsyncResult.Loading(this.cachedValue?.let(transform))
        is AsyncResult.Failure -> AsyncResult.Failure(this.error, this.cachedValue?.let(transform))
    }
}

fun <T> AsyncResult<T>.onSuccess(transform: (AsyncResult.Success<T>) -> AsyncResult<T>): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Success -> transform(this)
        else -> this
    }
}

fun <T> AsyncResult<T>.onLoading(transform: (AsyncResult.Loading<T>) -> AsyncResult<T>): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Loading -> transform(this)
        else -> this
    }
}

fun <T> AsyncResult<T>.onFailure(transform: (AsyncResult.Failure<T>) -> AsyncResult<T>): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Failure -> transform(this)
        else -> this
    }
}

fun <T> AsyncResult<T>.doOnSuccess(action: (AsyncResult.Success<T>) -> Unit): AsyncResult<T> {
    return onSuccess { action(it); it }
}

fun <T> AsyncResult<T>.doOnLoading(action: (AsyncResult.Loading<T>) -> Unit): AsyncResult<T> {
    return onLoading { action(it); it }
}

fun <T> AsyncResult<T>.doOnFailure(action: (AsyncResult.Failure<T>) -> Unit): AsyncResult<T> {
    return onFailure{ action(it); it }
}

fun <T, E: Throwable> AsyncResult<T>.onError(
        klass: KClass<E>,
        block: ErrorHandler<T, E>.() -> Unit
) = ErrorHandler(klass, this).apply(block).handle()

class ErrorHandler<T, E: Throwable>(val klass: KClass<E>, val result: AsyncResult<T>) {
    var filter: (E) -> Boolean = { true }

    infix fun whenever(filter: (E) -> Boolean) = apply {
        this.filter = filter
    }

    var transform: ((AsyncResult.Failure<T>) -> AsyncResult<T>) = { it }

    infix fun map(transform: (AsyncResult.Failure<T>) -> AsyncResult<T>) = apply {
        this.transform = transform
    }

    fun handle(): AsyncResult<T> {
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
