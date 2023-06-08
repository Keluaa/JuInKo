@file:Suppress("FunctionName")

package com.keluaa.juinko

import com.keluaa.juinko.types.jl_array_flags
import com.keluaa.juinko.types.jl_binding_t
import com.keluaa.juinko.types.jl_datatype_t
import com.keluaa.juinko.types.jl_task_t
import com.sun.jna.Library
import com.sun.jna.Pointer

typealias jl_value_t = Pointer
typealias jl_value_t_array = Pointer  /* jl_value_t** */
typealias jl_array_t = Pointer
typealias jl_sym_t = Pointer
typealias jl_module_t = Pointer
typealias jl_function_t = Pointer
typealias jl_gcframe_ptr = Pointer  /* jl_gcframe_t** */
typealias jl_taggedvalue_t = Pointer
typealias jl_svec_t = Pointer
typealias jl_tupletype_t = Pointer
typealias jl_datatype_ptr = Pointer  /* jl_datatype_t, but as a Pointer object */
typealias jl_methtable_t = Pointer
typealias JL_STREAM = Pointer  /* JL_STREAM, or 'struct uv_stream_s*'. NOT A 'jl_value_t*' !! */

/* Internal Julia types */
typealias jl_method_t = Pointer
typealias jl_method_instance_t = Pointer
typealias jl_code_instance_t = Pointer

/**
 * Equivalent to [JL_NOTSAFEPOINT](https://docs.julialang.org/en/v1/devdocs/gc-sa/#JL_NOTSAFEPOINT)
 *
 * Marks an internal Julia function as not a safe point for the Garbage Collector, implying that the GC will not run
 * during the function execution.
 */
annotation class NotSafePoint

/**
 * Equivalent to [JL_GLOBALLY_ROOTED](https://docs.julialang.org/en/v1/devdocs/gc-sa/#JL_GLOBALLY_ROOTED)
 */
annotation class GloballyRooted

/**
 * Equivalent to [JL_MAYBE_UNROOTED](https://docs.julialang.org/en/v1/devdocs/gc-sa/#JL_MAYBE_UNROOTED/JL_ROOTS_TEMPORARILY)
 */
annotation class MaybeUnrooted

/**
 * Equivalent to [JL_PROPAGATES_ROOT](https://docs.julialang.org/en/v1/devdocs/gc-sa/#JL_PROPAGATES_ROOT)
 */
annotation class PropagatesRoot

/**
 * Equivalent to [JL_ROOTING_ARGUMENT and JL_ROOTED_ARGUMENT](https://docs.julialang.org/en/v1/devdocs/gc-sa/#JL_ROOTING_ARGUMENT/JL_ROOTED_ARGUMENT)
 */
annotation class RootingArgument
annotation class RootedArgument


interface Julia {
    fun jl_init()
    fun jl_is_initialized(): Int

    fun jl_adopt_thread(): jl_gcframe_ptr

    @NotSafePoint fun jl_box_bool(x: Byte): jl_value_t
    @NotSafePoint fun jl_box_int8(x: Byte): jl_value_t
    @NotSafePoint fun jl_box_uint8(x: Byte): jl_value_t
    fun jl_box_int16(x: Short): jl_value_t
    fun jl_box_uint16(x: UShort): jl_value_t
    fun jl_box_int32(x: Int): jl_value_t
    fun jl_box_uint32(x: UInt): jl_value_t
    fun jl_box_char(x: UInt): jl_value_t
    fun jl_box_int64(x: Long): jl_value_t
    fun jl_box_uint64(x: ULong): jl_value_t
    fun jl_box_float32(x: Float): jl_value_t
    fun jl_box_float64(x: Double): jl_value_t
    fun jl_box_voidpointer(x: Pointer): jl_value_t
    fun jl_box_uint8pointer(x: Pointer): jl_value_t
    fun jl_box_ssavalue(x: Long): jl_value_t
    fun jl_box_slotnumber(x: Long): jl_value_t

    @NotSafePoint fun jl_unbox_int8(x: jl_value_t): Byte
    @NotSafePoint fun jl_unbox_uint8(x: jl_value_t): Byte
    @NotSafePoint fun jl_unbox_int16(x: jl_value_t): Short
    @NotSafePoint fun jl_unbox_uint16_(x: jl_value_t): UShort
    @NotSafePoint fun jl_unbox_int32(x: jl_value_t): Int
    @NotSafePoint fun jl_unbox_uint32_(x: jl_value_t): UInt
    @NotSafePoint fun jl_unbox_int64(x: jl_value_t): Long
    @NotSafePoint fun jl_unbox_uint64_(x: jl_value_t): ULong
    @NotSafePoint fun jl_unbox_float32(x: jl_value_t): Float
    @NotSafePoint fun jl_unbox_float64(x: jl_value_t): Double
    @NotSafePoint fun jl_unbox_voidpointer(x: jl_value_t): Pointer
    @NotSafePoint fun jl_unbox_uint8pointer(x: jl_value_t): Pointer

    fun jl_field_index(t: jl_datatype_ptr, fld: jl_sym_t, err: Int): Int  /* if err==0 and 'fld' is not in 't', returns -1 */

    fun jl_new_array(atype: jl_value_t, dims: jl_value_t): jl_array_t
    fun jl_alloc_array_1d(atype: jl_value_t, nr: Long): jl_array_t
    fun jl_alloc_array_2d(atype: jl_value_t, nr: Long, nc: Long): jl_array_t
    fun jl_alloc_array_3d(atype: jl_value_t, nr: Long, nc: Long, z: Long): jl_array_t
    fun jl_pchar_to_array(char: String, len: Long): jl_array_t
    fun jl_pchar_to_string(char: String, len: Long): jl_value_t
    fun jl_cstr_to_string(char: String): jl_value_t
    fun jl_alloc_vec_any(n: Long): jl_array_t
    fun jl_arrayref(a: jl_array_t, i: Long): jl_value_t  // 0-indexed
    @NotSafePoint fun jl_ptrarrayref(@PropagatesRoot a: jl_array_t, i: Long): jl_value_t  // 0-indexed
    fun jl_arrayset(@RootingArgument a: jl_array_t, @RootedArgument @MaybeUnrooted v: jl_value_t, i: Long)  // 0-indexed
    fun jl_arrayunset(a: jl_array_t, i: Long)  // 0-indexed
    fun jl_array_isassigned(a: jl_array_t, i: Long): Int  // 0-indexed
    fun jl_array_ptr(a: jl_array_t): Pointer
    fun jl_array_eltype(a: jl_value_t): Pointer
    fun jl_array_rank(a: jl_value_t): Int
    fun jl_array_size(a: jl_value_t, d: Int): Long

    fun jl_string_ptr(s: jl_value_t): String

    fun jl_get_binding(@PropagatesRoot m: jl_module_t, name: jl_sym_t): jl_binding_t?
    fun jl_get_binding_or_error( @PropagatesRoot m: jl_module_t, name: jl_sym_t): jl_binding_t
    fun jl_get_binding_wr(@PropagatesRoot m: jl_module_t, name: jl_sym_t, alloc: Int): jl_binding_t?
//    fun jl_get_binding_wr_or_error(@PropagatesRoot m: jl_module_t, name: jl_sym_t): jl_binding_t
    fun jl_get_binding_for_method_def(@PropagatesRoot m: jl_module_t, name: jl_sym_t): jl_binding_t
    fun jl_boundp(m: jl_module_t, name: jl_sym_t): Int  /* Equivalent to `isdefined(m, name)` */

    fun jl_checked_assignment(b: jl_binding_t, @MaybeUnrooted rhs: jl_value_t)
    fun jl_declare_constant(b: jl_binding_t)

    fun jl_eval_string(str: String): jl_value_t?
    fun jl_load_file_string(text: String, len: Long, filename: String, module: jl_module_t): jl_value_t?

    fun jl_gc_enable(on: Int)
    fun jl_gc_is_enabled(): Int

    companion object {
        // Modes for 'jl_gc_collect'
        const val JL_GC_AUTO: Int = 0         // use heuristics to determine the collection type
        const val JL_GC_FULL: Int = 1         // force a full collection
        const val JL_GC_INCREMENTAL: Int = 2  // force an incremental collection
    }

    fun jl_gc_collect(mode: Int)
    fun jl_gc_safepoint()

    @NotSafePoint fun jl_symbol_name(s: jl_sym_t): String

    @NotSafePoint fun jl_typename_str(v: jl_value_t): String
    @NotSafePoint fun jl_typeof_str(v: jl_value_t): String

    fun jl_apply_type(type: jl_value_t, params: jl_value_t_array, n: Long): jl_value_t?
    fun jl_apply_type1(type: jl_value_t, p1: jl_value_t): jl_value_t?
    fun jl_apply_type2(type: jl_value_t, p1: jl_value_t, p2: jl_value_t): jl_value_t?

    fun jl_apply_tuple_type(params: jl_svec_t): jl_tupletype_t
    fun jl_apply_tuple_type_v(p: jl_value_t_array, np: Long): jl_tupletype_t

    @NotSafePoint fun jl_symbol(str: String): jl_sym_t
    fun jl_get_global(@PropagatesRoot module: jl_module_t, var_symbol: jl_sym_t): jl_value_t?

    @NotSafePoint fun jl_errno(): Int
    @NotSafePoint fun jl_set_errno(e: Int)

    @NotSafePoint fun jl_current_exception(): jl_value_t
    fun jl_exception_occurred(): jl_value_t?
    @NotSafePoint fun jl_exception_clear()

    @NotSafePoint fun jl_invoke_api(@MaybeUnrooted linfo: jl_code_instance_t): Int

    fun jl_call(@MaybeUnrooted f: jl_function_t, args: jl_value_t_array, nargs: Int): jl_value_t?
    fun jl_call0(@MaybeUnrooted f: jl_function_t): jl_value_t?
    fun jl_call1(@MaybeUnrooted f: jl_function_t, @MaybeUnrooted a: jl_value_t): jl_value_t?
    fun jl_call2(@MaybeUnrooted f: jl_function_t, @MaybeUnrooted a: jl_value_t,
                 @MaybeUnrooted b: jl_value_t): jl_value_t?
    fun jl_call3(@MaybeUnrooted f: jl_function_t, @MaybeUnrooted a: jl_value_t,
                 @MaybeUnrooted b: jl_value_t, @MaybeUnrooted c: jl_value_t): jl_value_t?

    @NotSafePoint @GloballyRooted fun jl_get_pgcstack(): jl_gcframe_ptr

    // Not sure why those functions are not marked as JL_NOTSAFEPOINT in julia.h
    @NotSafePoint fun jl_stdout_stream(): JL_STREAM
    @NotSafePoint fun jl_stdin_stream(): JL_STREAM
    @NotSafePoint fun jl_stderr_stream(): JL_STREAM

    @NotSafePoint fun jl_stdout_obj(): jl_value_t /* Technically jl_value_t?, but it shouldn't happen */
    @NotSafePoint fun jl_stderr_obj(): jl_value_t /* idem */

    @NotSafePoint fun jl_static_show(out: JL_STREAM, v: jl_value_t): Long
    @NotSafePoint fun jl_static_show_func_sig(s: JL_STREAM, type: jl_value_t): Long

    @NotSafePoint fun jl_ver_major(): Int
    @NotSafePoint fun jl_ver_minor(): Int
    @NotSafePoint fun jl_ver_patch(): Int
    @NotSafePoint fun jl_ver_is_release(): Int
    @NotSafePoint fun jl_ver_string(): String

    /*
     * Varargs functions
     *
     * JNA doesn't support vararg functions with direct mapping, therefore those are wrapped in a separate interface.
     */

    interface Varargs: Library {
        fun jl_new_struct(type: jl_datatype_ptr, vararg params: jl_value_t): jl_value_t

        @MaybeUnrooted fun jl_svec(n: Long, vararg params: jl_value_t): jl_svec_t

        // Dynamic C-style printf to the given stream (Note: it doesn't explicitly use the Julia GC, so maybe @NotSafePoint?)
        @NotSafePoint fun jl_printf(stream: JL_STREAM, format: String, vararg params: Any): Int

        // Writes up to 999 characters to stderr with standard C-style printf formatting
        @NotSafePoint fun jl_safe_printf(format: String, vararg params: Any)
    }

    val varargs: Varargs

    /*
     * Internal functions, defined in 'julia_internal.h' and stored in 'libjulia-internal'
     */

    fun jl_alignment(sz: Long): Int

    @NotSafePoint fun jl_flush_cstdio()

    fun jl_compile_method_internal(@PropagatesRoot meth: jl_method_instance_t, world: Long): jl_code_instance_t

    fun jl_method_compiled(@PropagatesRoot mi: jl_method_instance_t, world: Long): jl_code_instance_t

    fun jl_get_backtrace(): jl_value_t

    @NotSafePoint fun jl_lock_value(v: jl_value_t)
    @NotSafePoint fun jl_unlock_value(v: jl_value_t)

    @NotSafePoint fun jl_sizeof_jl_options(): Long

    /*
     * Macros exported in jlapi.c
     */

    @NotSafePoint fun jl_astaggedvalue(v: jl_value_t): jl_taggedvalue_t
    @NotSafePoint fun jl_valueof(v: jl_taggedvalue_t): jl_value_t
    @NotSafePoint fun jl_typeof(v: jl_value_t): jl_value_t
    @NotSafePoint fun jl_get_fieldtypes(v: jl_value_t): jl_value_t

    @NotSafePoint fun jl_gc_unsafe_enter(): Byte
    @NotSafePoint fun jl_gc_unsafe_leave(state: Byte)

    @NotSafePoint fun jl_cpu_pause()
    @NotSafePoint fun jl_cpu_wake()

    /*
     * Macros/Non-exported functions re-definitions
     * Those are all implicitly JL_NOTSAFEPOINT
     */

    /*@NotSafePoint fun jl_astaggedvalue(v: jl_value_t): jl_taggedvalue_t {
        // ((jl_taggedvalue_t*)((char*)(v) - sizeof(jl_taggedvalue_t)))
        return v.share(-8)
    }

    @NotSafePoint fun jl_typeof(v: Pointer): jl_value_t {
        // ((jl_value_t*)(jl_astaggedvalue(v)->header & ~(uintptr_t)15))
        return Pointer(v.getLong(-8) and 15L.inv())
    }*/

    @NotSafePoint fun jl_set_typeof(v: jl_value_t, type: jl_value_t) {
        // jl_atomic_store_relaxed((_Atomic(jl_value_t*)*)&tag->type, (jl_value_t*)t);
        val tag = jl_astaggedvalue(v)
        tag.setPointer(0, type)
    }

    @NotSafePoint fun jl_typeis(v: jl_value_t, type: jl_value_t): Boolean {
        // (jl_typeof(v)==(jl_value_t*)(t))
        return jl_typeof(v) == type
    }

    @NotSafePoint fun jl_is_mutable(type: jl_value_t): Boolean {
        // (((jl_datatype_t*)t)->name->mutabl)
        return jl_datatype_t(type).name.mutable
    }

    @NotSafePoint fun jl_is_immutable(type: jl_value_t): Boolean {
        return !jl_is_mutable(type)
    }

    @NotSafePoint fun jl_current_task(): jl_task_t {
        // (container_of(jl_get_pgcstack(), jl_task_t, gcstack))
        return jl_task_t.from_pgcstack(jl_get_pgcstack())
    }

    @NotSafePoint fun jl_pgcstack(): jl_gcframe_ptr {
        // (jl_current_task->gcstack)
        // however since 'jl_current_task' calls 'jl_get_pgcstack' it is simpler to call it directly
        return jl_get_pgcstack()
    }

    @NotSafePoint fun JL_GC_ENCODE_PUSHARGS(n: Int): Long {
        // #define JL_GC_ENCODE_PUSHARGS(n)   (((size_t)(n))<<2)
        return n.toLong() shl 2
    }

    @NotSafePoint fun JL_GC_ENCODE_PUSH(n: Int): Long {
        // #define JL_GC_ENCODE_PUSH(n)       ((((size_t)(n))<<2)|1)
        return (n.toLong() shl 2) or 1L
    }

    @NotSafePoint fun JL_GC_PUSH(vararg args: jl_value_t): GCStack {
        return GCStack(this, args.size).push(*args)
    }

    @NotSafePoint fun JL_GC_POP(stack: GCStack) {
        stack.close()
    }

    @NotSafePoint fun jl_svec_len(t: jl_svec_t): Long = t.getLong(0)
    @NotSafePoint fun jl_svec_set_len_unsafe(t: jl_svec_t, n: Long) = t.setLong(0, n)
    @NotSafePoint fun jl_svec_data(t: jl_svec_t): jl_value_t_array = t.share(8)
    @NotSafePoint fun jl_svecref(@PropagatesRoot t: jl_svec_t, i: Long): jl_value_t = t.getPointer((i + 1) * 8)
    @NotSafePoint fun jl_array_len(a: jl_array_t): Long = a.getLong(8)
    @NotSafePoint fun jl_array_data(a: jl_array_t): Pointer = Pointer(a.getLong(0))
    @NotSafePoint fun jl_array_dim(a: jl_array_t, i: Long): Long = a.getLong(24 + i * 8)  // 8+8+2+2+4 = 24
    @NotSafePoint fun jl_array_dim0(a: jl_array_t): Long = a.getLong(24)
    @NotSafePoint fun jl_array_nrows(a: jl_array_t): Long = jl_array_dim0(a)
    @NotSafePoint fun jl_array_ndims(a: jl_array_t): Long = getArrayFlags(a).ndims.toLong()

    @NotSafePoint fun jl_string_data(s: jl_value_t): Pointer = s.share(8)
    @NotSafePoint fun jl_string_len(s: jl_value_t): Long = s.getLong(0)

    @NotSafePoint fun jl_field_names(st: jl_datatype_t): jl_svec_t = st.name.names
    @NotSafePoint fun jl_field_names(st: jl_datatype_ptr) = jl_field_names(jl_datatype_t(st))

    /*
     * "Hidden" functions, not defined in 'julia.h' or 'julia_internal.h'
     */

    fun jl_threadid(): Short

    fun jl_get_field_offset(ty: jl_datatype_ptr, field: Int): Long  // 1-indexed

    @NotSafePoint fun jl_module_name(m: jl_module_t): jl_sym_t
    @NotSafePoint fun jl_module_parent(m: jl_module_t): jl_module_t

    /*
     * Singletons/Global vars
     */

    @GloballyRooted fun main_module(): jl_module_t
    @GloballyRooted fun base_module(): jl_module_t
    @GloballyRooted fun core_module(): jl_module_t

    @GloballyRooted fun jl_emptysvec(): jl_svec_t
    @GloballyRooted fun jl_emptytuple(): jl_value_t
    @GloballyRooted fun jl_true(): jl_value_t
    @GloballyRooted fun jl_false(): jl_value_t
    @GloballyRooted fun jl_nothing(): jl_value_t

    fun jl_n_threads(): Int

    /*
     * Custom helper methods
     */

    fun getModuleObj(module: jl_module_t, name: String): jl_value_t {
        val obj = jl_get_global(module, jl_symbol(name))
        if (obj == null) {
            val moduleName = jl_symbol_name(jl_module_name(module))
            throw NullPointerException("Could not get '$name' from the module '$moduleName'")
        }
        return obj
    }

    fun getMainObj(name: String): jl_value_t
    fun getBaseObj(name: String): jl_value_t
    fun getCoreObj(name: String): jl_value_t

    fun isNothing(v: jl_value_t): Boolean = Pointer.nativeValue(v) == Pointer.nativeValue(jl_nothing())
    fun isTrue(v: jl_value_t): Boolean    = Pointer.nativeValue(v) == Pointer.nativeValue(jl_true())
    fun isFalse(v: jl_value_t): Boolean   = Pointer.nativeValue(v) == Pointer.nativeValue(jl_false())

    fun getArrayFlags(a: jl_array_t): jl_array_flags = jl_array_flags(a.getShort(16).toUShort().toUInt())
    fun setArrayFlags(a: jl_array_t, flags: jl_array_flags) = a.setShort(16, flags.value.toShort())

    /**
     * `true` if TLS is initialized on this thread, allowing to use all Julia function safely.
     */
    fun inJuliaThread(): Boolean = Pointer.nativeValue(jl_get_pgcstack()) != 0L
    fun assertInJuliaThread() {
        if (!inJuliaThread())
            throw NotInJuliaThreadException()
    }

    /**
     * Run a function in a Julia thread, from any JVM thread.
     *
     * Requires Julia 1.9.0 or later.
     *
     * Warning: Julia 1.9.0 and 1.9.1 have unsafe adoption mechanisms which will fail if the GC is running. Use 1.9.2 or
     * later to avoid this issue. See [this PR](https://github.com/JuliaLang/julia/pull/49934) for more info.
     */
    fun runInJuliaThread(func: () -> Unit) {
        if (!inJuliaThread()) jl_adopt_thread()
        val oldState = jl_gc_unsafe_enter()
        try {
            func()
        } finally {
            // Very important: if we have adopted a JVM thread, we must tell Julia that the thread is always in a
            // GC safe point state when the thread is not running in a Julia context.
            jl_gc_unsafe_leave(oldState)
        }
    }

    fun println(@MaybeUnrooted arg: jl_value_t) =
        jl_call1(getBaseObj("println"), arg)
    fun println(@MaybeUnrooted arg1: jl_value_t, @MaybeUnrooted arg2: jl_value_t) =
        jl_call2(getBaseObj("println"), arg1, arg2)
    fun println(@MaybeUnrooted arg1: jl_value_t, arg2: jl_value_t, @MaybeUnrooted arg3: jl_value_t) =
        jl_call3(getBaseObj("println"), arg1, arg2, arg3)
    fun println(@MaybeUnrooted vararg args: jl_value_t) {
        JL_GC_PUSH(*args).use {
            jl_call(getBaseObj("println"), it.array(), args.size)
        }
    }

    fun display(@MaybeUnrooted arg: jl_value_t) {
        jl_call1(getBaseObj("display"), arg)
        kotlin.io.println()
    }

    fun display(@MaybeUnrooted arg1: jl_value_t, @MaybeUnrooted arg2: jl_value_t) {
        jl_call2(getBaseObj("display"), arg1, arg2)
        kotlin.io.println()
    }

    fun display(@MaybeUnrooted arg1: jl_value_t, @MaybeUnrooted arg2: jl_value_t, @MaybeUnrooted arg3: jl_value_t) {
        jl_call3(getBaseObj("display"), arg1, arg2, arg3)
        kotlin.io.println()
    }

    fun display(@MaybeUnrooted vararg args: jl_value_t) {
        JL_GC_PUSH(*args).use {
            jl_call(getBaseObj("display"), it.array(), args.size)
            kotlin.io.println()
        }
    }

    /**
     * Returns `fieldoffset(t, name)`.
     *
     * This function performs allocations and therefore is a GC safe-point.
     */
    fun getFieldOffset(t: jl_datatype_ptr, name: String): Long {
        val fieldIndex = jl_field_index(t, jl_symbol(name), 0)
        if (fieldIndex == -1) {
            throw JuliaException("No field named '$name' in the given type")
        }
        return jl_get_field_offset(t, fieldIndex + 1)
    }

    @NotSafePoint
    fun getFieldCount(t: jl_datatype_ptr): Long = jl_svec_len(jl_datatype_t(t).name.names)

    /**
     * Returns `fieldtype(t, field)`. `field` can be either an `Int` (field index) or a `Symbol` (field name).
     *
     * This function performs allocations and therefore is a GC safe-point.
     */
    fun getFieldType(t: jl_datatype_ptr, field: jl_value_t): jl_value_t {
        val fieldType = jl_call2(getCoreObj("fieldtype"), t, field)
        if (fieldType == null) {
            exceptionCheck()
            return jl_nothing()
        }
        return fieldType
    }

    fun getFieldType(t: jl_datatype_ptr, field: Int) = getFieldType(t, jl_box_int32(field))

    /**
     * Returns `sizeof(t)`.
     *
     * This function performs allocations and therefore is a GC safe-point.
     */
    fun sizeof(t: jl_value_t): Long {
        val typeSize = jl_call1(getBaseObj("sizeof"), t)
        if (typeSize == null) {
            exceptionCheck()
            return -1
        }
        return jl_unbox_int64(typeSize)
    }

    fun getGlobalVar(symbol: String): Pointer

    val memory: GlobalMemory
    fun errorBuffer(): IOBuffer

    fun exceptionCheck()
}