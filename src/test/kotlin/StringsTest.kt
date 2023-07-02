package com.github.keluaa.juinko

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class StringsTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsInitialized()
        }
    }

    @Test
    fun ascii() {
        val str = """
            The built-in concrete type used for strings (and string literals) in Julia is String.
            This supports the full range of Unicode characters via the UTF-8 encoding.
        """.trimIndent()

        val jlStr = jl.jl_cstr_to_string(str)
        val jlStrLen = jl.jl_string_len(jlStr)
        Assertions.assertEquals(str.length.toLong(), jlStrLen)

        val jlStrData = jl.jl_string_data(jlStr).getByteArray(0, jlStrLen.toInt())
        val strBytes = str.toByteArray(Charsets.UTF_8)
        for (i in strBytes.indices) {
            Assertions.assertEquals(strBytes[i], jlStrData[i], "at index $i")
        }
    }

    @Test
    fun encodingConversion() {
        // Test string from https://www.w3.org/2001/06/utf-8-test/UTF-8-demo.html
        val str = "∮ E⋅da = Q,  n → ∞, ∑ f(i) = ∏ g(i), ∀x∈ℝ: ⌈x⌉ = −⌊−x⌋, α ∧ ¬β = ¬(¬α ∨ β),"

        val collect = jl.getBaseObj("collect")
        val jlStr = jl.jl_cstr_to_string(str)

        // `jlStr` and `testStr` have different encodings, so we can't compare their lengths. We must compare them
        // character per character.

        val jlChars = jl.jl_call1(collect, jlStr)!!
        val ktChars = str.codePoints().toArray()
        Assertions.assertEquals(ktChars.size.toLong(), jl.jl_array_len(jlChars))

        val int32 = jl.getBaseObj("UInt32")
        val gcStack = jl.JL_GC_PUSH(jlChars)
        for (i in str.indices) {
            val jlChar = jl.jl_arrayref(jlChars, i.toLong())
            val jlCodepoint = jl.jl_call1(int32, jlChar)!!
            val jlCodepointVal = jl.jl_unbox_uint32_(jlCodepoint)
            Assertions.assertEquals(ktChars[i].toUInt(), jlCodepointVal, "at index $i")
        }
        gcStack.close()
    }
}