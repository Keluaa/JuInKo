package com.keluaa.juinko

import com.keluaa.juinko.impl.JuliaLoader
import com.keluaa.juinko.types.jl_array_flags
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class ArrayTest {
    companion object {
        private lateinit var jl: Julia

        @BeforeAll
        @JvmStatic
        fun setUp() {
            jl = JuliaLoader.get()
        }
    }

    @Test
    fun arrayFlags() {
        val value = 0b1000100000000100u
        val flags = jl_array_flags(value)
        Assertions.assertEquals(value and 0b0000000000000011u shr 0, flags.how)
        Assertions.assertEquals(value and 0b0000011111111100u shr 2, flags.ndims)
        Assertions.assertEquals(value and 0b0000100000000000u shr 11, flags.pooled)
        Assertions.assertEquals(value and 0b0001000000000000u shr 12, flags.ptrarray)
        Assertions.assertEquals(value and 0b0010000000000000u shr 13, flags.hasptr)
        Assertions.assertEquals(value and 0b0100000000000000u shr 14, flags.isshared)
        Assertions.assertEquals(value and 0b1000000000000000u shr 15, flags.isaligned)
    }

    @Test
    fun dim1() {
        val constructor = jl.jl_eval_string("Array{Float64}")!!
        val undef = jl.getBaseObj("undef")
        val array = jl.jl_call2(constructor, undef, jl.jl_box_int32(10)) as jl_array_t

        Assertions.assertEquals(10, jl.jl_array_len(array))
        Assertions.assertEquals(1, jl.jl_array_ndims(array))
        Assertions.assertEquals(10, jl.jl_array_dim0(array))
        Assertions.assertEquals(10, jl.jl_array_nrows(array))
        Assertions.assertEquals(10, jl.jl_array_dim(array, 1))
    }

    @Test
    fun dim2() {
        val constructor = jl.jl_eval_string("Array{Float64}")!!
        val undef = jl.getBaseObj("undef")
        val array = jl.jl_call3(constructor, undef, jl.jl_box_int32(4), jl.jl_box_int32(7)) as jl_array_t

        Assertions.assertEquals(28, jl.jl_array_len(array))
        Assertions.assertEquals(2, jl.jl_array_ndims(array))
        Assertions.assertEquals(4, jl.jl_array_dim(array, 0))
        Assertions.assertEquals(7, jl.jl_array_dim(array, 1))
    }

    @Test
    fun dim3() {
        val constructor = jl.jl_eval_string("Array{Float64}")!!
        val undef = jl.getBaseObj("undef")
        val array: jl_array_t
        GCStack(jl, 4).use { stack ->
            stack[0] = undef
            stack[1] = jl.jl_box_int32(4)
            stack[2] = jl.jl_box_int32(7)
            stack[3] = jl.jl_box_int32(11)
            array = jl.jl_call(constructor, stack.array(0, 4), 4) as jl_array_t
        }

        Assertions.assertEquals(4*7*11, jl.jl_array_len(array))
        Assertions.assertEquals(3, jl.jl_array_ndims(array))
        Assertions.assertEquals(4, jl.jl_array_dim(array, 0))
        Assertions.assertEquals(7, jl.jl_array_dim(array, 1))
        Assertions.assertEquals(11, jl.jl_array_dim(array, 2))
    }

    @Test
    fun data() {
        val array = jl.jl_eval_string("collect(1:10)")!!
        val data = jl.jl_array_data(array)
        for (i in 0..9) {
            Assertions.assertEquals(i + 1L, data.getLong(i * 8L))
        }
    }
}