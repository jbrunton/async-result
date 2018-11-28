---
title: AsyncResult
---

# What is this?

`AsyncResult` is a monadic type for operating over values in an asynchronous environment. In essence:

```
sealed class AsyncResult<out T> {
    data class Success<T>(val value: T)
    data class Loading<T>(val cachedValue: T? = null)
    data class Failure<T>(val error: Throwable, val cachedValue: T? = null)
}
```

This is a fairly standard pattern: see for example Kotlin's own [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html) type. The key additions provided by `AsyncResult` are:

1. A third possible value `AsyncResult.Loading` so that loading states do not have to be treated as special cases when processing results.
2. `AsyncResult.Loading` and `AsyncResult.Failure` values may carry cached values.
3. The library includes methods to support transformations over the type, to greatly simplify the writing of pure functional pipelines that can convert results into any arbitrary interface or action.

# A simple example
Here's a fairly standard scenario when making an http request:

1. If a response is returned, transform it into an appropriate view state for the app.
2. If the app experiences a network error, show a "No Connection" empty state with an option for the user to try again if there's no cached value.
3. On network errors, if there is a cached value, show that instead of the empty state.

```kotlin
fun handleResult(result: AsyncResult<MyData>): MyViewState
  return result
      .map { MyViewState.from(it) }
      .onLoading {
        it.useCachedValue()
      }
      .onError(IOException::class) {
        map { it.useCachedValue().or(NoConnectionViewState) }
      }
}
```

# Filtering errors
