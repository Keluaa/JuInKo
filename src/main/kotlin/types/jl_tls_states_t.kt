package com.github.keluaa.juinko.types

import com.github.keluaa.juinko.JuliaVersion
import com.github.keluaa.juinko.types.JuliaRawStruct.RawField
import com.sun.jna.Pointer
import com.sun.jna.PointerType

/**
 * Mirror of `jl_tls_states_t`.
 * Not all fields are defined here, since there is no Julia interface to get their offsets, therefore, only those up to
 * `gc_state` are defined, since it is the only one we can test if it is correct or not.
 *
 * This class should preferably only be used for testing purposes.
 */
class jl_tls_states_t(p: Pointer?): PointerType(p) {

    companion object {
        private val STRUCT = JuliaRawStruct.build(
            RawField("tid", 2),
            RawField("threadpoolid", 1, JuliaVersion >= JuliaVersion(1, 9)),
            RawField("rngseed", 8),
            RawField("safepoint", 8),
            RawField("sleep_check_state", 1),
            RawField("gc_state", 1),
        )

        /**
         * Means the thread is running managed (Julia) code, preventing GC.
         */
        const val JL_GC_STATE_UNSAFE: Byte = 0

        /**
         * Means the thread is doing GC or is waiting for the GC to finish.
         */
        const val JL_GC_STATE_WAITING: Byte = 1

        /**
         * Means the thread is running unmanaged code that can be executed at the same time with the GC.
         */
        const val JL_GC_STATE_SAFE: Byte = 2

        fun fromTask(task: jl_task_t) = jl_tls_states_t(task.ptls)
    }

    val tid: Short
        get() = pointer.getShort(STRUCT["tid"])

    val threadpoolid: Byte
        get() = pointer.getByte(STRUCT["threadpoolid"])

    val rngseed: ULong
        get() = pointer.getLong(STRUCT["rngseed"]).toULong()

    val safepoint: Pointer
        get() = pointer.getPointer(STRUCT["safepoint"])

    val sleep_check_state: Byte
        get() = pointer.getByte(STRUCT["sleep_check_state"])

    /**
     * Can be [JL_GC_STATE_UNSAFE], [JL_GC_STATE_WAITING] or [JL_GC_STATE_SAFE]
     */
    var gc_state: Byte
        get() = pointer.getByte(STRUCT["gc_state"])
        set(value) { pointer.setByte(STRUCT["gc_state"], value) }
}