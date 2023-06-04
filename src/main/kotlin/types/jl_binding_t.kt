package com.keluaa.juinko.types

import com.keluaa.juinko.jl_module_t
import com.keluaa.juinko.jl_sym_t
import com.keluaa.juinko.jl_value_t
import com.sun.jna.PointerType


/**
 * Wrapper around a `jl_binding_t` pointer. Note that this is not a `jl_value_t`, and cannot be used as such.
 */
class jl_binding_t: PointerType() {
    val name: jl_sym_t
        get() = pointer.share(0)

    val value: jl_value_t
        get() = pointer.share(8)

    val globalref: jl_value_t
        get() = pointer.share(16)

    val owner: jl_module_t
        get() = pointer.share(24)

    val type: jl_value_t
        get() = pointer.share(32)

    val constp: Boolean
        get() = (pointer.getByte(40).toUInt() and 0b00001u) != 0u

    val exportp: Boolean
        get() = (pointer.getByte(40).toUInt() and 0b00010u) != 0u

    val imported: Boolean
        get() = (pointer.getByte(40).toUInt() and 0b00100u) != 0u

    val deprecated: Byte
        get() = ((pointer.getByte(40).toUInt() and 0b11000u) shr 3).toByte()
}