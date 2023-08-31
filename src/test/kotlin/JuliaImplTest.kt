package com.github.keluaa.juinko

import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class JuliaImplTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsInitialized()
        }
    }

    @Test
    fun jl_box_unbox_int32() {
        val i = 0x7D04EFA5
        val boxed = jl.jl_box_int32(i)
        assertNotNull(boxed)
        val unboxed = jl.jl_unbox_int32(boxed)
        assertEquals(i, unboxed)
    }

    @Test
    fun jl_alloc_array_1d() {
        val intVector = jl.jl_eval_string("Vector{Int}")
        requireNotNull(intVector)
        val vec = jl.jl_alloc_array_1d(intVector, 10)
        assertNull(jl.jl_exception_occurred())
        assertNotNull(vec)
        assertEquals(intVector, jl.jl_typeof(vec))
        val size = jl.jl_array_size(vec, 1)
        assertEquals(10, size)
    }

    @Test
    fun jl_apply_type1() {
        val vec = jl.getBaseObj("Vector")
        val int = jl.getBaseObj("Int")
        val intVec = jl.jl_apply_type1(vec, int)
        assertNotNull(intVec)
        assertNull(jl.jl_exception_occurred())
        val realIntVec = jl.jl_eval_string("Vector{Int}")
        assertEquals(realIntVec, intVec)
    }

    @Test
    fun jl_call() {
        val add = jl.getBaseObj("+")
        val a = jl.jl_box_int32(1)
        val b = jl.jl_box_int32(2)
        val cVal = jl.jl_call2(add, a, b)
        assertNotNull(cVal)
        val c = jl.jl_unbox_int32(cVal!!)
        assertEquals(3, c)
    }

    @Test
    fun jl_get_current_task() {
        val currentTaskPtr = jl.jl_eval_string("UInt(pointer_from_objref(current_task()))")!!
        val currentTaskAddr = jl.jl_unbox_int64(currentTaskPtr)
        val ourCurrentTaskAddr = Pointer.nativeValue(jl.jl_current_task().pointer)
        assertEquals(currentTaskAddr, ourCurrentTaskAddr)
    }

    @Test
    fun jl_typeof() {
        val vector = jl.jl_eval_string("[1, 2]")

        val vectorTypePtr = jl.jl_call1(jl.getBaseObj("typeof"), vector!!)
        val vectorTypeAddr = Pointer.nativeValue(vectorTypePtr)

        val ourVectorType = jl.jl_typeof(vector)
        val ourVectorTypeAddr = Pointer.nativeValue(ourVectorType)

        assertEquals(vectorTypeAddr, ourVectorTypeAddr)
    }

    @Test
    fun jl_checked_assignment() {
        val mod = jl.jl_main_module()
        val v = jl.jl_symbol("my_val")

        GCStack(jl, 1).use { stack ->
            val b = jl.jl_get_binding_wr(mod, v, 1)
            jl.exceptionCheck()
            // Bindings are rooted to the module and are NOT a `jl_value_t` before 1.10, therefore they do not belong on
            // the stack (even after 1.10!)
            b!!

            val rhs = jl.jl_box_int64(42)
            stack[0] = rhs

            jl.jl_declare_constant(b, mod, v)
            jl.exceptionCheck()

            jl.jl_checked_assignment(b, mod, v, rhs)
            jl.exceptionCheck()
        }

        val value = jl.jl_get_global(mod, v)
        jl.exceptionCheck()
        val unboxed = jl.jl_unbox_int64(value!!)
        assertEquals(42, unboxed)
    }

    @Test
    fun jl_set_typeof() {
        // We cast a Vector{UInt} to a Vector{Int} to test if it works
        val vectorType = jl.getBaseObj("Vector")
        val eltype = jl.getBaseObj("eltype")
        val uintType = jl.getBaseObj("UInt")
        val intType = jl.getBaseObj("Int")
        val vectorUintType = jl.jl_apply_type1(vectorType, uintType)
        val vectorIntType = jl.jl_apply_type1(vectorType, intType)

        val array = jl.jl_alloc_array_1d(vectorUintType!!, 4)
        jl.jl_set_typeof(array, vectorIntType!!)

        // Let Julia determine the type of the array
        val array_eltype_ptr = jl.jl_call1(eltype, array)

        val array_eltype_addr = Pointer.nativeValue(array_eltype_ptr)
        val int_t_addr = Pointer.nativeValue(intType)
        assertEquals(int_t_addr, array_eltype_addr)
    }

    @Test
    fun jl_is_mutable() {
        val vector = jl.jl_eval_string("zeros(10)")!!
        val vectorType = jl.jl_typeof(vector)
        assertTrue(jl.jl_is_mutable(vectorType))

        val number = jl.jl_box_int32(42)
        val numberType = jl.jl_typeof(number)
        assertTrue(jl.jl_is_immutable(numberType))
    }

    @Test
    fun jl_true() {
        assertEquals(Pointer.nativeValue(jl.jl_box_bool(1)), Pointer.nativeValue(jl.jl_true()))
        assertTrue(jl.isTrue(jl.jl_box_bool(1)))
    }

    @Test
    fun jl_nothing() {
        assertEquals(Pointer.nativeValue(jl.getBaseObj("nothing")), Pointer.nativeValue(jl.jl_nothing()))
        assertTrue(jl.isNothing(jl.jl_nothing()))
    }

    @Test
    fun readme_example() {
        val result = GCStack(jl, 2).use { stack ->
            stack[0] = jl.jl_box_int64(1)
            stack[1] = jl.jl_box_int64(2)
            val resultBoxed = jl.jl_call(jl.getBaseObj("+"), stack.array(), 2)
            jl.jl_unbox_int64(resultBoxed!!)
        }
        assertEquals(3, result)
    }
}