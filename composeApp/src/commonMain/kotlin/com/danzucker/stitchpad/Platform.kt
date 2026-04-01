package com.danzucker.stitchpad

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform