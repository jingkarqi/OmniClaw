package com.sora.omniclaw.core.common

data class HostError(
    val category: HostErrorCategory,
    val message: String,
    val recoverable: Boolean = false,
)

enum class HostErrorCategory {
    Validation,
    Permission,
    Storage,
    Bridge,
    Runtime,
    Unknown,
}
