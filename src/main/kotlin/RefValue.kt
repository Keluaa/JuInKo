package com.keluaa.juinko

import com.sun.jna.PointerType


/**
 * Wrapper for `Base.RefValue`
 *
 * Used by [GlobalMemory] for immutable objects.
 */
class RefValue(value: jl_value_t) : PointerType(value) {
    fun get(): jl_value_t = pointer.getPointer(0)
}