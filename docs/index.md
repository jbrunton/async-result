---
layout: default
title: AsyncResult
---

<a href="#what-is-this">What is this?</a> &middot;
<a href="#a-simple-example">A simple example</a> &middot;
<a href="#getting-started">Getting started</a> &middot;
<a href="#documentation">Documentation</a>

## What is this?

`AsyncResult` is a monadic type for operating over values representing the state in an asynchronous environment. In essence:

```kotlin
sealed class AsyncResult<out T> {
    data class Success<T>(val value: T)
    data class Loading<T>(val cachedValue: T? = null)
    data class Failure<T>(val error: Throwable,
                          val cachedValue: T? = null)
}
```

This is a fairly standard pattern: see for example Kotlin's own [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html) type. The key improvements provided by `AsyncResult` are:

1. A third possible value `AsyncResult.Loading` so that loading states do not have to be treated as special cases when processing results.
2. `AsyncResult.Loading` and `AsyncResult.Failure` values may carry cached values.
3. The library includes methods to support transformations over the type, to greatly simplify the writing of pure functional pipelines that can convert results into any arbitrary interface or action.

## A simple example

Here's a fairly standard scenario when making an HTTP request:

1. If a response is returned, transform it into an appropriate view state for the app.
2. While loading, show the cached value if there is one.
3. If the app experiences a network error, show a "No Connection" empty state with an option for the user to try again if there's no cached value.
4. On network errors, if there is a cached value, show that instead of the empty state.

```kotlin
fun handleResult(result: AsyncResult<MyData>): MyViewState
  return result
      .map { MyViewState.from(it) }
      .onLoading { it.useCachedValue().or(LoadingViewState) }
      .onError(IOException::class) {
        map { it.useCachedValue().or(NoConnectionViewState) }
      }
      .get()
}
```

## Filtering errors

We can also filter on more specific errors. For example, consider this scenario:

1. If the user is authenticated, show account details.
2. If the user got their credentials wrong, show the login state again.
3. If there's a network error, show a Snackbar.

```kotlin
fun handleResult(result: AsyncResult<Account>): MyViewState
  return result
      .map { AccountViewState.from(it) }
      .onError(HttpException::class) {
        map { SignedOutViewState } whenever { it.code() == 401 }
      }
      .onError(IOException::class) {
        map { showSnackbar() }
      }
      .get()
}
```

## Getting started

First make sure you're using [Jitpack](https://jitpack.io/) by adding it as a repository:

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the `async-result` artifact as a dependency to your build file:

```groovy
dependencies {
    implementation 'com.github.jbrunton:async-result:VERSION'
}
```

## Documentation

For more details see [the docs](dokka/async-result).