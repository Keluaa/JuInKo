package com.github.keluaa.juinko

import com.github.keluaa.juinko.types.jl_tls_states_t
import com.sun.jna.Memory
import java.util.WeakHashMap

@Suppress("MemberVisibilityCanBePrivate")
object DebuggingUtils {

    var TRACK_ADOPTIONS = false
        set(v) = synchronized(DebuggingUtils) { field = v }

    val ADOPTED_THREADS = WeakHashMap<Thread, Short>()

    init {
        // The main JVM thread is also the main Julia thread
        for (thread in Thread.getAllStackTraces().keys) {
            if (thread.id != 1L) continue
            ADOPTED_THREADS[thread] = 0
            break
        }
    }

    /**
     * Marks the current thread as being adopted.
     */
    fun threadAdopted(jl: Julia) = synchronized(DebuggingUtils) {
        if (!TRACK_ADOPTIONS) return
        ADOPTED_THREADS[Thread.currentThread()] = jl.jl_threadid()
    }

    /**
     * Map from Julia thread ID (including adopted) to its current `gc_state`.
     */
    fun getJuliaThreadsStates(jl: Julia): Map<Short, Byte> {
        val allTLS = jl.jl_all_tls_states()
        return buildMap(allTLS.size) {
            for (tlsPtr in allTLS) {
                val tls = jl_tls_states_t(tlsPtr)
                this[tls.tid] = tls.gc_state
            }
        }
    }

    /**
     * Map from JVM thread to the Julia thread ID, or -1 if none.
     */
    fun getJVMToJuliaThreadsMap(): Map<Thread, Short> {
        val threads = Thread.getAllStackTraces().keys
        return buildMap(threads.size) {
            for (thread in threads) {
                this[thread] = ADOPTED_THREADS.getOrDefault(thread, (-1).toShort())
            }
        }
    }

    fun gcStateToStr(gcState: Byte): String {
        return when (gcState) {
            jl_tls_states_t.JL_GC_STATE_SAFE -> "Safe"
            jl_tls_states_t.JL_GC_STATE_WAITING -> "GC"
            else -> "Unsafe"
        }
    }

    /**
     * Prints the state and thread IDs of all Julia threads and their associated JVM thread.
     * JVM thread names are also printed as it is this name which appears in exceptions.
     *
     * Note that [TRACK_ADOPTIONS] should be `true` in order to deduce the JVM thread ID from the Julia thread ID.
     */
    fun printThreadsStates(jl: Julia) = synchronized(DebuggingUtils) {
        val jvmThreads = getJVMToJuliaThreadsMap().toSortedMap { a, b -> a.id.compareTo(b.id) }
        val reversedJvmThreads = jvmThreads.entries.associateBy({ it.value }) { it.key }
        val jlThreads = getJuliaThreadsStates(jl).toSortedMap()
        val thisThread = if (jl.inJuliaThread()) jl.jl_threadid() else -1

        print(" JL | JVM | GC STATE | JVM Name")
        print(" (GC running: ${jl.jl_gc_running()})")
        if (!TRACK_ADOPTIONS) print(" (adoption tracking disabled)")
        println()

        for ((tid, gcState) in jlThreads) {
            println(
                if (tid in reversedJvmThreads) {
                    val thread = reversedJvmThreads[tid]!!
                    " %2d | %3d | %8s | %s".format(tid, thread.id, gcStateToStr(gcState),
                        thread.name + if (tid == thisThread) " (current thread)" else ""
                    )
                } else {
                    " %2d |     | %8s | ".format(tid, gcStateToStr(gcState))
                }
            )
        }

        if (thisThread == (-1).toShort()) {
            val thread = Thread.currentThread()
            println("    | %3d | %8s | %s".format(thread.id, "", thread.name + " (current thread)"))
        }
    }

    /**
     * Utility for debugging native calls. Will call [Julia.jl_breakpoint] in an infinite loop. Works even if Julia is
     * not initialized.
     *
     * [Julia.jl_breakpoint] is passed a pointer to a 1: `*(int*)v = 1`.
     * Then you can take your time to launch and attach your C debugger (gdb, lldb, etc...) and set a breakpoint to
     * [Julia.jl_breakpoint], which will be reached almost immediately. You can then set up other breakpoints.
     * To break out of [waitUntilNativeDebugger], simply set `v` to any other value: `set *(int*)v = 2`, which will resume
     * the flow of the program.
     * This also works when using a Java/Kotlin debugger, allowing you to debug both sides at once.
     * If `printPID = true` is not practical for you, you can also find the PID of the Java process with `jsp`.
     */
    fun waitUntilNativeDebugger(jl: Julia, printPID: Boolean = true) {
        if (printPID) {
            System.err.println("DEBUG == PID: ${ProcessHandle.current().pid()}")
        }
        val fakeObj = Memory(4)
        fakeObj.setInt(0, 1)
        do {
            Thread.sleep(10)
            jl.jl_breakpoint(fakeObj)
        } while (fakeObj.getInt(0) == 1)
    }
}