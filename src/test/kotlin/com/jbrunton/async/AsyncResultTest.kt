package com.jbrunton.async

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test
import javax.xml.ws.http.HTTPException

class AsyncResultTest {
    val value = 123
    val cachedValue = 456
    val error = Throwable("error")

    @Test
    fun returnsTheValue() {
        assertThat(success(value).get()).isEqualTo(value)
    }

    @Test
    fun returnsTheCachedValueIfLoading() {
        assertThat(loading(value).get()).isEqualTo(value)
    }

    @Test(expected = NullPointerException::class)
    fun throwsIfLoadingAndNoCachedValue() {
        loading(null).get()
    }

    @Test
    fun returnsTheCachedValueIfFailure() {
        assertThat(failure(IllegalStateException(), value).get()).isEqualTo(value)
    }

    @Test(expected = IllegalStateException::class)
    fun throwsIfFailureAndNoCachedValue() {
        failure(IllegalStateException(), null).get()
    }

    @Test
    fun convertsSuccessToValueOrNull() {
        assertThat(success(value).getOrNull()).isEqualTo(value)
    }

    @Test
    fun convertsLoadingToValueOrNull() {
        assertThat(loading(cachedValue).getOrNull()).isEqualTo(cachedValue)
        assertThat(loading(null).getOrNull()).isNull()
    }

    @Test
    fun convertsFailureToValueOrNull() {
        assertThat(failure(error, cachedValue).getOrNull()).isEqualTo(cachedValue)
        assertThat(failure(error,null).getOrNull()).isNull()
    }

    @Test
    fun transformsSuccessfulResults() {
        val result = success(2).map { it * 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun transformsLoadingResults() {
        val result = loading(2).map { it * 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun transformsFailureResults() {
        val result = failure(error, 2).map { it * 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun testFlatMapSuccess() {
        assertThat(success(1).flatMap { success(it + 2) }).isEqualTo(success(3))
    }

    @Test
    fun testFlatMapLoading() {
        assertThat(loading(1).flatMap { success(it + 2) }).isEqualTo(success(3))
        assertThat(loading(null).flatMap { success(it + 2) }).isEqualTo(loading(null))
    }

    @Test
    fun testFlatMapFailure() {
        assertThat(failure(error, 1).flatMap { success(it + 2) }).isEqualTo(success(3))
        assertThat(failure(error, null).flatMap { success(it + 2) }).isEqualTo(failure(error, null))
    }

    @Test
    fun transformsOnlySuccessResults() {
        val result = success(1).onSuccess {
            AsyncResult.Success(it.value * 2)
        }
        assertThat(result).isEqualTo(AsyncResult.Success(2))
    }

    @Test
    fun doesNotTransformOtherResultsWhenExpectingSuccess() {
        loading(null).onSuccess { throw IllegalStateException() }
        failure(error,null).onSuccess { throw IllegalStateException() }
    }

    @Test
    fun transformsOnlyLoadingResults() {
        val result = loading(1).onLoading {
            AsyncResult.Success(it.get() * 2)
        }
        assertThat(result).isEqualTo(AsyncResult.Success(2))
    }

    @Test
    fun doesNotTransformOtherResultsWhenExpectingLoading() {
        success(value).onLoading { throw IllegalStateException() }
        failure(error,null).onLoading { throw IllegalStateException() }
    }

    @Test
    fun transformsOnlyFailureResults() {
        val result = failure(error,1).onFailure {
            AsyncResult.Success(it.get() * 2)
        }
        assertThat(result).isEqualTo(AsyncResult.Success(2))
    }

    @Test
    fun doesNotTransformOtherResultsWhenExpectingFailure() {
        success(value).onFailure { throw IllegalStateException() }
        loading(value).onFailure { throw IllegalStateException() }
    }

    @Test
    fun invokesActionsOnSuccessResults() {
        var x = 0
        success(value).doOnSuccess { x = it.value + 1 }
        assertThat(x).isEqualTo(value + 1)
    }

    @Test
    fun invokesActionsOnLoadingResults() {
        var x = 0
        loading(cachedValue).doOnLoading { x = it.cachedValue!! + 1 }
        assertThat(x).isEqualTo(cachedValue + 1)
    }

    @Test
    fun invokesActionsOnFailureResults() {
        var x = 0
        loading(cachedValue).doOnLoading { x = it.cachedValue!! + 1 }
        assertThat(x).isEqualTo(cachedValue + 1)
    }

    @Test
    fun usesCachedValuesForErrors() {
        val result = failure(HTTPException(401), 1).onError(HTTPException::class) {
            use { it.get() * 2 }
        }
        assertThat(result).isEqualTo(AsyncResult.Success(2))
    }

    @Test
    fun usesCachedValuesForSpecificErrors() {
        val result = failure(HTTPException(401), 2)

        val transformedResult = result.onError(HTTPException::class) {
            use { it.get() * 2 } whenever { it.statusCode == 401 }
        }
        val otherResult = result.onError(HTTPException::class) {
            use { it.get() * 2 } whenever { it.statusCode == 400 }
        }

        assertThat(transformedResult).isEqualTo(AsyncResult.Success(4))
        assertThat(otherResult).isEqualTo(result)
    }

    @Test
    fun mapsErrors() {
        val result = failure(HTTPException(401), 1).onError(HTTPException::class) {
            map { success(it.get() * 2) }
        }
        assertThat(result).isEqualTo(AsyncResult.Success(2))
    }

    @Test
    fun mapSpecificErrors() {
        val result = failure(HTTPException(401), 2)

        val transformedResult = result.onError(HTTPException::class) {
            map { success(it.get() * 2) } whenever { it.statusCode == 401 }
        }
        val otherResult = result.onError(HTTPException::class) {
            map { success(it.get() * 2) } whenever { it.statusCode == 400 }
        }

        assertThat(transformedResult).isEqualTo(AsyncResult.Success(4))
        assertThat(otherResult).isEqualTo(result)
    }

    @Test
    fun actsOnErrors() {
        var x = 0
        failure(HTTPException(401), 1).doOnError(HTTPException::class) {
            action { x = it.cachedValue!! + 1 }
        }
        assertThat(x).isEqualTo(2)
    }

    @Test
    fun actsOnSpecificErrors() {
        val result = failure(HTTPException(401), 2)
        var x = 0
        var y = 0

        result.doOnError(HTTPException::class) {
            action { x = it.cachedValue!! + 1 } whenever { it.statusCode == 401 }
        }
        result.doOnError(HTTPException::class) {
            action { y = it.cachedValue!! + 1 } whenever { it.statusCode == 400 }
        }

        assertThat(x).isEqualTo(3)
        assertThat(y).isEqualTo(0)
    }

    @Test
    fun zipsSuccesses() {
        val result = success(2).zipWith(success(3)) { x, y -> x * y }
        assertThat(result).isEqualTo(AsyncResult.Success(6))
    }

    @Test
    fun zipsLeftFailures() {
        val result = failure(error, 2).zipWith(success(3)) { x, y -> x * y }
        assertThat(result).isEqualTo(AsyncResult.Failure(error, 6))
    }

    @Test
    fun zipsRightFailures() {
        val result = success(3).zipWith(failure(error, 2)) { x, y -> x * y }
        assertThat(result).isEqualTo(AsyncResult.Failure(error, 6))
    }

    @Test
    fun zipsLeftLoadingResults() {
        val result = loading(2).zipWith(success(3)) { x, y -> x * y }
        assertThat(result).isEqualTo(AsyncResult.Loading(6))
    }

    @Test
    fun zipsRightLoadingResults() {
        val result = success(2).zipWith(loading(3)) { x, y -> x * y }
        assertThat(result).isEqualTo(AsyncResult.Loading(6))
    }

    @Test
    fun returnsCachedValues() {
        val loadingResult = loading(cachedValue)
        val failureResult = failure(error, cachedValue)

        assertThat(loadingResult.useCachedValue()).isEqualTo(success(cachedValue))
        assertThat(failureResult.useCachedValue()).isEqualTo(success(cachedValue))
    }

    @Test
    fun returnsSelfWhenNoCachedValues() {
        val loadingResult = loading(null)
        val failureResult = failure(error, null)

        assertThat(loadingResult.useCachedValue()).isEqualTo(loadingResult)
        assertThat(failureResult.useCachedValue()).isEqualTo(failureResult)
    }

    @Test
    fun testOrOperation() {
        assertThat(loading(1).or(success(2))).isEqualTo(success(2))
        assertThat(success(1).or(success(2))).isEqualTo(success(1))

        assertThat(loading(1).or(2)).isEqualTo(success(2))
        assertThat(success(1).or(2)).isEqualTo(success(1))
    }

    @Test
    fun testZip2() {
        val result = AsyncResult.zip(
                success(1),
                success(2)
        ) { x, y ->
            x + y
        }
        assertThat(result).isEqualTo(success(3))
    }

    @Test
    fun testZip3() {
        val result = AsyncResult.zip(
                success(1),
                success(2),
                success(3)
        ) { x, y, z ->
            x + y + z
        }
        assertThat(result).isEqualTo(success(6))
    }

    @Test
    fun testZip4() {
        val result = AsyncResult.zip(
                success(1),
                success(2),
                success(3),
                success(4)
        ) { u, v, x, y ->
            u + v + x + y
        }
        assertThat(result).isEqualTo(success(10))
    }

    @Test
    fun testZip5() {
        val result = AsyncResult.zip(
                success(1),
                success(2),
                success(3),
                success(4),
                success(5)
        ) { u, v, x, y, z ->
            u + v + x + y + z
        }
        assertThat(result).isEqualTo(success(15))
    }

    @Test
    fun testZip5Loading() {
        val result = AsyncResult.zip(
                success(1),
                success(2),
                success(3),
                loading(4),
                success(5)
        ) { u, v, x, y, z ->
            u + v + x + y + z
        }
        assertThat(result).isEqualTo(loading(15))
    }

    @Test
    fun testZip5Failure() {
        val result = AsyncResult.zip(
                success(1),
                success(2),
                success(3),
                failure(error, 4),
                success(5)
        ) { u, v, x, y, z ->
            u + v + x + y + z
        }
        assertThat(result).isEqualTo(failure(error, 15))
    }

    private fun success(value: Int): AsyncResult.Success<Int> {
        return AsyncResult.Success(value)
    }

    private fun loading(cachedValue: Int?): AsyncResult.Loading<Int> {
        return AsyncResult.Loading(cachedValue)
    }

    private fun failure(error: Throwable, cachedValue: Int?): AsyncResult.Failure<Int> {
        return AsyncResult.Failure(error, cachedValue)
    }
}
