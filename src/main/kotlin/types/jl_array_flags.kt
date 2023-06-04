package com.keluaa.juinko.types

/**
 * Wrapper around the `jl_arrayflags_t` struct, composed only of bitfields.
 *
 * @see <a href="https://github.com/JuliaLang/julia/blob/master/src/julia.h#L155-L170">jl_arrayflags_t definition</a>
 */
class jl_array_flags(private var flags: UInt) {
    private fun mask(n: Int, o: Int) = (UInt.MAX_VALUE shr (UInt.SIZE_BITS - n)) shl o

    var value: UShort
        get() = flags.toUShort()
        set(v) { flags = v.toUInt() }

    var how: UInt
        get() = flags and mask(2, 0)
        set(v) { flags = flags or (v and mask(2, 0)) }

    var ndims: UInt
        get() = flags and mask(9, 2) shr 2
        set(v) { flags = flags or (v and mask(9, 2) shl 2) }

    var pooled: UInt
        get() = flags and mask(1, 11) shr 11
        set(v) { flags = flags or (v and mask(1, 11) shl 11) }

    var ptrarray: UInt
        get() = flags and mask(1, 12) shr 12
        set(v) { flags = flags or (v and mask(1, 12)) shl 12 }

    var hasptr: UInt
        get() = flags and mask(1, 13) shr 13
        set(v) { flags = flags or (v and mask(1, 13)) shl 13 }

    var isshared: UInt
        get() = flags and mask(1, 14) shr 14
        set(v) { flags = flags or (v and mask(1, 14)) shl 14 }

    var isaligned: UInt
        get() = flags and mask(1, 15) shr 15
        set(v) { flags = flags or (v and mask(1, 15)) shl 15 }
}