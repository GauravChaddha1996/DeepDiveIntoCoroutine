package com.example.testproject

import kotlinx.coroutines.*

fun main2() {
    runBlocking {
        launch(Dispatchers.Default) {
            println(c())
        }
        launch {
            repeat(10) {
                delay(50)
                println(it * 50)
            }
        }
    }
}

suspend fun c(): Int = supervisorScope {
    val a = async { a() }
    val b = async { b() }
    return@supervisorScope a.await() + b.await()
}

suspend fun a(): Int {
    delay(200)
    throw Exception("I failed")
}

suspend fun b(): Int {
    delay(5000)
    return 2
}