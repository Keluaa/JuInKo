package com.github.keluaa.juinko

import com.github.keluaa.juinko.types.jl_tls_states_t
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIf
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
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

        @JvmStatic
        fun hasAdoption() = JuliaVersion >= JuliaVersion("1.9.0")
    }

    private val threadExceptions = mutableListOf<Throwable>()
    private val threadExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
        synchronized(threadExceptions) {
            threadExceptions.add(e)
        }
    }

    private fun trackThreadExceptions(thread: Thread) = thread.setUncaughtExceptionHandler(threadExceptionHandler)
    private fun checkForThreadExceptions() {
        if (threadExceptions.isEmpty()) return
        threadExceptions.forEach { it.printStackTrace() }
        Assertions.fail<Unit>("${threadExceptions.size} thread${if (threadExceptions.size > 1) "s" else ""} had an exception")
    }

    @BeforeEach
    fun cleanup() = threadExceptions.clear()

    @Test
    fun threadCount() {
        val threadsModule = jl.getBaseObj("Threads")
        val nthreadsFunc = jl.getModuleObj(threadsModule, "nthreads")
        val threadsCount = jl.jl_unbox_int64(jl.jl_call0(nthreadsFunc)!!)
        Assertions.assertEquals(JULIA_THREADS.toLong(), threadsCount)

        Assertions.assertEquals(JULIA_THREADS, jl.threadsCount())
    }

    @RepeatedTest(10)
    @Timeout(500, unit = TimeUnit.MILLISECONDS)
    fun naiveMultithreading() {
        val mainLock = ReentrantLock()
        val withTLS = AtomicInteger(0)
        val noTLS = AtomicInteger(0)

        // WARNING: This is what you shouldn't do when mixing JVM and Julia threads
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

        threads.forEach(this::trackThreadExceptions)

        // Make sure that all threads are all running at the same time, maximizing the chance that the JVM uses a thread
        // with no TLS initialized by Julia.
        mainLock.lock()
        threads.forEach { it.start() }
        mainLock.unlock()
        threads.forEach { it.join() }

        checkForThreadExceptions()

        Assertions.assertEquals(JULIA_THREADS*2, noTLS.get() + withTLS.get())
        Assertions.assertNotEquals(0, noTLS.get())
    }

    @Test
    fun threadGCStates() {
        fun getGCState() = jl_tls_states_t.fromTask(jl.jl_current_task()).gc_state

        Assertions.assertEquals(jl_tls_states_t.JL_GC_STATE_UNSAFE, getGCState())

        val oldState = jl.jl_gc_safe_enter()
        Assertions.assertEquals(jl_tls_states_t.JL_GC_STATE_UNSAFE, oldState)
        Assertions.assertEquals(jl_tls_states_t.JL_GC_STATE_SAFE, getGCState())

        let {
            val oldState2 = jl.jl_gc_unsafe_enter()
            Assertions.assertEquals(jl_tls_states_t.JL_GC_STATE_SAFE, oldState2)
            Assertions.assertEquals(jl_tls_states_t.JL_GC_STATE_UNSAFE, getGCState())
            jl.jl_gc_unsafe_leave(oldState2)
            Assertions.assertEquals(oldState2, getGCState())
        }

        jl.jl_gc_safe_leave(oldState)
        Assertions.assertEquals(oldState, getGCState())
    }

    @RepeatedTest(10)
    @EnabledIf("hasAdoption")
    @Timeout(500, unit = TimeUnit.MILLISECONDS)
    fun adoptThread() {
        val adoptedThreads = AtomicInteger(0)
        val tids = Array(JULIA_THREADS) { 0 }
        val nextTid = jl.jl_n_threads().toShort()

        // IMPORTANT: here we only use Julia through adopted worker threads, meaning that the main Julia thread will
        // never do Julia work, and therefore never reach any GC safepoint. If worker threads try to run the GC, they
        // will deadlock, since the default Julia is marked as `JL_GC_STATE_UNSAFE` by default.
        // See below for an alternative.
//        jl.putMainJuliaThreadToSleep()

        // TODO: currently not merged in the 1.10-DEV branch, therefore this would fails in julia-nightly
        //  When merged, remove the `< v1.10` condition
        val isGCSafe = JuliaVersion >= JuliaVersion(1, 9, 2) && JuliaVersion < JuliaVersion(1, 10)

        val threads = mutableListOf<Thread>()
        for (i in 0 until JULIA_THREADS) {
            threads.add(Thread {
                if (!jl.inJuliaThread()) adoptedThreads.incrementAndGet()
                assertDoesNotThrow { jl.runAsJuliaThread { GCStack(jl, 1).use {
                    val jl_tid = jl.jl_threadid()
                    tids[i] = jl_tid.toInt()

                    // Only safe starting from 1.9.2
                    if (!isGCSafe) return@runAsJuliaThread

                    // Print only once per test repetition
                    if (jl_tid == nextTid) DebuggingUtils.printThreadsStates(jl)

                    // Will deadlock if the GC states of all Julia threads are not perfect
                    jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)
                }}}
            })
        }

        threads.forEach(this::trackThreadExceptions)

        // Instead of using `jl.putMainJuliaThreadToSleep()`, we can also wrap the part where the main thread waits for
        // the workers with `jl.runOutsideJuliaThread`. This is not compatible with all use cases however.
        // Explanation: we are running on the main JVM thread, and the associated Julia thread (tid=0) is still marked
        // as `JL_GC_STATE_UNSAFE` (0). `runOutsideJuliaThread` only applies to the current thread, and it will prevent
        // a deadlock in this case since we are using Julia only on worker threads: the current thread will never reach
        // a GC safepoint while waiting for the worker threads to complete!
        jl.runOutsideJuliaThread {
            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }
//        threads.forEach { it.start() }
//        threads.forEach { it.join() }

        checkForThreadExceptions()

        Assertions.assertNotEquals(0, adoptedThreads.get())
        for (tid in tids) {
            // No original Julia thread should have run
            Assertions.assertTrue(tid >= JULIA_THREADS)
        }
    }

    @RepeatedTest(10)
    @EnabledIf("hasAdoption")
    fun runWhileGC() {
        // Testing `jl_gc_safe_leave` and `jl_gc_unsafe_enter` when it should trigger a GC safepoint while the GC is
        // running, which should result in a segfault.
        // `runAsJuliaThread` and `runOutsideJuliaThread` should prevent that segfault.
        val readyToGC = CyclicBarrier(2)
        val gcDone = CyclicBarrier(2)
        fun waitUntilGC() { while (!jl.jl_gc_running()) Thread.sleep(1) }
        // [0] -> the order in which atomic operations return are in the comments

        val gcThread = Thread {
            jl.runAsJuliaThread {
                // Each full sweep should take more than 10 ms
                readyToGC.await()  // [2]
                jl.jl_gc_collect(Julia.JL_GC_FULL)
                gcDone.await()  // [3]

                readyToGC.await()  // [6]
                jl.jl_gc_collect(Julia.JL_GC_FULL)
                gcDone.await()  // [7]

                readyToGC.await()  // [10]
                jl.jl_gc_collect(Julia.JL_GC_FULL)
                gcDone.await()  // [11]
            }
        }

        val dangerThread = Thread {
            // `jl_gc_safe_leave()` while the GC is running (old state is `JL_GC_STATE_UNSAFE`)
            jl.runAsJuliaThread {
                jl.runOutsideJuliaThread {  // `JL_GC_STATE_UNSAFE` to `JL_GC_STATE_SAFE` => no safepoint
                    readyToGC.await()  // [1] Note that we trigger the GC after adopting all threads of the test
                    waitUntilGC()
                }  // `jl_gc_safe_leave()` => // `JL_GC_STATE_SAFE` to `JL_GC_STATE_UNSAFE` => safepoint
            }
            gcDone.await()  // [4]

            // `jl_gc_unsafe_enter()` while the GC is running (old state is `JL_GC_STATE_SAFE`)
            jl.runOutsideJuliaThread {  // `JL_GC_STATE_SAFE` to `JL_GC_STATE_SAFE` => no safepoint
                readyToGC.await()  // [5]
                waitUntilGC()
                jl.runAsJuliaThread {  // `jl_gc_unsafe_enter()` => `JL_GC_STATE_SAFE` to `JL_GC_STATE_UNSAFE` => safepoint
                }
            }
            gcDone.await()  // [8]

            // `jl_gc_unsafe_enter()` while the GC is running (old state is `JL_GC_STATE_SAFE`), i.e. the GC is waiting for this thread
            jl.runAsJuliaThread {
                readyToGC.await()  // [9]
                waitUntilGC()
                jl.runAsJuliaThread {  // `jl_gc_safepoint()` should be called to avoid a deadlock
                    gcDone.await()  // [12]
                }
            }
        }

        trackThreadExceptions(gcThread)
        trackThreadExceptions(dangerThread)

        jl.runOutsideJuliaThread {
            gcThread.start()
            dangerThread.start()
            dangerThread.join(500)
            gcThread.join(500)
        }

        val allFinished = !dangerThread.isAlive && !gcThread.isAlive
        if (dangerThread.isAlive) dangerThread.interrupt()
        if (gcThread.isAlive) gcThread.interrupt()

        checkForThreadExceptions()
        Assertions.assertTrue(allFinished)
    }

    @Test
    @EnabledIf("hasAdoption")
    fun adoptingWhileGC() {
        val readyToGC = CyclicBarrier(2)

        val gcThread = Thread {
            jl.runAsJuliaThread {
                readyToGC.await()
                jl.jl_gc_collect(Julia.JL_GC_FULL)
            }
        }

        val dangerThread = Thread {
            readyToGC.await()
            while (!jl.jl_gc_running()) Thread.sleep(1)
            jl.runAsJuliaThread { }
        }

        trackThreadExceptions(gcThread)
        trackThreadExceptions(dangerThread)

        jl.runOutsideJuliaThread {
            gcThread.start()
            dangerThread.start()
            dangerThread.join(500)
            gcThread.join(500)
        }

        val allFinished = !dangerThread.isAlive && !gcThread.isAlive
        if (dangerThread.isAlive) dangerThread.interrupt()
        if (gcThread.isAlive) gcThread.interrupt()

        checkForThreadExceptions()
        Assertions.assertTrue(allFinished)
    }
}