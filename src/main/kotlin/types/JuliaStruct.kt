package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.*
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible


/**
 * Helper to create lazily computed field offsets using the corresponding Julia type.
 */
class JuliaStruct(private val structName: String, place: Location) {

    enum class Location {
        CORE, BASE, MAIN
    }

    companion object {
        internal lateinit var jl: Julia

        private fun getFieldNames(datatype: jl_datatype_ptr): jl_svec_t {
            // Equivalent to `jl_datatype_t(t).name.names`, but without using lazy offsets
            val nameOffset = 0L    // jl_datatype_t.OFFSET_name
            val namesOffset = 16L  // jl_typename_t.OFFSET_names
            return datatype.getPointer(nameOffset).getPointer(namesOffset)
        }

        fun getFields(jl: Julia, datatype: jl_datatype_ptr): Map<String, Long> {
            val names = getFieldNames(datatype)
            val count = jl.jl_svec_len(names)

            val structInfo = mutableMapOf<String, Long>()

            for (i in 0 until count) {
                val offset = jl.jl_get_field_offset(datatype, i.toInt() + 1)
                val nameSymbol = jl.jl_svecref(names, i)
                val name = jl.jl_symbol_name(nameSymbol)
                structInfo[name] = offset
            }

            return structInfo
        }
    }

    private var datatype: jl_datatype_t
    private var fields: Map<String, Long>

    init {
        this.datatype = jl_datatype_t(when (place) {
            Location.CORE -> jl.getCoreObj(structName)
            Location.BASE -> jl.getBaseObj(structName)
            Location.MAIN -> jl.getMainObj(structName)
        })
        fields = getFields(jl, datatype.asPtr())
    }

    fun field(name: String): Lazy<Long> = lazy {
        fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
    }

    fun offset(name: String, offset: Int): Lazy<Long> = lazy {
        val other = fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
        other + offset
    }

    fun offset(other: KProperty0<*>, offset: Int): Lazy<Long> = lazy {
        other.isAccessible = true
        val otherVal = (other.getDelegate() as Lazy<*>).value
        (otherVal as Long) + offset
    }

    fun before(name: String, version: JuliaVersion): Lazy<Long> = lazy {
        if (JuliaVersion < version)
            fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
        else
            throw VersionException("<", version, "Type $structName has the field '$name' only before version $version")
    }

    fun after(name: String, version: JuliaVersion): Lazy<Long> = lazy {
        if (JuliaVersion > version)
            fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
        else
            throw VersionException(">", version, "Type $structName has the field '$name' only after version $version")
    }

    fun from(name: String, version: JuliaVersion): Lazy<Long> = lazy {
        if (JuliaVersion >= version)
            fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
        else
            throw VersionException(">=", version, "Type $structName has the field '$name' starting from version $version")
    }

    fun until(name: String, version: JuliaVersion): Lazy<Long> = lazy {
        if (JuliaVersion <= version)
            fields[name] ?: throw NoSuchFieldException("Julia type $structName doesn't have a field named '$name'")
        else
            throw VersionException("<=", version, "Type $structName has the field '$name' until version $version")
    }

    fun custom(f: (Julia, jl_datatype_t) -> Long): Lazy<Long> = lazy { f(jl, datatype) }
}