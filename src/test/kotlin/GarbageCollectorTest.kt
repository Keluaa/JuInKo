package com.github.keluaa.juinko

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class GarbageCollectorTest: BaseTest() {

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
    fun withoutRootingItGetsGC() {
        monitor.reset()

        val constructor = jl.getBaseObj("IOBuffer")
        val obj = jl.jl_call0(constructor)!!

        monitor.track(obj)

        jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)

        Assertions.assertEquals(obj, monitor.getLast())
    }

    @Test
    fun push() {
        jl.jl_gc_collect(Julia.JL_GC_FULL)

        val a: jl_value_t = jl.jl_box_int32(2)
        jl.JL_GC_PUSH(a).use {
            jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)
        }

        Assertions.assertEquals(2, jl.jl_unbox_int32(a))
    }

    @Test
    fun pushPushPopPop() {
        jl.jl_gc_collect(Julia.JL_GC_FULL)

        val a: jl_value_t = jl.jl_box_int32(2)
        jl.JL_GC_PUSH(a).use {
            val b: jl_value_t = jl.jl_box_int32(3)
            jl.JL_GC_PUSH(b).use {
                jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)
            }

            Assertions.assertEquals(2, jl.jl_unbox_int32(a))
            Assertions.assertEquals(3, jl.jl_unbox_int32(b))

            jl.jl_gc_collect(Julia.JL_GC_INCREMENTAL)
        }

        Assertions.assertEquals(2, jl.jl_unbox_int32(a))
    }

    @Test
    fun array() {
        val plus = jl.getBaseObj("+")
        GCStack(jl, 4).use { stack ->
            stack[0] = jl.jl_box_int32(1)
            stack[1] = jl.jl_box_int32(2)
            stack[2] = jl.jl_box_int32(3)
            stack[3] = jl.jl_box_int32(4)
            val sum = jl.jl_call(plus, stack.array(), stack.size)!!
            Assertions.assertEquals(10, jl.jl_unbox_int32(sum))
        }
    }
}