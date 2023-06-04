package com.keluaa.juinko

import com.keluaa.juinko.impl.JuliaLoader
import com.keluaa.juinko.types.JuliaOptions
import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal class PointerOffsets {

    companion object {
        private lateinit var jl: Julia

        @BeforeAll
        @JvmStatic
        fun setUp() {
            jl = JuliaLoader.get(false)
            // Note: jl.exceptionCheck, jl.permMem and jl.errorBuffer are uninitialized and should not be used here
        }
    }

    private fun <K : Any> getOffsetFields(k: K, klass: KClass<K>): Map<String, Long> {
        return klass.memberProperties
            .filter { it.name.startsWith("OFFSET_") }
            .map {
                it.isAccessible = true
                val name = it.name.substring("OFFSET_".length)
                try {
                    val value = it.get(k) as Long
                    name to value
                } catch (e: InvocationTargetException) {
                    // Ignore fields inaccessible because of a Julia version mismatch
                    if (e.cause is VersionException) null
                    else throw e
                }
            }
            .filterNotNull()
            .toMap()
    }

    private fun buildStructInfoArray(datatype: jl_datatype_ptr): List<Pair<String, Long>> {
        val count = jl.getFieldCount(datatype)
        val names = jl.jl_field_names(datatype)

        val structInfoList = mutableListOf<Pair<String, Long>>()

        for (i in 0 until count) {
            val offset = jl.jl_get_field_offset(datatype, i.toInt() + 1)
            val nameSymbol = jl.jl_svecref(names, i)
            val name = jl.jl_symbol_name(nameSymbol)
            structInfoList.add(name to offset)
        }

        return structInfoList
    }

    private fun compareOffsets(juliaOffsets: List<Pair<String, Long>>, ourOffsets: Map<String, Long>) {
        val arg = mutableMapOf<String, Long>()
        arg.putAll(ourOffsets)
        var failCount = 0
        for ((name, offset) in juliaOffsets) {
            val ourOffset = ourOffsets[name] ?: continue
            if (offset != ourOffset) {
                println("Field $name:\n" +
                    "Expected: 0x${offset.toString(16)} ($offset)\n" +
                    "Got:      0x${ourOffset.toString(16)} ($ourOffset)\n")
                failCount++
            }
            arg.remove(name)
        }
        Assertions.assertEquals(0, failCount)
        if (arg.isNotEmpty()) {
            println("Untested fields:")
            arg.forEach { println(" - " + it.key) }
        }
    }

    private inline fun <reified K : Any> checkAllOffsets(datatype: jl_value_t, k: K) {
        val structInfoList = buildStructInfoArray(datatype)
        val offsets = getOffsetFields(k, K::class)
        Assertions.assertNotEquals(0, offsets.size)
        compareOffsets(structInfoList, offsets)
    }

    @Test
    fun jl_typename_t() {
        checkAllOffsets(jl.getCoreObj("TypeName"), com.keluaa.juinko.types.jl_typename_t.Companion)
    }

    @Test
    fun jl_datatype_t() {
        checkAllOffsets(jl.getCoreObj("DataType"), com.keluaa.juinko.types.jl_datatype_t.Companion)
    }

    @Test
    fun jl_task_t() {
        checkAllOffsets(jl.getCoreObj("Task"), com.keluaa.juinko.types.jl_task_t.Companion)

        if (JuliaVersion >= JuliaVersion(1, 9, 1)) {
            // TODO: use the new exported globals to check the pointers. However they are only set AFTER 'jl_init' has been called
            //    jl_task_gcstack_offset = offsetof(jl_task_t, gcstack);
            //    jl_task_ptls_offset = offsetof(jl_task_t, ptls);
            //  https://github.com/JuliaLang/julia/commit/c3d84e42aaf65c4ea7ff390ff0a509da5fd9583d
        }

        val pgcstack = jl.jl_get_pgcstack()
        val task = jl.jl_current_task()
        Assertions.assertEquals(pgcstack, task.gcstack)

        val currentTaskJlPtr = jl.jl_eval_string("pointer_from_objref(current_task())")!!
        val currentTaskPtr = jl.jl_unbox_voidpointer(currentTaskJlPtr)
        Assertions.assertEquals(currentTaskPtr, task.pointer)
    }

    @Test
    fun jl_options_t() {
        val options = JuliaOptions(Pointer(0))
        checkAllOffsets(jl.getBaseObj("JLOptions"), options)

        val ourOptionsSize = options.structSize
        val jlOptionsSize = jl.jl_sizeof_jl_options()
        Assertions.assertEquals(jlOptionsSize, ourOptionsSize)
    }
}