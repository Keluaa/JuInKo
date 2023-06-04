package com.keluaa.juinko

class JuliaException: Exception {
    private val context_msg: String
    private val julia_msg: String

    constructor(error_buffer: IOBuffer) {
        context_msg = ""
        julia_msg = error_buffer.getStringAndClear()
    }

    constructor(context: String, error_buffer: IOBuffer) {
        context_msg = context
        julia_msg = error_buffer.getStringAndClear()
    }

    constructor(error_msg: String) {
        context_msg = ""
        julia_msg = error_msg
    }

    constructor(context: String, error_msg: String) {
        context_msg = context
        julia_msg = error_msg
    }

    override val message: String
        get() {
            if (julia_msg.isEmpty()) return context_msg
            if (context_msg.isEmpty()) return "Julia exception message:\n$julia_msg\n"
            return "$context_msg\nJulia exception message:\n$julia_msg\n"
        }
}


class NotInJuliaThreadException(private val context: String = ""): Exception() {
    override val message: String
        get() {
            return "Attempted to use a Julia function in a non-Julia thread" +
                    (if (context.isEmpty()) "." else ": $context")
        }
}
