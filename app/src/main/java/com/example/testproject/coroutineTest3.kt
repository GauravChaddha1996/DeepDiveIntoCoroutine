package com.example.testproject

import kotlinx.coroutines.*

fun main3() {
    runBlocking {
        val job = GlobalScope.launch(Dispatchers.Default) {
            println("Starting job")
            val result = networkCall()
            println("After network call")
            println("Result: $result")
        }
        job.join()
    }
}

suspend fun networkCall(): String {
    println("Network call in progress")
    delay(100)
    println("Network call ends")
    return "Hello world"
}
