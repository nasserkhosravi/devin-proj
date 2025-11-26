package com.khosravi.sample.devin

import kotlin.random.Random


fun aString(length: Int = 5, maybeNumber: Boolean = true, random: Random = Random): String {
    require(length > -1) {
        "length must bigger than -1"
    }
    var allowedChars = ('A'..'Z') + ('a'..'z')
    if (maybeNumber) {
        allowedChars = allowedChars.plus('0'..'9')
    }
    return (1..length)
        .map { allowedChars.random(random) }
        .joinToString("")
}

fun generateRandomStringKB(sizeInKB: Int): String {
    val sizeInBytes = sizeInKB * 1024
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val stringBuilder = StringBuilder(sizeInBytes)

    repeat(sizeInBytes) {
        val randomIndex = Random.nextInt(chars.length)
        stringBuilder.append(chars[randomIndex])
    }

    return stringBuilder.toString()
}