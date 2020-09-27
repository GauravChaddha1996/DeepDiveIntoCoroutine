package com.example.testproject

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * @return a job to represent the coroutine for 'T'
 * */
fun taskT(): Job {
    /*
    * Starting task 'T' by launching coroutine 'C'
    *
    * Task name: T
    * Coroutine name: C
    * Thread pool: IO is used since we do network calls etc.
    * Scope: We use global scope since the operation is to be completed even if the page closes.
    * Builder explanation:  launch builder is used since it is a fire and forget task the calling page doesn't care about it's result.
    * suspension: No this won't block the main thread but simply start 'C'.
    *
    * We return the representative job.
    * */
    return GlobalScope.launch(Dispatchers.IO) {

        println("Starting C on ${Thread.currentThread().name}\n")

        /*
        * Next we need to do task 'T1' which is a network call. So we call t1()
        *
        * suspension: Since t1() is marked suspend, it'll suspend the calling function as we don't start any new coroutine using a builder.
        *             But since the code of 't1' isn't launching a new coroutine, the same coroutine will be used to execute t1().
        *             Meaning we continue as if it's just a synchronous function and the calling function isn't executing any further
        *             i.e. only when t1 returns with the result will T execute any further.
        * Task name: T1
        * Coroutine name: C1 which is just C
        * Thread pool: Still IO as we are on 'C'
        *
        * We make the network call and then return the result.
        * */

        val resultT1 = t1()

        /*
        * Next we need to do task 'T2' whose output is input to task 'T3' both of which are a network call.
        *
        * Builder explanation: We need the result from these two task for our final result. So we'll use async builder.
        *                      It'll return a deferred object which we store in variable 'jobT2T3'.
        *                      Deferred is a job whose value one can await.
        * Task name: T2, T3
        * Coroutine name: C2, C3 respectively
        * Thread pool: Since nothing is mentioned, while mixing context the parent dispatcher is used i.e. IO.
        * suspension: async is started as soon as it's called but it won't suspend the calling function.
        *             Async or launch will also not suspend the calling coroutine since the former returns a deferred and the latter is fire and forget type.
        * scope: As a child if nothing is mentioned it inherits the scope of the parent. So this job will also run in global scope.
        * */
        println("Starting task T2,T3 on ${Thread.currentThread().name}")
        val jobT2T3 = async {
            println("Inside Task T2,T3 on ${Thread.currentThread().name}")
            // t2 and t3 are suspend functions and will suspend this block until it returns the result
            val resultT2 = t2(resultT1)
            val resultT3 = t3(resultT2)
            return@async resultT3
        }

        /*
        * Since task T4, T5 aren't dependant on task T2 or T3 using async for T2,T3 is a win for us
        * since we can continue our execution.
        * Task 'T4' output is input to task 'T5'.
        *
        * Builder explanation: async is used as we need result from these two tasks for our final result
        * Task name: T4, T5
        * Coroutine name: C4, C5 respectively
        * Thread pool: Since T4 is a file read and T5 we database write operation, we use default thread pool for our combined coroutine
        * suspension: It won't suspend the calling function.
        * start context item : To demonstrate start keyword, we pass it as lazy - meaning only start this async coroutine when await() is called.
        *
        * */
        println("Starting task T4,T5 on ${Thread.currentThread().name}\n")
        val jobT4T5 = async(Dispatchers.Default, start = CoroutineStart.LAZY) {
            println("Inside Task T4,T5 on ${Thread.currentThread().name}")
            val resultT4 = t4()
            val resultT5 = t5(scope = this, param4 = resultT4)
            return@async resultT5
        }

        // delay so that task T2,T3 starts and T4,T5 doesn't, to demonstrate use of CoroutineStart.
        delay(100)
        println("Going to await result of task T2,T3 and T4,T5")

        // Here we await the result of task T2,T3 and T4,T5 and then combine their result.
        // await() is a suspend function which either suspends the execution of calling function if the
        // deferred doesn't hold the value or will return the result instantly.
        // Suspension: Will suspend the calling coroutine

        // Note: Here it may so happen that jobT2T3 await gives the result back and then we call await on
        // jopT4T5 and it'll return the result immediately since it already computed it while t2 and t3 were executing.
        // This is an example where calling a suspend function doesn't result in a suspension even though we were launching coroutines
        // inside it because the result was already calculated by the time await was called.
        val combinedResult = jobT2T3.await() + "_" + jobT4T5.await()

        // After the await() calls for both tasks are done, the jobs are now in the completed state.
        println("\nThe final result is $combinedResult")
    }
}

suspend fun t1(): Int {
    println("Inside t1() on ${Thread.currentThread().name}")
    // Emulate network call
    delay(100)
    return 1
}

suspend fun t2(param1: Int): String {
    println("Inside t2() on ${Thread.currentThread().name}")
    // Emulate network call
    delay(200)
    return "${param1}_".plus("resultT2")
}

suspend fun t3(param2: String): String {
    println("Inside t3() on ${Thread.currentThread().name}")
    // Emulate network call
    delay(200)
    return param2.plus("_").plus("resultT3")
}

suspend fun t4(): String {
    println("Inside t4() on ${Thread.currentThread().name}")
    try {
        // Emulate reading a file
        delay(100)
        return "resultT4"
    } catch (e: Exception) {
        // Ignore exception handling for now
        return ""
    }
}

// A way to make custom dispatcher using Executors
object CustomDispatchers {
    val DB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}

// Scope is passed so we can launch our db operation while respecting the scope
suspend fun t5(scope: CoroutineScope, param4: String): String {

    // Since this is a db entry operation we run this block using the DB dispatcher we made.
    return scope.async(CustomDispatchers.DB) {
        println("Inside t5() on ${Thread.currentThread().name}")
        try {
            // Emulate db entry operation
            delay(100)
            return@async param4.plus("_").plus("resultT5")
        } catch (e: Exception) {
            // Ignore exception handling for now
            return@async ""
        }
    }.await()
}

fun main() {
    // Thread: Main
    // Builder explanation: runBlocking is used for bridging coroutine
    //      code to normal code.
    //      It blocks the thread calling it until it's execution is complete.
    //      We call our taskT() on main thread and launch coroutines inside it.
    //      It returns the coroutine job as result.
    runBlocking {
        val parentCoroutineC = taskT()

        // Join has to be used for example purposes otherwise the program
        // will complete before 'T' completes.
        // This is because since 'T' is a fire and forget task, we start it
        // with launch coroutine builder which will return instantly with
        // the representative job 'C' and the execution of this block will be complete.
        // So to wait for 'C' to complete we wait until it joins.

        // Also note that since we start 'C' in GlobalScope
        // it's not a child of the scope provided by the runBlocking.
        // Hence the parent-child relationship doesn't apply.
        parentCoroutineC.join()
    }
    println("Exiting main()")
    exitProcess(0)
}