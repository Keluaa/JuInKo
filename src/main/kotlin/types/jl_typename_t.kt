package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.*
import com.sun.jna.Pointer
import com.sun.jna.PointerType

class jl_typename_t(p: Pointer?) : PointerType(p) {

    constructor() : this(Pointer.NULL)

    companion object {
        private val STRUCT = JuliaStruct("TypeName", JuliaStruct.Location.CORE)

        private val OFFSET_name:            Long by STRUCT.field("name")
        private val OFFSET_module:          Long by STRUCT.field("module")
        private val OFFSET_names:           Long by STRUCT.field("names")
        private val OFFSET_atomicfields:    Long by STRUCT.field("atomicfields")
        private val OFFSET_constfields:     Long by STRUCT.from("constfields", JuliaVersion("1.8.0-beta1"))
        private val OFFSET_wrapper:         Long by STRUCT.field("wrapper")
        private val OFFSET_Typeofwrapper:   Long by STRUCT.from("Typeofwrapper", JuliaVersion("1.9.0-alpha1"))
        private val OFFSET_cache:           Long by STRUCT.field("cache")
        private val OFFSET_linearcache:     Long by STRUCT.field("linearcache")
        private val OFFSET_mt:              Long by STRUCT.field("mt")
        private val OFFSET_partial:         Long by STRUCT.field("partial")
        private val OFFSET_hash:            Long by STRUCT.field("hash")
        private val OFFSET_n_uninitialized: Long by STRUCT.field("n_uninitialized")
        private val OFFSET_flags:           Long by STRUCT.field("flags")
    }

    val name: jl_sym_t
        get() = pointer.getPointer(OFFSET_name)

    val module: jl_module_t
        get() = pointer.getPointer(OFFSET_module)

    val names: jl_svec_t
        get() = pointer.getPointer(OFFSET_names)

    val atomicFields: Pointer
        get() = pointer.getPointer(OFFSET_atomicfields)

    val constFields: Pointer
        get() = pointer.getPointer(OFFSET_constfields)

    val wrapper: jl_value_t
        get() = pointer.getPointer(OFFSET_wrapper)

    val typeOfWrapper: jl_value_t
        get() = pointer.getPointer(OFFSET_Typeofwrapper)

    val cache: jl_value_t
        get() = pointer.getPointer(OFFSET_cache)

    val linearCache: jl_svec_t
        get() = pointer.getPointer(OFFSET_linearcache)

    val methodTable: jl_methtable_t
        get() = pointer.getPointer(OFFSET_mt)

    val partial: jl_array_t
        get() = pointer.getPointer(OFFSET_partial)

    val hash: Long
        get() = pointer.getLong(OFFSET_hash)

    val n_uninitialized: Int
        get() = pointer.getInt(OFFSET_n_uninitialized)

    /*private*/ val properties
        get() = pointer.getByte(OFFSET_flags).toInt()

    val abstract
        get() = (properties and 0b001) != 0

    val mutable
        get() = (properties and 0b010) != 0

    val mayInlineAlloc
        get() = (properties and 0b100) != 0
}