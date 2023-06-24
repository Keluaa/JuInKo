package com.keluaa.juinko

import com.keluaa.juinko.types.JuliaStruct
import com.sun.jna.PointerType


/**
 * Interface over a `Base.IOBuffer`.
 */
class IOBuffer: PointerType {

    companion object {
        private val STRUCT = JuliaStruct("IOBuffer", JuliaStruct.Location.BASE)

        private val OFFSET_data: Long by STRUCT.field("data")
        private val OFFSET_size: Long by STRUCT.field("size")
    }

    private val jl: Julia

    /**
     * Creates a new IOBuffer. The object is not rooted.
     */
    constructor(jl: Julia) {
        this.jl = jl
        val buf_type = jl.getBaseObj("IOBuffer")
        val buf_obj = jl.jl_call0(buf_type)
        jl.exceptionCheck()
        pointer = buf_obj!!
    }

    /**
     * Wraps an IOBuffer pointer.
     */
    constructor(jl: Julia, ptr: jl_value_t) {
        this.jl = jl
        pointer = ptr
    }

    val size: Long
        get() = pointer.getLong(OFFSET_size)

    val isEmpty: Boolean
        get() = size == 0L

    val string: String
        get() {
            // Get the data of the IOBuffer without any allocation on the Julia side
            val bytes = pointer
                .getPointer(OFFSET_data)  // IOBuffer.data     => UInt8[] (aka jl_array_t*)
                .getPointer(0)            // jl_array_t->data  => char*
                .getByteArray(0, size.toInt())
            return String(bytes)  // Accepts UTF-8
        }

    fun clear() {
        val truncate = jl.getBaseObj("truncate")
        val zero = jl.jl_box_int32(0)
        jl.jl_call2(truncate, pointer, zero)
    }

    fun getStringAndClear(): String {
        val str = string
        clear()
        return str
    }
}