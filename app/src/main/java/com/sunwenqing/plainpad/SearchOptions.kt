package com.sunwenqing.plainpad

data class SearchOptions(
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
    val fromStart: Boolean = false
)