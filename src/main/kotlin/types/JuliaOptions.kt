package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.JuliaVersion
import com.github.keluaa.juinko.types.JuliaRawStruct.RawField
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Interface over `struct jl_options_t` or `Base.JLOptions`.
 *
 * Since this struct must be used before initializing Julia, we cannot use [JuliaStruct] to set the field offsets.
 */
@Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection", "PropertyName", "unused")
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

        val STRUCT = JuliaRawStruct.build(
            RawField("quiet", 1),  // int8_t
            RawField("banner", 1),  // int8_t
            RawField("julia_bindir", Native.POINTER_SIZE),  // const char *
            RawField("julia_bin", Native.POINTER_SIZE),  // const char *
            RawField("commands", Native.POINTER_SIZE),  // const char ** (named 'cmds' in C)
            RawField("image_file", Native.POINTER_SIZE),  // const char *
            RawField("cpu_target", Native.POINTER_SIZE),  // const char *
            RawField("nthreadpools", 1, JuliaVersion >= JuliaVersion("1.9.0")),  // int8_t
            RawField("nthreads", JuliaVersion.from("1.9.0", { 2 }, { 4 })),  // int16_t from 1.9, int32_t before
            RawField("nthreads_per_pool", Native.POINTER_SIZE, JuliaVersion >= JuliaVersion("1.9.0")),  // const int16_t *
            RawField("nprocs", 4),  // int32_t
            RawField("machine_file", Native.POINTER_SIZE),  // const char *
            RawField("project", Native.POINTER_SIZE),  // const char *
            RawField("isinteractive", 1),  // int8_t
            RawField("color", 1),  // int8_t
            RawField("historyfile", 1),  // int8_t
            RawField("startupfile", 1),  // int8_t
            RawField("compile_enabled", 1),  // int8_t
            RawField("code_coverage", 1),  // int8_t
            RawField("malloc_log", 1),  // int8_t
            RawField("tracked_path", Native.POINTER_SIZE, JuliaVersion >= JuliaVersion("1.8.0")),  // const char *
            RawField("opt_level", 1),  // int8_t
            RawField("opt_level_min", 1),  // int8_t
            RawField("debug_level", 1),  // int8_t
            RawField("check_bounds", 1),  // int8_t
            RawField("depwarn", 1), // int8_t
            RawField("warn_overwrite", 1), // int8_t
            RawField("can_inline", 1), // int8_t
            RawField("polly", 1), // int8_t
            RawField("trace_compile", Native.POINTER_SIZE), // const char *
            RawField("fast_math", 1), // int8_t
            RawField("worker", 1), // int8_t
            RawField("cookie", Native.POINTER_SIZE), // const char *
            RawField("handle_signals", 1), // int8_t
            RawField("use_sysimage_native_code", 1), // int8_t
            RawField("use_compiled_modules", 1), // int8_t
            RawField("use_pkgimages", 1, JuliaVersion >= JuliaVersion("1.9.0")), // int8_t
            RawField("bindto", Native.POINTER_SIZE), // const char *
            RawField("outputbc", Native.POINTER_SIZE), // const char *
            RawField("outputunoptbc", Native.POINTER_SIZE), // const char *
            RawField("outputo", Native.POINTER_SIZE), // const char *
            RawField("outputasm", Native.POINTER_SIZE), // const char *
            RawField("outputji", Native.POINTER_SIZE), // const char *
            RawField("output_code_coverage", Native.POINTER_SIZE), // const char *
            RawField("incremental", 1), // int8_t
            RawField("image_file_specified", 1), // int8_t
            RawField("warn_scope", 1), // int8_t
            RawField("image_codegen", 1), // int8_t
            RawField("rr_detach", 1), // int8_t
            RawField("strip_metadata", 1, JuliaVersion >= JuliaVersion("1.8.0")), // int8_t
            RawField("strip_ir", 1, JuliaVersion >= JuliaVersion("1.8.0")), // int8_t
            RawField("heap_size_hint", 8, JuliaVersion >= JuliaVersion("1.9.0")) // uint64_t
        )
    }

    var nthreadpools: Int
        get() = optionsPointer.getByte(STRUCT["nthreadpools"]).toInt()
        set(value) = optionsPointer.setByte(STRUCT["nthreadpools"], value.toByte())

    var nthreads: Int
        get() = optionsPointer.getShort(STRUCT["nthreads"]).toInt()
        set(value) = optionsPointer.setShort(STRUCT["nthreads"], value.toShort())

    var nthreads_per_pool: ShortArray
        get() = optionsPointer.getPointer(STRUCT["nthreads_per_pool"]).getShortArray(0, nthreadpools)
        set(value) {
            val array = Memory((value.size * 2).toLong())
            array.write(0, value, 0, value.size)
            optionsPointer.setPointer(STRUCT["nthreads_per_pool"], array)
        }

    var startupfile: Boolean
        get() = optionsPointer.getByte(STRUCT["startupfile"]) == JL_OPTIONS_STARTUPFILE_ON.toByte()
        set(value) = optionsPointer.setByte(STRUCT["startupfile"],
            if (value) JL_OPTIONS_STARTUPFILE_ON.toByte() else JL_OPTIONS_STARTUPFILE_OFF.toByte())

    var opt_level: Int
        get() = optionsPointer.getByte(STRUCT["opt_level"]).toInt()
        set(value) = optionsPointer.setByte(STRUCT["opt_level"], value.toByte())

    var check_bounds: Int
        get() = optionsPointer.getByte(STRUCT["check_bounds"]).toInt()
        set(value) = optionsPointer.setByte(STRUCT["check_bounds"], value.toByte())

    var handle_signals: Boolean
        get() = optionsPointer.getByte(STRUCT["handle_signals"]) == 1.toByte()
        set(value) = optionsPointer.setByte(STRUCT["handle_signals"],
            if (value) JL_OPTIONS_HANDLE_SIGNALS_ON.toByte() else JL_OPTIONS_HANDLE_SIGNALS_OFF.toByte())

    val structSize = STRUCT.size

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