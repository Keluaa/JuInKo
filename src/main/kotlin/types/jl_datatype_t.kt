package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.*
import com.sun.jna.Pointer
import com.sun.jna.PointerType

class jl_datatype_t(p: Pointer?) : PointerType(p) {

    constructor() : this(Pointer.NULL)

    companion object {
        private val STRUCT = JuliaStruct("DataType", JuliaStruct.Location.CORE)

        private val OFFSET_name:       Long by STRUCT.field("name")
        private val OFFSET_super:      Long by STRUCT.field("super")
        private val OFFSET_parameters: Long by STRUCT.field("parameters")
        private val OFFSET_types:      Long by STRUCT.field("types")
        private val OFFSET_instance:   Long by STRUCT.field("instance")
        private val OFFSET_layout:     Long by STRUCT.field("layout")
        private val OFFSET_size:       Long by STRUCT.before("size", JuliaVersion(1, 9, 0))
        private val OFFSET_hash:       Long by STRUCT.field("hash")
        private val OFFSET_flags:      Long by STRUCT.field("flags")
    }

    val name
        get() = jl_typename_t(pointer.getPointer(OFFSET_name))

    val superType: jl_datatype_t
        get() = jl_datatype_t(pointer.getPointer(OFFSET_super))

    val parameters: jl_svec_t
        get() = pointer.getPointer(OFFSET_parameters)

    val types: jl_svec_t
        get() = pointer.getPointer(OFFSET_types)

    val instance: jl_value_t
        get() = pointer.getPointer(OFFSET_instance)

    val layout: Pointer // TODO: wrapper for jl_datatype_layout_t
        get() = pointer.getPointer(OFFSET_layout)

    val size: Int
        get() {
            return if (JuliaVersion < JuliaVersion(1, 9, 0)) {
                pointer.getInt(OFFSET_size)
            } else {
                pointer.getPointer(OFFSET_layout).getInt(0)
            }
        }

    val hash: UInt
        get() = pointer.getInt(OFFSET_hash).toUInt()

    val hasFreeTypeVars: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00000001) != 0 // majority part of isconcrete computation

    val isConcreteType: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00000010) != 0 // whether this type can have instances

    val isDispatchTuple: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00000100) != 0 // aka isleaftupletype

    val isBitsType: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00001000) != 0 // relevant query for C-api and type-parameters

    val zeroInit: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00010000) != 0 // if one or more fields requires zero-initialization

    val hasConcreteSubtype: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b00100000) != 0 // If clear, no value will have this datatype

    val cachedByHash: Boolean
        get() = (pointer.getByte(OFFSET_flags).toInt() and 0b01000000) != 0 // stored in hash-based set cache (instead of linear cache)

    fun asPtr(): jl_datatype_ptr = pointer
    fun asVal(): jl_value_t = pointer
}