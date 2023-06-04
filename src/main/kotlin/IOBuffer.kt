package com.keluaa.juinko

import com.sun.jna.Pointer
import com.sun.jna.PointerType

class IOBuffer: PointerType {

    companion object {
        private const val SIZE_OFFSET = 0x10  // TODO: set with 'fieldoffset(IOBuffer, 6)' ?
    }

    private val jl: Julia

    constructor(jl: Julia) {
        this.jl = jl
        val buf_type = jl.getBaseObj("IOBuffer")
        val buf_obj = jl.jl_call0(buf_type)
        pointer = buf_obj
    }

    constructor(jl: Julia, ptr: Pointer?) {
        this.jl = jl
        pointer = ptr
    }

    val size: Long
        get() {
            return pointer.getLong(SIZE_OFFSET.toLong())
        }

    val isEmpty: Boolean
        get() = size == 0L

    val string: String
        get() {
            // Get the data of the IOBuffer without any allocation on the Julia side
            val bytes = pointer
                .getPointer(0) // IOBuffer.data     => UInt8[] (aka jl_array_t*)
                .getPointer(0) // jl_array_t->data  => char*
                .getByteArray(0, size.toInt())
            return String(bytes)
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