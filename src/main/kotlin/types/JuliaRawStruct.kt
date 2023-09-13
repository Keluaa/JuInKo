package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.JuliaException

/**
 * Helper to compute field offsets manually from hardcoded field sizes.
 *
 * To be used by structs with no Julia datatype.
 */
class JuliaRawStruct(val fields: Map<String, Long>, val size: Long) {

    class RawField(val name: String, val size: Int, val present: Boolean = true)

    companion object {
        fun alignTo(offset: Long, size: Int): Long {
            if (size == 0)
                return offset
            if (offset % size == 0L)
                return offset
            return offset + size - (offset % size)
        }

        fun build(vararg fields: RawField): JuliaRawStruct {
            val fieldMap = mutableMapOf<String, Long>()
            var offset = 0L
            for (field in fields) {
                if (!field.present) {
                    fieldMap[field.name] = Long.MIN_VALUE
                    continue
                }
                offset = alignTo(offset, field.size)
                fieldMap[field.name] = offset
                offset += field.size
            }
            return JuliaRawStruct(fieldMap, offset)
        }
    }

    operator fun get(field: String): Long {
        val offset = fields[field] ?: throw NoSuchElementException(field)
        if (offset == Long.MIN_VALUE)
            throw JuliaException("Field '$field' is unavailable in this version")
        return offset
    }
}