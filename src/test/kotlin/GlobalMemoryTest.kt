package com.keluaa.juinko

import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class GlobalMemoryTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsInitialized()
        }
    }

    private val monitor = FinalizerMonitor(jl)

    @Test
    fun insertMutable() {
        monitor.reset()
        val perm = GlobalMemory(jl)

        val constructor = jl.getBaseObj("IOBuffer")
        val obj = jl.jl_call0(constructor)!!
        perm.insert(obj)
        monitor.track(obj)

        jl.jl_gc_collect(Julia.JL_GC_FULL)
        Assertions.assertEquals(Pointer.NULL, monitor.getLast())

        perm.remove(obj)

        jl.jl_gc_collect(Julia.JL_GC_FULL)
        Assertions.assertEquals(obj, monitor.getLast())
    }

    @Test
    fun insertImmutable() {
        monitor.reset()
        val perm = GlobalMemory(jl)

        val obj = jl.jl_box_int64(42)
        val wrapper = perm.insertImmutable(obj)
        monitor.track(wrapper)

        jl.jl_gc_collect(Julia.JL_GC_FULL)
        Assertions.assertEquals(Pointer.NULL, monitor.getLast())

        perm.remove(wrapper)

        jl.jl_gc_collect(Julia.JL_GC_FULL)
        Assertions.assertEquals(wrapper.pointer, monitor.getLast())
    }
}