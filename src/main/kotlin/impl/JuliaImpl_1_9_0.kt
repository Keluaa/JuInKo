package com.github.keluaa.juinko.impl

import com.github.keluaa.juinko.*
import com.sun.jna.Native
import com.sun.jna.Pointer

class JuliaImpl_1_9_0: JuliaImplBase() {
    companion object {
        init {
            Native.register(JuliaPath.LIB_JULIA)
        }
    }

    external override fun jl_init()
    external override fun jl_is_initialized(): Int

    external override fun jl_adopt_thread(): jl_gcframe_ptr

    external override fun jl_box_bool(x: Byte): jl_value_t
    external override fun jl_box_int8(x: Byte): jl_value_t
    external override fun jl_box_uint8(x: Byte): jl_value_t
    external override fun jl_box_int16(x: Short): jl_value_t
    external fun jl_box_uint16(x: Short): jl_value_t
    override fun jl_box_uint16(x: UShort): jl_value_t = jl_box_uint16(x.toShort())
    external override fun jl_box_int32(x: Int): jl_value_t
    external fun jl_box_uint32(x: Int): jl_value_t
    override fun jl_box_uint32(x: UInt): jl_value_t = jl_box_uint32(x.toInt())
    external fun jl_box_char(x: Int): jl_value_t
    override fun jl_box_char(x: UInt): jl_value_t = jl_box_char(x.toInt())
    external override fun jl_box_int64(x: Long): jl_value_t
    external fun jl_box_uint64(x: Long): jl_value_t
    override fun jl_box_uint64(x: ULong): jl_value_t = jl_box_uint64(x.toLong())
    external override fun jl_box_float32(x: Float): jl_value_t
    external override fun jl_box_float64(x: Double): jl_value_t
    external override fun jl_box_voidpointer(x: Pointer): jl_value_t
    external override fun jl_box_uint8pointer(x: Pointer): jl_value_t
    external override fun jl_box_ssavalue(x: Long): jl_value_t
    external override fun jl_box_slotnumber(x: Long): jl_value_t

    external override fun jl_unbox_int8(x: jl_value_t): Byte
    external override fun jl_unbox_uint8(x: jl_value_t): Byte
    external override fun jl_unbox_int16(x: jl_value_t): Short
    external fun jl_unbox_uint16(x: jl_value_t): Short
    override fun jl_unbox_uint16_(x: jl_value_t): UShort = jl_unbox_uint16(x).toUShort()
    external override fun jl_unbox_int32(x: jl_value_t): Int
    external fun jl_unbox_uint32(x: jl_value_t): Int
    override fun jl_unbox_uint32_(x: jl_value_t): UInt = jl_unbox_uint32(x).toUInt()
    external override fun jl_unbox_int64(x: jl_value_t): Long
    external fun jl_unbox_uint64(x: jl_value_t): Long
    override fun jl_unbox_uint64_(x: jl_value_t): ULong = jl_unbox_uint64(x).toULong()
    external override fun jl_unbox_float32(x: jl_value_t): Float
    external override fun jl_unbox_float64(x: jl_value_t): Double
    external override fun jl_unbox_voidpointer(x: jl_value_t): Pointer
    external override fun jl_unbox_uint8pointer(x: jl_value_t): Pointer

    external override fun jl_field_index(t: jl_datatype_ptr, fld: jl_sym_t, err: Int): Int

    external override fun jl_new_array(atype: jl_value_t, dims: jl_value_t): jl_array_t
    external override fun jl_alloc_array_1d(atype: jl_value_t, nr: Long): jl_array_t
    external override fun jl_alloc_array_2d(atype: jl_value_t, nr: Long, nc: Long): jl_array_t
    external override fun jl_alloc_array_3d(atype: jl_value_t, nr: Long, nc: Long, z: Long): jl_array_t
    external override fun jl_pchar_to_array(char: String, len: Long): jl_array_t
    external override fun jl_pchar_to_string(char: String, len: Long): jl_value_t
    external override fun jl_cstr_to_string(char: String): jl_value_t
    external override fun jl_alloc_vec_any(n: Long): jl_array_t
    external override fun jl_arrayref(a: jl_array_t, i: Long): jl_value_t
    external override fun jl_ptrarrayref(a: jl_array_t, i: Long): jl_value_t
    external override fun jl_arrayset(a: jl_array_t, v: jl_value_t, i: Long)
    external override fun jl_arrayunset(a: jl_array_t, i: Long)
    external override fun jl_array_isassigned(a: jl_array_t, i: Long): Int

    external override fun jl_string_ptr(s: jl_value_t): String

    external override fun jl_get_binding(m: jl_module_t, name: jl_sym_t): jl_binding_t?
    external override fun jl_get_binding_or_error(m: jl_module_t, name: jl_sym_t): jl_binding_t
    external override fun jl_get_binding_wr(m: jl_module_t, name: jl_sym_t, alloc: Int): jl_binding_t?
    external override fun jl_get_binding_for_method_def(m: jl_module_t, name: jl_sym_t): jl_binding_t
    external override fun jl_boundp(m: jl_module_t, name: jl_sym_t): Int

    external fun jl_checked_assignment(b: jl_binding_t, rhs: jl_value_t)
    override fun jl_checked_assignment(b: jl_binding_t, mod: jl_module_t, v: jl_sym_t, rhs: jl_value_t) = jl_checked_assignment(b, rhs)

    external fun jl_declare_constant(b: jl_binding_t)
    override fun jl_declare_constant(b: jl_binding_t, mod: jl_module_t, v: jl_sym_t) = jl_declare_constant(b)

    external override fun jl_eval_string(str: String): jl_value_t?
    external override fun jl_load_file_string(text: String, len: Long, filename: String, module: jl_module_t): jl_value_t?

    external override fun jl_gc_enable(on: Int)
    external override fun jl_gc_is_enabled(): Int
    external override fun jl_gc_collect(mode: Int)
    external override fun jl_gc_safepoint()

    external override fun jl_typename_str(v: jl_value_t): String
    external override fun jl_typeof_str(v: jl_value_t): String

    external override fun jl_apply_type(type: jl_value_t, params: jl_value_t_array, n: Long): jl_value_t?
    external override fun jl_apply_type1(type: jl_value_t, p1: jl_value_t): jl_value_t?
    external override fun jl_apply_type2(type: jl_value_t, p1: jl_value_t, p2: jl_value_t): jl_value_t?

    external override fun jl_apply_tuple_type(params: jl_svec_t): jl_tupletype_t
    external override fun jl_apply_tuple_type_v(p: jl_value_t_array, np: Long): jl_tupletype_t

    external override fun jl_symbol(str: String): jl_sym_t
    external override fun jl_get_global(module: jl_module_t, var_symbol: jl_sym_t): jl_value_t?

    external override fun jl_errno(): Int
    external override fun jl_set_errno(e: Int)

    external override fun jl_current_exception(): jl_value_t
    external override fun jl_exception_occurred(): jl_value_t?
    external override fun jl_exception_clear()

    external override fun jl_invoke_api(linfo: jl_code_instance_t): Int

    external override fun jl_call(f: jl_function_t, args: jl_value_t_array, nargs: Int): jl_value_t?
    external override fun jl_call0(f: jl_function_t): jl_value_t?
    external override fun jl_call1(f: jl_function_t, a: jl_value_t): jl_value_t?
    external override fun jl_call2(f: jl_function_t, a: jl_value_t, b: jl_value_t): jl_value_t?
    external override fun jl_call3(f: jl_function_t, a: jl_value_t, b: jl_value_t, c: jl_value_t): jl_value_t?

    external override fun jl_get_pgcstack(): jl_gcframe_ptr

    external override fun jl_stdout_stream(): JL_STREAM
    external override fun jl_stdin_stream(): JL_STREAM
    external override fun jl_stderr_stream(): JL_STREAM

    external override fun jl_stdout_obj(): jl_value_t
    external override fun jl_stderr_obj(): jl_value_t

    external override fun jl_static_show(out: JL_STREAM, v: jl_value_t): Long
    external override fun jl_static_show_func_sig(s: JL_STREAM, type: jl_value_t): Long

    external override fun jl_ver_major(): Int
    external override fun jl_ver_minor(): Int
    external override fun jl_ver_patch(): Int
    external override fun jl_ver_is_release(): Int
    external override fun jl_ver_string(): String

    external override fun jl_array_ptr(a: jl_array_t): Pointer
    external override fun jl_array_eltype(a: jl_value_t): jl_value_t
    external override fun jl_array_rank(a: jl_value_t): Int
    external override fun jl_array_size(a: jl_value_t, d: Int): Long

    external override fun jl_threadid(): Short

    external override fun jl_module_name(m: jl_module_t): jl_sym_t
    external override fun jl_module_parent(m: jl_module_t): jl_module_t

    external override fun jl_alignment(sz: Long): Int

    external override fun jl_get_backtrace(): jl_value_t

    private class Internal {
        companion object {
            init {
                Native.register(JuliaPath.LIB_JULIA_INTERNAL)
            }
        }

        external fun jl_flush_cstdio()

        external fun jl_compile_method_internal(@PropagatesRoot meth: jl_method_instance_t, world: Long): jl_code_instance_t

        external fun jl_method_compiled(@PropagatesRoot mi: jl_method_instance_t, world: Long): jl_code_instance_t

        external fun jl_lock_value(v: jl_value_t)
        external fun jl_unlock_value(v: jl_value_t)

        external fun jl_sizeof_jl_options(): Long

        external fun jl_breakpoint(v: jl_value_t)

        // Moved to julia-internal in 1.9

        external fun jl_symbol_name(s: jl_sym_t): String

        external fun jl_astaggedvalue(v: jl_value_t): jl_taggedvalue_t
        external fun jl_typeof(v: jl_value_t): jl_value_t
        external fun jl_valueof(v: jl_taggedvalue_t): jl_value_t

        external fun jl_get_field_offset(ty: jl_datatype_ptr, field: Int): Long
        external fun jl_get_fieldtypes(v: jl_value_t): jl_value_t

        external fun jl_cpu_pause()
        external fun jl_cpu_wake()

        external fun jl_gc_unsafe_enter(): Byte
        external fun jl_gc_unsafe_leave(state: Byte)
    }

    private val internal = Internal()

    override fun jl_flush_cstdio() = internal.jl_flush_cstdio()

    override fun jl_compile_method_internal(meth: jl_method_instance_t, world: Long) = internal.jl_compile_method_internal(meth, world)

    override fun jl_method_compiled(@PropagatesRoot mi: jl_method_instance_t, world: Long) = internal.jl_method_compiled(mi, world)

    override fun jl_lock_value(v: jl_value_t) = internal.jl_lock_value(v)
    override fun jl_unlock_value(v: jl_value_t) = internal.jl_unlock_value(v)

    override fun jl_sizeof_jl_options() = internal.jl_sizeof_jl_options()

    override fun jl_breakpoint(v: jl_value_t) = internal.jl_breakpoint(v)

    override fun jl_symbol_name(s: jl_sym_t) = internal.jl_symbol_name(s)

    override fun jl_astaggedvalue(v: jl_value_t) = internal.jl_astaggedvalue(v)
    override fun jl_typeof(v: jl_value_t) = internal.jl_typeof(v)
    override fun jl_valueof(v: jl_taggedvalue_t) = internal.jl_valueof(v)

    override fun jl_get_field_offset(ty: jl_datatype_ptr, field: Int) = internal.jl_get_field_offset(ty, field)
    override fun jl_get_fieldtypes(v: jl_value_t) = internal.jl_get_fieldtypes(v)

    override fun jl_cpu_pause() = internal.jl_cpu_pause()
    override fun jl_cpu_wake() = internal.jl_cpu_wake()

    override fun jl_gc_unsafe_enter() = internal.jl_gc_unsafe_enter()
    override fun jl_gc_unsafe_leave(state: Byte) = internal.jl_gc_unsafe_leave(state)
}