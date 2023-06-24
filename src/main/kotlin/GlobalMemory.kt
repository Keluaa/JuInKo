package com.keluaa.juinko


/**
 * A place to store objects which should have a lifetime lasting longer than a single function call.
 *
 * Objects are stored in a `Dict` with their address as keys. Each instance of the [GlobalMemory] class uses its own
 * dict, stored in the global `Main` module of Julia. If the [GlobalMemory] object is garbage collected by the JVM, the
 * dict will be deleted and all of its objects will be freed during the next pass of Julia's GC.
 *
 * You are responsible to remove the objects from global memory whenever they need to be discarded.
 *
 * **Note**: immutable objects must be handled differently from mutable ones, because immutable objects do not have a
 * permanent address.
 * To remove an immutable object from memory, **you need to give the wrapper returned by [insertImmutable]**, not the
 * pointer to the object.
 * *Failing to do so will not result in a error*, so be careful if you want to keep our memory nice and tidy.
 *
 * @see <a href="https://docs.julialang.org/en/v1/manual/embedding/#Memory-Management">Memory management in the Julia docs</a>
 */
class GlobalMemory(private val jl: Julia) {

    private val setindex_fptr: jl_value_t = jl.getBaseObj("setindex!")
    private val delete_fptr: jl_value_t = jl.getBaseObj("delete!")
    private val empty_fptr: jl_value_t = jl.getBaseObj("empty!")
    private val ref_value_t: jl_value_t
    private val refs_dict: jl_value_t

    init {
        val refValue = jl.getBaseObj("RefValue")
        val any = jl.getBaseObj("Any")
        ref_value_t = jl.jl_apply_type1(refValue, any)!!

        val dictType = jl.getBaseObj("IdDict")
        val dictName = jl.jl_symbol("refs_" + hashCode())

        // refs_<hash> = IdDict()
        val binding = jl.jl_get_binding_wr(jl.jl_main_module(), dictName, 1)!!
        refs_dict = jl.jl_call0(dictType)!!
        jl.jl_checked_assignment(binding, refs_dict)
        jl.exceptionCheck()
    }

    fun insert(@MaybeUnrooted value: jl_value_t): jl_value_t {
        if (jl.jl_is_immutable(jl.jl_typeof(value))) throw JuliaException("Use `insertImmutable` to insert immutable values")
        // setindex!(refs_dict, value, value)
        jl.jl_call3(setindex_fptr, refs_dict, value, value)
        jl.exceptionCheck()
        return value
    }

    fun insertImmutable(@MaybeUnrooted value: jl_value_t): RefValue {
        jl.JL_GC_PUSH(value).use {
            // v_ref = RefValue(value)
            val wrapper = jl.varargs.jl_new_struct(ref_value_t, value)
            // setindex!(refs_dict, v_ref, v_ref)
            jl.jl_call3(setindex_fptr, refs_dict, wrapper, wrapper)
            jl.exceptionCheck()
            return RefValue(wrapper)
        }
    }

    fun remove(value: jl_value_t) = jl.jl_call2(delete_fptr, refs_dict, value)
    fun remove(value: RefValue) = remove(value.pointer)

    fun clear() = jl.jl_call1(empty_fptr, refs_dict)

    fun finalize() = clear()
}