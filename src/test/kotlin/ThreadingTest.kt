package com.keluaa.juinko

import com.sun.jna.CallbackReference
import org.junit.jupiter.api.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

internal class ThreadingTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureJuliaHasThreads()
        }
    }

    @Test
    fun threadCount() {
        val threadsModule = jl.getBaseObj("Threads")
        val nthreadsFunc = jl.getModuleObj(threadsModule, "nthreads")
        val threadsCount = jl.jl_unbox_int64(jl.jl_call0(nthreadsFunc)!!)
        Assertions.assertEquals(JULIA_THREADS.toLong(), threadsCount)

        Assertions.assertEquals(JULIA_THREADS, jl.jl_n_threads())
    }

    @Test
    fun synchronousCallback() {
        val retValue = 42

        val myCallback = JuliaCallbackRetAny {
            jl.jl_box_int32(retValue)
        }

        val spawnerCode = """
        function spawner(callback::Ptr{Cvoid})
            return ccall(callback, Any, ())
        end
        """

        val spawner = jl.jl_eval_string(spawnerCode)
        if (spawner == null) {
            jl.exceptionCheck()
            return
        }

        val value = jl.jl_call1(spawner, jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(myCallback)))
        jl.exceptionCheck()
        val jlValue = jl.jl_unbox_int32(value!!)
        Assertions.assertEquals(retValue, jlValue)
    }

    @Test
    fun asynchronousCallback() {
        val initialValue = 42
        val retValue = AtomicInteger(initialValue)

        val myCallback = JuliaCallbackRetAny {
            val ret = retValue.getAndAdd(1)
            jl.jl_box_int32(ret)
        }

        val spawnerCode = """
        function spawner(callback::Ptr{Cvoid})
            return Threads.@spawn ccall(${'$'}callback, Any, ())
        end
        """

        val spawner = jl.jl_eval_string(spawnerCode)
        if (spawner == null) {
            jl.exceptionCheck()
            return
        }

        val taskArray = Array<jl_value_t?>(JULIA_THREADS) { null }
        for (i in 0 until JULIA_THREADS) {
            val task = jl.jl_call1(spawner, jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(myCallback)))
            taskArray[i] = task
        }

        jl.exceptionCheck()

        val fetchFunc = jl.getBaseObj("fetch")
        val returnedValues = mutableListOf<Int>()
        val expectedValues = mutableListOf<Int>()
        for ((i, task) in taskArray.withIndex()) {
            if (task == null)
                throw Exception("task $i is null")
            val jlValue = jl.jl_call1(fetchFunc, task)
            if (jlValue == null) {
                println("task $i failed")
                jl.exceptionCheck()
            }
            val value = jl.jl_unbox_int32(jlValue!!)
            returnedValues.add(value)
            expectedValues.add(initialValue + i)
        }

        Assertions.assertTrue(returnedValues.containsAll(expectedValues))
    }

    @Test
    fun taskLocalStorage() {
        val myCallback = JuliaCallbackRetAny {
            // This wait should guarantee that all tasks are scheduled before the first one is completed, therefore
            // all possible tid values will be returned in the end
            Thread.sleep(250)
            val tid = jl.jl_threadid() // Relies internally on 'jl_current_task'
            jl.jl_box_int16(tid)
        }

        val spawnerCode = """
        function spawner(callback::Ptr{Cvoid})
            return Threads.@spawn ccall(${'$'}callback, Any, ())
        end
        """

        val spawner = jl.jl_eval_string(spawnerCode)
        if (spawner == null) {
            jl.exceptionCheck()
            return
        }

        val taskArray = Array(JULIA_THREADS) { jl_value_t(0) }
        for (i in 0 until JULIA_THREADS) {
            val task = jl.jl_call1(spawner, jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(myCallback)))
            taskArray[i] = task!!
        }

        val fetchFunc = jl.getBaseObj("fetch")
        val tids = mutableListOf<Short>()
        val expectedTids = mutableListOf<Short>()
        for ((i, task) in taskArray.withIndex()) {
            val retValue = jl.jl_call1(fetchFunc, task)
            jl.exceptionCheck()
            val tid = jl.jl_unbox_int16(retValue!!)
            tids.add(tid)
            expectedTids.add(i.toShort())
        }

        Assertions.assertTrue(expectedTids.containsAll(tids))
    }

    @Test
    fun juliaMultiThreading() {
        jl.assertInJuliaThread()
        GCStack(jl, 3).use { stack ->
            val threads = JuliaThreads(jl)

            val task10 = threads.spawn(JuliaCallbackRetAny {
                jl.jl_box_int32(10)
            })
            stack[0] = task10

            val task20 = threads.spawn(JuliaCallbackRetAny {
                jl.jl_box_int32(20)
            })
            stack[1] = task20

            val callbackArg = jl.jl_box_int32(1)
            stack[2] = callbackArg

            val taskAdd1 = threads.spawn(JuliaCallback1Arg {
                jl.jl_box_int32(jl.jl_unbox_int32(it) + 1)
            }, callbackArg)
            stack[2] = taskAdd1

            threads.waitForAll(stack)

            // TODO: GC safety, on the Julia side but also on the Kotlin side, as JNA requires callbacks to be referenced
            //  in order to prevent them from being invalidated

            val retValues = List(stack.size) { jl.jl_unbox_int32(stack[it]) }
            Assertions.assertEquals(listOf(10, 20, 2), retValues)
        }
    }

    @Test
    fun naiveMultithreading() {
        val mainLock = ReentrantLock()
        val withTLS = AtomicInteger(0)
        val noTLS = AtomicInteger(0)

        // This is what you shouldn't do when mixing JVM and Julia threads
        val threads = mutableListOf<Thread>()
        for (i in 0 until JULIA_THREADS*2) {
            threads.add(Thread {
                mainLock.lock()
                try {
                    if (jl.inJuliaThread()) {
                        // It is only possible to safely use Julia functions in a TLS-initialized thread
                        assertDoesNotThrow {
                            GCStack(jl, 1).use {
                                // Some code...
                            }
                        }
                        withTLS.incrementAndGet()
                    } else {
                        // Running Julia functions from a non-Julia initialized JVM thread will result in segfaults or
                        // exceptions who prevent segfaults
                        assertThrows<NotInJuliaThreadException> {
                            GCStack(jl, 1).use {
                                // Some code...
                            }
                        }
                        noTLS.incrementAndGet()
                    }
                } finally {
                    mainLock.unlock()
                }
            })
        }

        // Make sure that all threads are all running at the same time, maximizing the chance that the JVM uses a thread
        // with no TLS initialized by Julia.
        mainLock.lock()
        threads.forEach { it.start() }
        mainLock.unlock()
        threads.forEach { it.join() }

        Assertions.assertEquals(JULIA_THREADS*2, noTLS.get() + withTLS.get())
        Assertions.assertNotEquals(0, noTLS.get())
    }

    @Test
    fun adoptThread() {
        if (JuliaVersion < JuliaVersion("1.9.0"))
            return  // 'jl_adopt_thread' is in Julia 1.9 and above only

        val adoptedThreads = AtomicInteger(0)
        val tids = Array(JULIA_THREADS*2) { 0 }

        // This is what you SHOULD do when mixing JVM and Julia threads
        val threads = mutableListOf<Thread>()
        for (i in 0 until JULIA_THREADS*2) {
            threads.add(Thread {
                if (!jl.inJuliaThread()) adoptedThreads.incrementAndGet()
                assertDoesNotThrow {
                    jl.runInJuliaThread {
                        GCStack(jl, 1).use {
                            val jl_tid = jl.jl_threadid()
                            tids[i] = jl_tid.toInt()

                            val jvm_tid = Thread.currentThread().id
                            println("hello from Julia thread $jl_tid aka JVM thread $jvm_tid")

                            if (JuliaVersion >= JuliaVersion(1, 9, 2)) {
                                // Only safe starting from 1.9.2
                                jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)
                            }
                        }
                    }
                }
            })
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        Assertions.assertNotEquals(0, adoptedThreads.get())
        for (tid in tids) {
            // No original Julia thread should have run
            Assertions.assertTrue(tid >= JULIA_THREADS)
        }
    }
}