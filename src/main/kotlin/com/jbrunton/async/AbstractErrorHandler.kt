package com.jbrunton.async

import kotlin.reflect.KClass

abstract class AbstractErrorActionHandler<T, E: Throwable>(val klass: KClass<E>)
{
    var filter: (E) -> Boolean = { true }

    infix fun whenever(filter: (E) -> Boolean) = apply {
        this.filter = filter
    }
}