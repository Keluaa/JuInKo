package com.keluaa.juinko

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class BufferTest : BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsInitialized()
        }
    }

    @Test
    fun bufferWrapping() {
        val jvmStr = "This is a test for IOBuffer size"
        val bufferType = jl.getBaseObj("IOBuffer")
        val jlBuffer = jl.jl_call1(bufferType, jl.jl_cstr_to_string(jvmStr))
        val buffer = IOBuffer(jl, jlBuffer!!)

        Assertions.assertEquals(jvmStr.length, buffer.size.toInt())
        Assertions.assertEquals(jvmStr, buffer.string)
    }

    @Test
    fun bufferClear() {
        val buffer = IOBuffer(jl)

        val testStr = "test"
        val print = jl.getBaseObj("print")
        jl.jl_call2(print, buffer.pointer, jl.jl_cstr_to_string(testStr))

        Assertions.assertEquals(testStr.length, buffer.size.toInt())
        Assertions.assertEquals(testStr, buffer.string)

        buffer.clear()

        Assertions.assertEquals(0, buffer.size)
    }
}