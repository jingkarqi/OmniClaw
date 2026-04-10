package com.sora.omniclaw.core.common

sealed interface HostResult<out T> {
    data class Success<T>(val value: T) : HostResult<T>

    data class Failure(val error: HostError) : HostResult<Nothing>
}

val HostResult<*>.isSuccess: Boolean
    get() = this is HostResult.Success

val HostResult<*>.isFailure: Boolean
    get() = this is HostResult.Failure

fun <T> HostResult<T>.getOrNull(): T? = when (this) {
    is HostResult.Success -> value
    is HostResult.Failure -> null
}

fun HostResult<*>.errorOrNull(): HostError? = when (this) {
    is HostResult.Success -> null
    is HostResult.Failure -> error
}
