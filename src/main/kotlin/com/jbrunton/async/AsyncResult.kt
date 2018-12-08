package com.jbrunton.async

import kotlin.reflect.KClass

/**
 * A discriminated union representing possible states in an asynchronous environment.
 *
 * Possible values are [AsyncResult.Success], [AsyncResult.Loading] and [AsyncResult.Failure].
 */
sealed class AsyncResult<out T> {
    /**
     * Represents a successful [AsyncResult] with a value of [value].
     *
     * @param T the type of the value of the result.
     * @property value the successful result value.
     * @constructor Creates a successful [AsyncResult].
     */
    data class Success<T>(val value: T): AsyncResult<T>()

    /**
     * Represents a loading [AsyncResult] state with an optional cached value of [cachedValue].
     *
     * @param T the type of the optional cached value.
     * @property cachedValue the optional cached value.
     * @constructor Creates a loading [AsyncResult].
     */
    data class Loading<T>(val cachedValue: T? = null): AsyncResult<T>()

    /**
     * Represents an [AsyncResult] failure state.
     *
     * @param T the type of the optional cached value.
     * @property error details of the error.
     * @property cachedValue the optional cached value.
     * @constructor Creates a failure [AsyncResult].
     */
    data class Failure<T>(val error: Throwable, val cachedValue: T? = null): AsyncResult<T>()

    companion object {
        fun <T> success(value: T) = AsyncResult.Success(value)
        fun <T> loading(cachedValue: T?) = AsyncResult.Loading(cachedValue)
        fun <T> failure(error: Throwable, cachedValue: T? = null) = AsyncResult.Failure(error, cachedValue)
    }
}


/**
 * Returns the value of the result.
 *
 * * For [AsyncResult.Success] this will be [value][AsyncResult.Success.value].
 * * For [AsyncResult.Loading] this will be [cachedValue][AsyncResult.Loading.cachedValue] if it is not null. Otherwise
 * a NullPointerException will be thrown.
 * * For [AsyncResult.Failure] this will be [cachedValue][AsyncResult.Failure.cachedValue] if it is not null. Otherwise
 * the [error][AsyncResult.Failure.error] will be thrown.
 */
fun <T> AsyncResult<T>.get(): T {
    return when (this) {
        is AsyncResult.Success -> this.value
        is AsyncResult.Loading -> this.cachedValue ?: throw NullPointerException()
        is AsyncResult.Failure -> this.cachedValue ?: throw this.error
    }
}

/**
 * Returns the value of the result if it has one and the given [defaultValue] otherwise.
 *
 * * For [AsyncResult.Success] this will be [value][AsyncResult.Success.value].
 * * For [AsyncResult.Loading] this will be [cachedValue][AsyncResult.Loading.cachedValue] if it exists and
 * [defaultValue] otherwise.
 * * For [AsyncResult.Failure] this will be [cachedValue][AsyncResult.Failure.cachedValue] if it exists and
 * [defaultValue] otherwise.
 */
fun <T> AsyncResult<T>.getOr(defaultValue: T): T {
    return when (this) {
        is AsyncResult.Success -> this.value
        is AsyncResult.Loading -> this.cachedValue ?: defaultValue
        is AsyncResult.Failure -> this.cachedValue ?: defaultValue
    }
}

/**
 * Returns the value of the result if it exists and null otherwise.
 *
 * * For [AsyncResult.Success] this will be [value][AsyncResult.Success.value].
 * * For [AsyncResult.Loading] this will be [cachedValue][AsyncResult.Loading.cachedValue] if it exists and
 * null otherwise.
 * * For [AsyncResult.Failure] this will be [cachedValue][AsyncResult.Failure.cachedValue] if it exists and
 * null otherwise.
 */
fun <T> AsyncResult<T>.getOrNull(): T? {
    return getOr(null)
}

/**
 * Returns [AsyncResult.Success] for any result which has a value (whether cached or not). Returns `this` otherwise.
 */
fun <T> AsyncResult<T>.useCachedValue(): AsyncResult<T> {
    val cachedValue = getOrNull()
    if (cachedValue == null) {
        return this
    } else {
        return AsyncResult.success(cachedValue)
    }
}

/**
 * Returns `this` if the result is [AsyncResult.Success]. Returns `other` otherwise.
 */
fun <T> AsyncResult<T>.or(other: AsyncResult<T>): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Success -> this
        else -> other
    }
}

/**
 * Returns `this` if the result is [AsyncResult.Success]. Returns `AsyncResult.Success(value)` otherwise.
 */
fun <T> AsyncResult<T>.or(value: T): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Success -> this
        else -> AsyncResult.success(value)
    }
}

/**
 * Returns the result of applying `transform` to the values of the results.
 *
 * Result types are as follows:
 * * If both `this` and `other` are of type [AsyncResult.Success] then returns [AsyncResult.Success].
 * * If either `this` or `other` are of type [AsyncResult.Loading] then returns [AsyncResult.Loading].
 * * If either `this` or `other` are of type [AsyncResult.Failure] then returns [AsyncResult.Failure].
 ]*/
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

/**
 * Returns a result of the same type as `this`, transforming the value.
 */
fun <S, T> AsyncResult<S>.map(transform: (S) -> T): AsyncResult<T> {
    return when (this) {
        is AsyncResult.Success -> AsyncResult.Success(transform(this.value))
        is AsyncResult.Loading -> AsyncResult.Loading(this.cachedValue?.let(transform))
        is AsyncResult.Failure -> AsyncResult.Failure(this.error, this.cachedValue?.let(transform))
    }
}

/**
 * Returns the result of `onSuccess` if the result is [AsyncResult.Success], the result of `onLoading` if the result is
 * [AsyncResult.Loading], and the result of `onFailure` if the result is [AsyncResult.Failure].
 */
fun <S, T> AsyncResult<T>.bind(
        onSuccess: (AsyncResult.Success<T>) -> S,
        onLoading: (AsyncResult.Loading<T>) -> S,
        onFailure: (AsyncResult.Failure<T>) -> S
): S {
    return when (this) {
        is AsyncResult.Success -> onSuccess(this)
        is AsyncResult.Loading -> onLoading(this)
        is AsyncResult.Failure -> onFailure(this)
    }
}

/**
 * Returns the result of `onSuccess` if the result is [AsyncResult.Success]. Otherwise returns the result unchanged.
 */
fun <T> AsyncResult<T>.onSuccess(
        onSuccess: (AsyncResult.Success<T>) -> AsyncResult<T>
) = bind(onSuccess, { it }, { it })

/**
 * Returns the result of `onLoading` if the result is [AsyncResult.Loading]. Otherwise returns the result unchanged.
 */
fun <T> AsyncResult<T>.onLoading(
        onLoading: (AsyncResult.Loading<T>) -> AsyncResult<T>
) = bind({ it }, onLoading, { it })

/**
 * Returns the result of `onFailure` if the result is [AsyncResult.Failure]. Otherwise returns the result unchanged.
 */
fun <T> AsyncResult<T>.onFailure(
        onFailure: (AsyncResult.Failure<T>) -> AsyncResult<T>
) = bind({ it }, { it }, onFailure)

/**
 * Performs `action` if the result is [AsyncResult.Success]. Returns the original result unchanged.
 */
fun <T> AsyncResult<T>.doOnSuccess(
        action: (AsyncResult.Success<T>) -> Unit): AsyncResult<T>
{
    bind(action, {}, {})
    return this
}

/**
 * Performs `action` if the result is [AsyncResult.Loading]. Returns the original result unchanged.
 */
fun <T> AsyncResult<T>.doOnLoading(
        action: (AsyncResult.Loading<T>) -> Unit): AsyncResult<T>
{
    bind({}, action, {})
    return this
}

/**
 * Performs `action` if the result is [AsyncResult.Failure]. Returns the original result unchanged.
 */
fun <T> AsyncResult<T>.doOnFailure(
        action: (AsyncResult.Failure<T>) -> Unit
): AsyncResult<T> {
    bind({}, {}, action)
    return this
}

/**
 * Conditionally transforms [AsyncResult.Failure] results based on the value of [AsyncResult.Failure.error].
 *
 * For example, to transform a network failure:
 *
 *     result.onError(IOException::class) {
 *         map { success(NoConnection) }
 *     }
 *
 * You can also filter conditionally:
 *
 *     result.onError(HttpException::class) {
 *         map { success(AuthFailure) } whenever { it.code() == 401 }
 *     }
 *
 * In cases when handling an error involves returning a success result, this shorthand is available:
 *
 *     result.onError(IOException::class) {
 *         use { NoConnection } // shorthand for map { success(NoConnection) }
 *     }
 *
 * @param klass the error class to match on.
 * @param handler the error handler to apply.
 */
fun <T, E: Throwable> AsyncResult<T>.onError(
        klass: KClass<E>,
        handler: ErrorMapHandler<T, E>.() -> Unit
) = ErrorMapHandler<T, E>(klass).apply(handler).handle(this)

/**
 * Conditionally invokes action on [AsyncResult.Failure] results based on the value of [AsyncResult.Failure.error].
 *
 * For example, to perform an action on a network failure:
 *
 *     result.doOnError(IOException::class) {
 *         action { showRetrySnackbar() }
 *     }
 *
 * You can also filter conditionally:
 *
 *     result.onError(HttpException::class) {
 *         action { showLoginPrompt() } whenever { it.code() == 401 }
 *     }
 *
 * @param klass the error class to match on.
 * @param handler the error handler to apply.
 */
fun <T, E: Throwable> AsyncResult<T>.doOnError(
        klass: KClass<E>,
        handler: ErrorActionHandler<T, E>.() -> Unit
) = ErrorActionHandler<T, E>(klass).apply(handler).handle(this)
