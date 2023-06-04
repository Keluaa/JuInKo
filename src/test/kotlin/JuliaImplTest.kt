package com.keluaa.juinko

import com.keluaa.juinko.impl.JuliaLoader
import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class JuliaImplTest {

    companion object {
        private lateinit var jl: Julia

        @BeforeAll
        @JvmStatic
        fun setUp() {
            jl = JuliaLoader.get()
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
}