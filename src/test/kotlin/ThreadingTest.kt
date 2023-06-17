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