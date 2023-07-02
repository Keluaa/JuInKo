package com.keluaa.juinko

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf

internal class ExceptionsTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsInitialized()
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

        @JvmStatic
        fun if_before_1_10() = JuliaVersion < JuliaVersion(1, 10)
    }

    fun handleReturnValue(ret: jl_value_t, errorBuffer: IOBuffer): jl_value_t {
        if (!jl.isNothing(ret)) return ret  // No errors
        if (errorBuffer.isEmpty) return ret  // Returned nothing, but no errors
        throw JuliaException(errorBuffer)
    }

    @Test
    fun simpleError() {
        GCStack(jl, 1).use { stack ->
            val buffer = IOBuffer(jl)
            stack[0] = buffer.pointer

            jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
            jl.exceptionCheck()

            var callf1 = jl.jl_eval_string(SIMPLE_ERROR_WITH_BACKTRACE)
            jl.exceptionCheck()
            callf1 = callf1!!

            jl.jl_call1(callf1, buffer.pointer)
            Assertions.assertNull(jl.jl_exception_occurred())

            val str = buffer.getStringAndClear()
            println(str)
        }
    }

    @Test
    fun exceptionChaining() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        jl.exceptionCheck()

        var callf1 = jl.jl_eval_string(SIMPLE_ERROR_WITH_BACKTRACE)
        jl.exceptionCheck()
        callf1 = callf1!!

        var ret = jl.jl_call1(callf1, jl.errorBuffer().pointer)
        jl.exceptionCheck()
        ret = ret!!

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
        jl.exceptionCheck()

        var callff1 = jl.jl_eval_string(NESTED_ERRORS_WITH_BACKTRACE)
        jl.exceptionCheck()
        callff1 = callff1!!

        var ret = jl.jl_call1(callff1, jl.errorBuffer().pointer)
        jl.exceptionCheck()
        ret = ret!!

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
        jl.exceptionCheck()

        var callff1 = jl.jl_load_file_string(NESTED_ERRORS_WITH_BACKTRACE, NESTED_ERRORS_WITH_BACKTRACE.length.toLong(),
            "ExceptionsTest.kt::NESTED_ERRORS_WITH_BACKTRACE", jl.jl_main_module())
        jl.exceptionCheck()
        callff1 = callff1!!

        var ret = jl.jl_call1(callff1, jl.errorBuffer().pointer)
        jl.exceptionCheck()
        ret = ret!!

        val exception = Assertions.assertThrows(JuliaException::class.java) {
            handleReturnValue(ret, jl.errorBuffer())
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }

    @Test
    @Disabled
    fun jl_load_file_string_error() {
        jl.jl_eval_string(EXCEPTION_HANDLER_SOURCE)
        jl.exceptionCheck()

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

        var callff1 = jl.jl_load_file_string(WRONG_CODE, WRONG_CODE.length.toLong(),
            "ExceptionsTest.kt::WRONG_CODE", jl.jl_main_module())

        // TODO
    }

    @Test
    @EnabledIf("if_before_1_10")
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
        var func = jl.jl_eval_string(UNKNOWN_FUNCTION)
        jl.exceptionCheck()
        func = func!!

        jl.jl_call0(func)
        val exception = Assertions.assertThrows(JuliaException::class.java) {
            jl.exceptionCheck()
        }
        if (PRINT_STACK_TRACES) exception.printStackTrace()
    }
}