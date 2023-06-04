package com.keluaa.juinko

import com.keluaa.juinko.impl.JuliaLoader
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class ExceptionsTest {

    companion object {
        private lateinit var jl: Julia

        @BeforeAll
        @JvmStatic
        fun setUp() {
            jl = JuliaLoader.get()
        }

        private const val PRINT_STACK_TRACES = true

        @Language("Julia")
        private const val EXCEPTION_HANDLER_SOURCE = """
            function handle_exceptions(error_buffer::IO)
                for (exc, bt) in current_exceptions()
                    showerror(error_buffer, exc, bt)
                    println(error_buffer)
                end
            end
        """

        @Language("Julia")
        private const val SIMPLE_ERROR_WITH_BACKTRACE = """
        f3() = error("Error")
        f2() = f3()
        f1() = f2()
        
        function call_f1(err::IO)
            try
                f1()
            catch
                handle_exceptions(err)
            end
        end
        """

        @Language("Julia")
        private const val NESTED_ERRORS_WITH_BACKTRACE = """
        f3() = error("f3 Error")
        f2() = f3()
        f1() = f2()
        
        function ff3()
            try
                f1()
            catch
                error("ff3 Error")
            end
        end
        
        ff2() = ff3()
        ff1() = ff2()
        
        function call_f11(err::IO)
            try
                ff1()
            catch
                handle_exceptions(err)
            end
        end
        """

        private const val WRONG_CODE = """
        function a = 4
        """

        @Language("Julia")
        private const val UNKNOWN_FUNCTION = """
        function a()
            return abracadabra()
        end
        """
    }

    fun handleReturnValue(ret: jl_value_t, errorBuffer: IOBuffer): jl_value_t {
        if (!jl.isNothing(ret)) return ret  // No errors
        if (errorBuffer.isEmpty) return ret  // Returned nothing, but no errors
        throw JuliaException(errorBuffer)
    }

    @Test
    fun simpleError() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        val callf1 = jl.jl_eval_string(SIMPLE_ERROR_WITH_BACKTRACE)!!
        jl.jl_call1(callf1, jl.jl_stdout_obj())
        Assertions.assertNull(jl.jl_exception_occurred())
    }

    @Test
    fun exceptionChaining() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        val callf1 = jl.jl_eval_string(SIMPLE_ERROR_WITH_BACKTRACE)!!

        val ret = jl.jl_call1(callf1, jl.errorBuffer().pointer)!!
        Assertions.assertNull(jl.jl_exception_occurred())
        Assertions.assertTrue(jl.isNothing(ret))
        Assertions.assertNotEquals(0, jl.errorBuffer().size)

        val exception = Assertions.assertThrows(JuliaException::class.java) {
            handleReturnValue(ret, jl.errorBuffer())
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }

    @Test
    fun nestedExceptions() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        val callff1 = jl.jl_eval_string(NESTED_ERRORS_WITH_BACKTRACE)!!

        val ret = jl.jl_call1(callff1, jl.errorBuffer().pointer)!!
        Assertions.assertNull(jl.jl_exception_occurred())
        Assertions.assertTrue(jl.isNothing(ret))
        Assertions.assertNotEquals(0, jl.errorBuffer().size)

        val exception = Assertions.assertThrows(JuliaException::class.java) {
            handleReturnValue(ret, jl.errorBuffer())
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }

    @Test
    fun jl_load_file_string() {
        // TODO: test jl_load_file_string to see if we can put the correct file name in the backtraces
        // TODO: BUT also make jl_load_file_string throw an error to see if we can handle it somewhat
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        val callff1 = jl.jl_load_file_string(NESTED_ERRORS_WITH_BACKTRACE, NESTED_ERRORS_WITH_BACKTRACE.length.toLong(),
            "ExceptionsTest.kt::NESTED_ERRORS_WITH_BACKTRACE", jl.main_module())!!
        val ret = jl.jl_call1(callff1, jl.errorBuffer().pointer)!!
        val exception = Assertions.assertThrows(JuliaException::class.java) {
            handleReturnValue(ret, jl.errorBuffer())
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }

    @Test
    @Disabled
    fun jl_load_file_string_error() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)

        val try_catch_delegate = """
            struct FunctionCall{Ret, Args}
                f::Ptr{Cvoid}
            end
            
            function ezg(err::IO, function_call::FunctionCall{}, args)
                try
                    ccall(f, )
                catch
                    handle_exception(err)
                end
            end
        """.trimIndent()

        val callff1 = jl.jl_load_file_string(WRONG_CODE, WRONG_CODE.length.toLong(),
            "ExceptionsTest.kt::WRONG_CODE", jl.main_module())

        // TODO
    }

    @Test
    fun parsingException() {
        jl.jl_eval_string(WRONG_CODE)
        val exception = Assertions.assertThrows(JuliaException::class.java) {
            jl.exceptionCheck()
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }

    @Test
    fun loadingException() {
        // TODO: call a non-existing function
        val func = jl.jl_eval_string(UNKNOWN_FUNCTION)!!
        Assertions.assertNull(jl.jl_exception_occurred())

        jl.jl_call0(func)
        val exception = Assertions.assertThrows(JuliaException::class.java) {
            jl.exceptionCheck()
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }
}