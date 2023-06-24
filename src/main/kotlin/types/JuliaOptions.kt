package com.keluaa.juinko.types

import com.keluaa.juinko.JuliaVersion
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Interface over `struct jl_options_t` or `Base.JLOptions`.
 *
 * Since this struct must be used before initializing Julia, we cannot use [JuliaStruct] to set the field offsets.
 */
class JuliaOptions(private val optionsPointer: Pointer) {

    companion object {
        fun getOptions(lib: NativeLibrary): JuliaOptions {
            val optionsPointer = lib.getGlobalVariableAddress("jl_options")
            return JuliaOptions(optionsPointer)
        }

        const val JL_LOG_NONE = 0
        const val JL_LOG_USER = 1
        const val JL_LOG_ALL = 2
        const val JL_LOG_PATH = 3

        const val JL_OPTIONS_CHECK_BOUNDS_DEFAULT = 0
        const val JL_OPTIONS_CHECK_BOUNDS_ON = 1
        const val JL_OPTIONS_CHECK_BOUNDS_OFF = 2

        const val JL_OPTIONS_COMPILE_DEFAULT = 1
        const val JL_OPTIONS_COMPILE_OFF = 0
        const val JL_OPTIONS_COMPILE_ON = 1
        const val JL_OPTIONS_COMPILE_ALL = 2
        const val JL_OPTIONS_COMPILE_MIN = 3

        const val JL_OPTIONS_COLOR_AUTO = 0
        const val JL_OPTIONS_COLOR_ON = 1
        const val JL_OPTIONS_COLOR_OFF = 2

        const val JL_OPTIONS_HISTORYFILE_ON = 1
        const val JL_OPTIONS_HISTORYFILE_OFF = 0

        const val JL_OPTIONS_STARTUPFILE_ON = 1
        const val JL_OPTIONS_STARTUPFILE_OFF = 2

        const val JL_LOGLEVEL_BELOWMIN = -1000001
        const val JL_LOGLEVEL_DEBUG = -1000
        const val JL_LOGLEVEL_INFO = 0
        const val JL_LOGLEVEL_WARN = 1000
        const val JL_LOGLEVEL_ERROR = 2000
        const val JL_LOGLEVEL_ABOVEMAX = 1000001

        const val JL_OPTIONS_DEPWARN_OFF = 0
        const val JL_OPTIONS_DEPWARN_ON = 1
        const val JL_OPTIONS_DEPWARN_ERROR = 2

        const val JL_OPTIONS_WARN_OVERWRITE_OFF = 0
        const val JL_OPTIONS_WARN_OVERWRITE_ON = 1

        const val JL_OPTIONS_WARN_SCOPE_OFF = 0
        const val JL_OPTIONS_WARN_SCOPE_ON = 1

        const val JL_OPTIONS_POLLY_ON = 1
        const val JL_OPTIONS_POLLY_OFF = 0

        const val JL_OPTIONS_FAST_MATH_ON = 1
        const val JL_OPTIONS_FAST_MATH_OFF = 2
        const val JL_OPTIONS_FAST_MATH_DEFAULT = 0

        const val JL_OPTIONS_HANDLE_SIGNALS_ON = 1
        const val JL_OPTIONS_HANDLE_SIGNALS_OFF = 0

        const val JL_OPTIONS_USE_SYSIMAGE_NATIVE_CODE_YES = 1
        const val JL_OPTIONS_USE_SYSIMAGE_NATIVE_CODE_NO = 0

        const val JL_OPTIONS_USE_COMPILED_MODULES_YES = 1
        const val JL_OPTIONS_USE_COMPILED_MODULES_NO = 0

        const val JL_OPTIONS_USE_PKGIMAGES_YES = 1
        const val JL_OPTIONS_USE_PKGIMAGES_NO = 0

        // Implicit values
        const val JL_OPTIONS_THREADS_AUTO = -1
    }

    private fun alignTo(offset: Long, size: Int): Long {
        if (size == 0)
            return offset
        if (offset % size == 0L)
            return offset
        return offset + size - (offset % size)
    }

    private val SIZE_quiet = 1 // int8_t
    private val OFFSET_quiet = 0L

    private val SIZE_banner = 1 // int8_t
    private val OFFSET_banner = alignTo(OFFSET_quiet + SIZE_quiet, SIZE_banner)

    private val SIZE_julia_bindir = Native.POINTER_SIZE // const char *
    private val OFFSET_julia_bindir = alignTo(OFFSET_banner + SIZE_banner, SIZE_julia_bindir)

    private val SIZE_julia_bin = Native.POINTER_SIZE // const char *
    private val OFFSET_julia_bin = alignTo(OFFSET_julia_bindir + SIZE_julia_bindir, SIZE_julia_bin)

    private val SIZE_cmds = Native.POINTER_SIZE // const char **
    private val OFFSET_cmds = alignTo(OFFSET_julia_bin + SIZE_julia_bin, SIZE_cmds)

    private val SIZE_image_file = Native.POINTER_SIZE // const char *
    private val OFFSET_image_file = alignTo(OFFSET_cmds + SIZE_cmds, SIZE_image_file)

    private val SIZE_cpu_target = Native.POINTER_SIZE // const char *
    private val OFFSET_cpu_target = alignTo(OFFSET_image_file + SIZE_image_file, SIZE_cpu_target)

    private val SIZE_nthreadpools = JuliaVersion.from("1.9.0", { 1 }, { 0 }) // int8_t
    private val OFFSET_nthreadpools = alignTo(OFFSET_cpu_target + SIZE_cpu_target, SIZE_nthreadpools)
    var nthreadpools: Int
        get() = optionsPointer.getByte(OFFSET_nthreadpools).toInt()
        set(value) = optionsPointer.setByte(OFFSET_nthreadpools, value.toByte())

    private val SIZE_nthreads = JuliaVersion.from("1.9.0", { 2 }, { 4 }) // int16_t from 1.9, int32_t before
    private val OFFSET_nthreads = alignTo(OFFSET_nthreadpools + SIZE_nthreadpools, SIZE_nthreads)
    var nthreads: Int
        get() = optionsPointer.getShort(OFFSET_nthreads).toInt()
        set(value) = optionsPointer.setShort(OFFSET_nthreads, value.toShort())

    private val SIZE_nthreads_per_pool = JuliaVersion.from("1.9.0", { Native.POINTER_SIZE }, { 0 }) // const int16_t *
    private val OFFSET_nthreads_per_pool = alignTo(OFFSET_nthreads + SIZE_nthreads, SIZE_nthreads_per_pool)
    var nthreads_per_pool: ShortArray
        get() = optionsPointer.getPointer(OFFSET_nthreads_per_pool).getShortArray(0, nthreadpools)
        set(value) {
            val array = Memory((value.size * 2).toLong())
            array.write(0, value, 0, value.size)
            optionsPointer.setPointer(OFFSET_nthreads_per_pool, array)
        }

    private val SIZE_nprocs = 4 // int32_t
    private val OFFSET_nprocs = alignTo(OFFSET_nthreads_per_pool + SIZE_nthreads_per_pool, SIZE_nprocs)

    private val SIZE_machine_file = Native.POINTER_SIZE // const char *
    private val OFFSET_machine_file = alignTo(OFFSET_nprocs + SIZE_nprocs, SIZE_machine_file)

    private val SIZE_project = Native.POINTER_SIZE // const char *
    private val OFFSET_project = alignTo(OFFSET_machine_file + SIZE_machine_file, SIZE_project)

    private val SIZE_isinteractive = 1 // int8_t
    private val OFFSET_isinteractive = alignTo(OFFSET_project + SIZE_project, SIZE_isinteractive)

    private val SIZE_color = 1 // int8_t
    private val OFFSET_color = alignTo(OFFSET_isinteractive + SIZE_isinteractive, SIZE_color)

    private val SIZE_historyfile = 1 // int8_t
    private val OFFSET_historyfile = alignTo(OFFSET_color + SIZE_color, SIZE_historyfile)

    private val SIZE_startupfile = 1 // int8_t
    private val OFFSET_startupfile = alignTo(OFFSET_historyfile + SIZE_historyfile, SIZE_startupfile)
    var startupfile: Boolean
        get() = optionsPointer.getByte(OFFSET_startupfile) == JL_OPTIONS_STARTUPFILE_ON.toByte()
        set(value) = optionsPointer.setByte(OFFSET_startupfile,
            if (value) JL_OPTIONS_STARTUPFILE_ON.toByte() else JL_OPTIONS_STARTUPFILE_OFF.toByte())

    private val SIZE_compile_enabled = 1 // int8_t
    private val OFFSET_compile_enabled = alignTo(OFFSET_startupfile + SIZE_startupfile, SIZE_compile_enabled)

    private val SIZE_code_coverage = 1 // int8_t
    private val OFFSET_code_coverage = alignTo(OFFSET_compile_enabled + SIZE_compile_enabled, SIZE_code_coverage)

    private val SIZE_malloc_log = 1 // int8_t
    private val OFFSET_malloc_log = alignTo(OFFSET_code_coverage + SIZE_code_coverage, SIZE_malloc_log)

    private val SIZE_tracked_path = JuliaVersion.from("1.8.0", { Native.POINTER_SIZE }, { 0 }) // const char *
    private val OFFSET_tracked_path = alignTo(OFFSET_malloc_log + SIZE_malloc_log, SIZE_tracked_path)

    private val SIZE_opt_level = 1 // int8_t
    private val OFFSET_opt_level = alignTo(OFFSET_tracked_path + SIZE_tracked_path, SIZE_opt_level)
    var opt_level: Int
        get() = optionsPointer.getByte(OFFSET_opt_level).toInt()
        set(value) = optionsPointer.setByte(OFFSET_opt_level, value.toByte())

    private val SIZE_opt_level_min = 1 // int8_t
    private val OFFSET_opt_level_min = alignTo(OFFSET_opt_level + SIZE_opt_level, SIZE_opt_level_min)

    private val SIZE_debug_level = 1 // int8_t
    private val OFFSET_debug_level = alignTo(OFFSET_opt_level_min + SIZE_opt_level_min, SIZE_debug_level)

    private val SIZE_check_bounds = 1 // int8_t
    private val OFFSET_check_bounds = alignTo(OFFSET_debug_level + SIZE_debug_level, SIZE_check_bounds)
    var check_bounds: Int
        get() = optionsPointer.getByte(OFFSET_check_bounds).toInt()
        set(value) = optionsPointer.setByte(OFFSET_check_bounds, value.toByte())

    private val SIZE_depwarn = 1 // int8_t
    private val OFFSET_depwarn = alignTo(OFFSET_check_bounds + SIZE_check_bounds, SIZE_depwarn)

    private val SIZE_warn_overwrite = 1 // int8_t
    private val OFFSET_warn_overwrite = alignTo(OFFSET_depwarn + SIZE_depwarn, SIZE_warn_overwrite)

    private val SIZE_can_inline = 1 // int8_t
    private val OFFSET_can_inline = alignTo(OFFSET_warn_overwrite + SIZE_warn_overwrite, SIZE_can_inline)

    private val SIZE_polly = 1 // int8_t
    private val OFFSET_polly = alignTo(OFFSET_can_inline + SIZE_can_inline, SIZE_polly)

    private val SIZE_trace_compile = Native.POINTER_SIZE // const char *
    private val OFFSET_trace_compile = alignTo(OFFSET_polly + SIZE_polly, SIZE_trace_compile)

    private val SIZE_fast_math = 1 // int8_t
    private val OFFSET_fast_math = alignTo(OFFSET_trace_compile + SIZE_trace_compile, SIZE_fast_math)

    private val SIZE_worker = 1 // int8_t
    private val OFFSET_worker = alignTo(OFFSET_fast_math + SIZE_fast_math, SIZE_worker)

    private val SIZE_cookie = Native.POINTER_SIZE // const char *
    private val OFFSET_cookie = alignTo(OFFSET_worker + SIZE_worker, SIZE_cookie)

    private val SIZE_handle_signals = 1 // int8_t
    private val OFFSET_handle_signals = alignTo(OFFSET_cookie + SIZE_cookie, SIZE_handle_signals)
    var handle_signals: Boolean
        get() = optionsPointer.getByte(OFFSET_handle_signals) == 1.toByte()
        set(value) = optionsPointer.setByte(OFFSET_handle_signals,
            if (value) JL_OPTIONS_HANDLE_SIGNALS_ON.toByte() else JL_OPTIONS_HANDLE_SIGNALS_OFF.toByte())

    private val SIZE_use_sysimage_native_code = 1 // int8_t
    private val OFFSET_use_sysimage_native_code = alignTo(OFFSET_handle_signals + SIZE_handle_signals, SIZE_use_sysimage_native_code)

    private val SIZE_use_compiled_modules = 1 // int8_t
    private val OFFSET_use_compiled_modules = alignTo(OFFSET_use_sysimage_native_code + SIZE_use_sysimage_native_code, SIZE_use_compiled_modules)

    private val SIZE_use_pkgimages = JuliaVersion.from("1.9.0", { 1 }, { 0 }) // int8_t
    private val OFFSET_use_pkgimages = alignTo(OFFSET_use_compiled_modules + SIZE_use_compiled_modules, SIZE_use_pkgimages)

    private val SIZE_bindto = Native.POINTER_SIZE // const char *
    private val OFFSET_bindto = alignTo(OFFSET_use_pkgimages + SIZE_use_pkgimages, SIZE_bindto)

    private val SIZE_outputbc = Native.POINTER_SIZE // const char *
    private val OFFSET_outputbc = alignTo(OFFSET_bindto + SIZE_bindto, SIZE_outputbc)

    private val SIZE_outputunoptbc = Native.POINTER_SIZE // const char *
    private val OFFSET_outputunoptbc = alignTo(OFFSET_outputbc + SIZE_outputbc, SIZE_outputunoptbc)

    private val SIZE_outputo = Native.POINTER_SIZE // const char *
    private val OFFSET_outputo = alignTo(OFFSET_outputunoptbc + SIZE_outputunoptbc, SIZE_outputo)

    private val SIZE_outputasm = Native.POINTER_SIZE // const char *
    private val OFFSET_outputasm = alignTo(OFFSET_outputo + SIZE_outputo, SIZE_outputasm)

    private val SIZE_outputji = Native.POINTER_SIZE // const char *
    private val OFFSET_outputji = alignTo(OFFSET_outputasm + SIZE_outputasm, SIZE_outputji)

    private val SIZE_output_code_coverage = Native.POINTER_SIZE // const char *
    private val OFFSET_output_code_coverage = alignTo(OFFSET_outputji + SIZE_outputji, SIZE_output_code_coverage)

    private val SIZE_incremental = 1 // int8_t
    private val OFFSET_incremental = alignTo(OFFSET_output_code_coverage + SIZE_output_code_coverage, SIZE_incremental)

    private val SIZE_image_file_specified = 1 // int8_t
    private val OFFSET_image_file_specified = alignTo(OFFSET_incremental + SIZE_incremental, SIZE_image_file_specified)

    private val SIZE_warn_scope = 1 // int8_t
    private val OFFSET_warn_scope = alignTo(OFFSET_image_file_specified + SIZE_image_file_specified, SIZE_warn_scope)

    private val SIZE_image_codegen = 1 // int8_t
    private val OFFSET_image_codegen = alignTo(OFFSET_warn_scope + SIZE_warn_scope, SIZE_image_codegen)

    private val SIZE_rr_detach = 1 // int8_t
    private val OFFSET_rr_detach = alignTo(OFFSET_image_codegen + SIZE_image_codegen, SIZE_rr_detach)

    private val SIZE_strip_metadata = JuliaVersion.from("1.8.0", { 1 }, { 0 }) // int8_t
    private val OFFSET_strip_metadata = alignTo(OFFSET_rr_detach + SIZE_rr_detach, SIZE_strip_metadata)

    private val SIZE_strip_ir = JuliaVersion.from("1.8.0", { 1 }, { 0 }) // int8_t
    private val OFFSET_strip_ir = alignTo(OFFSET_strip_metadata + SIZE_strip_metadata, SIZE_strip_ir)

    private val SIZE_heap_size_hint = JuliaVersion.from("1.9.0", { 8 }, { 0 }) // uint64_t
    private val OFFSET_heap_size_hint = alignTo(OFFSET_strip_ir + SIZE_strip_ir, SIZE_heap_size_hint)

    val structSize: Long
        // Align to 4 bytes since:
        get() = alignTo(OFFSET_heap_size_hint + SIZE_heap_size_hint, 4)

    /*
     * Helper methods
     */

    /**
     * Sets `nthreads` and initialize the thread pool if needed.
     */
    fun setNumThreads(nthreads: Int) {
        this.nthreads = nthreads
        if (JuliaVersion >= JuliaVersion(1, 9)) {
            val pools = ShortArray(1)
            pools[0] = nthreads.toShort()
            this.nthreadpools = 1
            this.nthreads_per_pool = pools
        }
    }
}