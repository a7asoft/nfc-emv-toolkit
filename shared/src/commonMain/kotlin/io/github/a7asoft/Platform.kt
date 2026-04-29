package io.github.a7asoft

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform