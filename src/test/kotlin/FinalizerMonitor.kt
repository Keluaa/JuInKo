package com.keluaa.juinko

import com.sun.jna.Pointer
import org.intellij.lang.annotations.Language


/**
 * Using finalizers, tracks which mutable value was GCed last.
 */
class FinalizerMonitor(private val jl: Julia) {

    companion object {
        @Language("Julia")
        private val FINALIZER_FUNC_SOURCE = """
            function set_finalizer_monitor(v)
                if ismutable(v)
                    global _finalizer_monitor = pointer_from_objref(v)
                end
            end    
        """.trimIndent()
    }

    private val monitorName = jl.jl_symbol("_finalizer_monitor")
    private val finalizerFunc = jl.jl_eval_string(FINALIZER_FUNC_SOURCE)!!

    fun reset() {
        val binding = jl.jl_get_binding_wr(jl.jl_main_module(), monitorName, 1)!!
        val boxedNull = jl.jl_box_voidpointer(Pointer.createConstant(0))
        jl.jl_checked_assignment(binding, jl.jl_main_module(), monitorName, boxedNull)
        jl.exceptionCheck()
    }

    fun track(obj: jl_value_t) {
        val finalizer = jl.getBaseObj("finalizer")
        jl.jl_call2(finalizer, finalizerFunc, obj)!!
    }

    fun track(obj: RefValue) = track(obj.pointer)

    fun getLast(): Pointer {
        val finalized = jl.jl_get_global(jl.jl_main_module(), monitorName)!!
        return jl.jl_unbox_voidpointer(finalized)
    }
}