package com.keluaa.juinko.types

import com.keluaa.juinko.*
import com.sun.jna.Pointer
import com.sun.jna.PointerType

class jl_task_t(p: Pointer?) : PointerType(p) {

    companion object {
        private val STRUCT = JuliaStruct("Task", JuliaStruct.Location.CORE)

        private val OFFSET_next:         Long by STRUCT.field("next")
        private val OFFSET_queue:        Long by STRUCT.field("queue")
        private val OFFSET_storage:      Long by STRUCT.field("storage")
        private val OFFSET_donenotify:   Long by STRUCT.field("donenotify")
        private val OFFSET_result:       Long by STRUCT.field("result")
        private val OFFSET_logstate:     Long by STRUCT.field("logstate")
        private val OFFSET_code:         Long by STRUCT.field("code")
        private val OFFSET_rngState0:    Long by STRUCT.field("rngState0")
        private val OFFSET_rngState1:    Long by STRUCT.field("rngState1")
        private val OFFSET_rngState2:    Long by STRUCT.field("rngState2")
        private val OFFSET_rngState3:    Long by STRUCT.field("rngState3")
        private val OFFSET__state:       Long by STRUCT.field("_state")
        private val OFFSET_sticky:       Long by STRUCT.field("sticky")
        private val OFFSET__isexception: Long by STRUCT.field("_isexception")
        private val OFFSET_priority:     Long by STRUCT.from("priority", JuliaVersion("1.9.0-alpha1"))

        // Hidden properties
        private val OFFSET_gcstack:      Long by STRUCT.offset("rngState3", 8 * 2 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))
        private val OFFSET_world_age:    Long by STRUCT.offset("rngState3", 8 * 3 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))
        private val OFFSET_ptls:         Long by STRUCT.offset("rngState3", 8 * 4 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))
        private val OFFSET_excstack:     Long by STRUCT.offset("rngState3", 8 * 5 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))
        private val OFFSET_eh:           Long by STRUCT.offset("rngState3", 8 * 6 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))
        private val OFFSET_ctx:          Long by STRUCT.offset("rngState3", 8 * 7 + JuliaVersion.from("1.9.0", { 8 }, { 0 }))

        const val JL_TASK_STATE_RUNNABLE = 0
        const val JL_TASK_STATE_DONE = 1
        const val JL_TASK_STATE_FAILED = 2

        fun from_pgcstack(ptr: Pointer): jl_task_t {
            return jl_task_t(ptr.share(-OFFSET_gcstack))
        }
    }

    val next: jl_value_t
        get() = pointer.getPointer(OFFSET_next)

    val queue: jl_value_t
        get() = pointer.getPointer(OFFSET_queue)

    val storage: jl_value_t  // Named 'tls' in later versions
        get() = pointer.getPointer(OFFSET_storage)

    val doneNotify: jl_value_t
        get() = pointer.getPointer(OFFSET_donenotify)

    val result: jl_value_t
        get() = pointer.getPointer(OFFSET_result)

    val logState: jl_value_t
        get() = pointer.getPointer(OFFSET_logstate)

    val code: jl_function_t  // Named 'start' in later versions
        get() = pointer.getPointer(OFFSET_code)

    val rngState: Pointer
        get() = pointer.share(OFFSET_rngState0, 4 * 8)

    val state: Byte
        get() = pointer.getByte(OFFSET__state)

    val sticky: Boolean
        get() = pointer.getByte(OFFSET_sticky).toInt() != 0

    val isException: Byte
        get() = pointer.getByte(OFFSET__isexception)

    val priority: UShort
        get() = pointer.getShort(OFFSET_priority).toUShort()

    val gcstack: jl_gcframe_ptr
        get() = pointer.share(OFFSET_gcstack)

    var world_age: Long
        get() = pointer.getLong(OFFSET_world_age)
        set(value) = pointer.setLong(OFFSET_world_age, value)

    val ptls: Pointer
        get() = pointer.getPointer(OFFSET_ptls)

    val excstack: Pointer
        get() = pointer.getPointer(OFFSET_excstack)

    val eh: Pointer
        get() = pointer.getPointer(OFFSET_eh)

    val ctx: Pointer
        get() = pointer.getPointer(OFFSET_ctx)
}