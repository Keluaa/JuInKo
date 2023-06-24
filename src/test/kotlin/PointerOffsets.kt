package com.keluaa.juinko

import com.keluaa.juinko.impl.JuliaImplBase
import com.keluaa.juinko.types.*
import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal class PointerOffsets: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            initJulia()
            ensureImplConstantsNotInitialized()
            // Note: jl.exceptionCheck, jl.permMem and jl.errorBuffer use uninitialized variables and should not be used here
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
        checkAllOffsets(jl.getCoreObj("TypeName"), jl_typename_t.Companion)
    }

    @Test
    fun jl_datatype_t() {
        checkAllOffsets(jl.getCoreObj("DataType"), jl_datatype_t.Companion)
    }

    @Test
    fun jl_task_t() {
        checkAllOffsets(jl.getCoreObj("Task"), jl_task_t.Companion)

        if (JuliaVersion >= JuliaVersion(1, 9, 1)) {
            val jl_task_gcstack_offset = (jl as JuliaImplBase).getGlobal<Int>("jl_task_gcstack_offset").toLong()
            val jl_task_ptls_offset    = (jl as JuliaImplBase).getGlobal<Int>("jl_task_ptls_offset").toLong()
            Assertions.assertEquals(jl_task_gcstack_offset, jl_task_t.Companion.OFFSET_gcstack)
            Assertions.assertEquals(jl_task_ptls_offset, jl_task_t.Companion.OFFSET_ptls)
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