package com.github.keluaa.juinko

import com.sun.jna.Memory
import com.sun.jna.Pointer

/**
 * Wrapper for the embedding interface of the Julia GC macros `JL_GC_PUSHx`, `JL_GC_PUSHARGS` and
 * `JL_GC_POP`, which roots values to prevent the Julia's garbage collector from deleting them.
 *
 * This class assumes that the current thread is a Julia thread (or an adopted JVM thread).
 *
 * Managing more permanent memory is done through the [GlobalMemory] class, which roots the Julia values globally instead
 * of on the current task's GC stack.
 *
 * Since this class implements [AutoCloseable], not calling the [GCStack.close] method will result in
 * a warning.
 * Not calling it will result in a memory leak or a GC stack corruption.
 *
 * Three equivalent workflows are possible with this class:
 *
 *  * Using *try-with-resources*. This ensures the [GCStack.close] method will be called in all
 * cases, preventing memory leaks even when exceptions occur in Kotlin/Java code.
 * In Kotlin:
 * ```
 * jl.JL_PUSH1(v).use { stack ->
 *      ...
 * }
 * ```
 * Or with the Java syntax:
 * ```
 * try (GCStack stack = jl.JL_GC_PUSH1(v)) {
 *     ...
 * }
 * ```
 *
 *  * Using the Julia C macros explicitly:
 * ```
 * GCStack stack = jl.JL_GC_PUSH1(v);
 * ...
 * jl.JL_GC_POP(stack);
 * ```
 *
 *  * Calling the close function explicitly:
 * ```
 * GCStack stack = jl.JL_GC_PUSH1(v);
 * ...
 * stack.close();
 * ```
 *
 * @see <a href="https://docs.julialang.org/en/v1/manual/embedding/#Memory-Management">Julia docs about memory management</a>
 */
class GCStack(private val jl: Julia, val size: Int) : AutoCloseable {

    private val stack: Memory = Memory(((size + 2) * 8).toLong())
    private var head: Int = 0

    init {
        stack.clear()
        pushGCStack()
    }

    private fun memIdx(i: Int): Long = ((2 + i) * 8).toLong()

    fun checkPointer(p: Pointer) {
        val address = Pointer.nativeValue(p)
        if (address % 8L != 0L)
            throw Exception("Pointer is not aligned to 8 bytes: $address")
    }

    operator fun get(i: Int): jl_value_t {
        if (head == -1) throw NullPointerException("Cannot access a GC stack after it was closed/JL_GC_POP")
        if (i < 0 || i >= size) throw IndexOutOfBoundsException("Trying to access GC stack object of size $size at index $i")
        return Pointer(stack.getLong(memIdx(i)))
    }

    operator fun set(i: Int, v: jl_value_t): jl_value_t {
        if (head == -1) throw NullPointerException("Cannot write to a GC stack after it was closed/JL_GC_POP")
        if (i < 0 || i >= size) throw IndexOutOfBoundsException("Trying to write to GC stack object of size $size at index $i")
        checkPointer(v)
        stack.setPointer(memIdx(i), v)
        return v
    }

    fun array() = array(0)
    fun array(i: Int) = array(i, size - i)
    fun array(i: Int, n: Int): jl_value_t_array {
        if (head == -1) throw NullPointerException("Cannot access a GC stack after it was closed/JL_GC_POP")
        if (i < 0 || i + n - 1 >= size) throw IndexOutOfBoundsException("Trying to share part of a GC stack object of size $size from index $i to ${i+n-1}")
        return stack.share(memIdx(i), n * 8L)
    }

    fun push(value: jl_value_t): GCStack {
        if (head == -1) throw NullPointerException("Cannot push to a GC stack after it was closed/JL_GC_POP")
        if (head == size) throw IndexOutOfBoundsException("Cannot push to a GC stack already full")
        checkPointer(value)
        stack.setPointer(memIdx(head), value)
        head++
        return this
    }

    fun push(vararg values: jl_value_t): GCStack {
        if (head == -1) throw NullPointerException("Cannot push to a GC stack after it was closed/JL_GC_POP")
        if (head + values.size > size) throw IndexOutOfBoundsException("Too many values to push")
        for (value in values) {
            checkPointer(value)
            stack.setPointer(memIdx(head), value)
            head++
        }
        return this
    }

    private fun pushGCStack() {
        // Using JL_GC_ENCODE_PUSHARGS instead of JL_GC_ENCODE_PUSHx allows to have nullptr in the stack, since
        // JL_GC_PUSHx expects non-null pointers to jl_value_t* while JL_GC_PUSHARGS creates an array to put jl_value_t*
        // directly.
        stack.setLong(0, jl.JL_GC_ENCODE_PUSHARGS(size))
        val gcstack = jl.jl_get_pgcstack()
        if (gcstack == Pointer.NULL) {
            throw NotInJuliaThreadException("GCStack")
        }
        stack.setPointer(8, gcstack.getPointer(0))
        gcstack.setPointer(0, stack)
    }

    override fun close() {
        if (head == -1) return
        head = -1

        // Equivalent to JL_GC_POP()
        // (jl_pgcstack = jl_pgcstack->prev)
        val gcstack = jl.jl_get_pgcstack()
        val prevGCStack = gcstack.getPointer(0).getLong(8)
        gcstack.setLong(0, prevGCStack)
    }
}
