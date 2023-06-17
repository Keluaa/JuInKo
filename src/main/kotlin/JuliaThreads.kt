package com.keluaa.juinko

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.companionObjectInstance


/**
 * Defines how [JuliaThreads.createSpawner] will generate the ccall which will in turn invoke the Kotlin callback.
 *
 *  - `C_CALL_RETURN_TYPE`: the Julia type of the return type of the callback
 *  - `C_CALL_ARGS_TYPES`: a tuple of the Julia types of the arguments of the callback
 *  - `C_CALL_ARGS`: comma separated list of arguments. May contain expressions or interpolation ('$') to capture the
 *    values of the function arguments ([see the Julia docs](https://docs.julialang.org/en/v1/base/multi-threading/#Base.Threads.@spawn))
 *  - `JULIA_FUNC_ARGS`: comma separated list of arguments. May contain type annotations (`arg1::Int, arg2::String`)
 */
interface JuliaCallbackInfo {
    val C_CALL_RETURN_TYPE: String
    val C_CALL_ARGS_TYPES: String
    val C_CALL_ARGS: String
    val JULIA_FUNC_ARGS: String
}

interface JuliaCallbackBase: Callback

fun interface JuliaCallback: JuliaCallbackBase {
    companion object : JuliaCallbackInfo {
        override val C_CALL_RETURN_TYPE = "Cvoid"
        override val C_CALL_ARGS_TYPES = "()"
        override val C_CALL_ARGS = ""
        override val JULIA_FUNC_ARGS = ""
    }

    fun invoke()
}

fun interface JuliaCallbackRetAny: JuliaCallbackBase {
    companion object : JuliaCallbackInfo {
        override val C_CALL_RETURN_TYPE = "Any"
        override val C_CALL_ARGS_TYPES = "()"
        override val C_CALL_ARGS = ""
        override val JULIA_FUNC_ARGS = ""
    }

    fun invoke(): jl_value_t
}

fun interface JuliaCallback1Arg: JuliaCallbackBase {
    companion object : JuliaCallbackInfo {
        override val C_CALL_RETURN_TYPE = "Any"
        override val C_CALL_ARGS_TYPES = "(Any,)"
        override val C_CALL_ARGS = "\$arg1"
        override val JULIA_FUNC_ARGS = "arg1"
    }

    fun invoke(arg1: jl_value_t): jl_value_t
}

fun interface JuliaCallback1ArgNoRet: JuliaCallbackBase {
    companion object : JuliaCallbackInfo {
        override val C_CALL_RETURN_TYPE = "Cvoid"
        override val C_CALL_ARGS_TYPES = "(Any,)"
        override val C_CALL_ARGS = "\$arg1"
        override val JULIA_FUNC_ARGS = "arg1"
    }

    fun invoke(arg1: jl_value_t)
}

fun interface JuliaCallback2Arg: JuliaCallbackBase {
    companion object : JuliaCallbackInfo {
        override val C_CALL_RETURN_TYPE = "Any"
        override val C_CALL_ARGS_TYPES = "(Any, Any)"
        override val C_CALL_ARGS = "\$arg1, \$arg2"
        override val JULIA_FUNC_ARGS = "arg1, arg2"
    }

    fun invoke(arg1: jl_value_t, arg2: jl_value_t): jl_value_t
}


/**
 * Provides functionality similar to `Threads.@spawn`, with the spawned task executing a Kotlin callback.
 * The callback is a [JuliaCallbackBase], with a companion object extending [JuliaCallbackInfo] which defines the
 * parameters of the callback and its return type.
 */
class JuliaThreads(val jl: Julia) {

    companion object {
        private val spawners: MutableMap<String, jl_function_t> = ConcurrentHashMap()

        private val RESERVED_STACK_SIZE = if (Platform.isWindows()) 0 else 4 * 1024  // 4 kiB

        /**
         * An "override" of `Threads.@spawn` to specify the `reserved_stack` argument of the `Task` constructor.
         * This argument is the thread's stack size, which doesn't matter on Windows (from my testing) but will segfault
         * the JVM on Linux with the message:
         * "JNA: Can't attach native thread to VM for callback: -1 (check stacksize for callbacks)"
         *
         * My guess is that the JVM cannot detect the stack size of a Julia thread and assumes that it cannot attach to
         * it.
         */
        private val CUSTOM_TASK_SPAWN = """
            macro juinko_task_spawn(expr)
                base_thread_spawn = macroexpand(__module__, :(Threads.@spawn ${'$'}(esc(expr))))
                # Find the call to the `Base.Threads.Task` constructor
                task_ctor = base_thread_spawn.args[2].args[2].args[2].args[1].args[2]
                # Add the secret `reserved_stack` argument to it
                push!(task_ctor.args, :(${RESERVED_STACK_SIZE}))
                return base_thread_spawn
            end
        """.trimIndent()
    }

    private val fetchFunction = lazy { jl.getBaseObj("fetch") }
    private val isTaskDoneFunction = lazy { jl.getBaseObj("istaskdone") }
    private val sleepFunction = lazy { jl.getBaseObj("sleep") }

    private var spawnersInitialized = false

    private fun initSpawners() {
        if (spawnersInitialized) return
        System.err.println(CUSTOM_TASK_SPAWN)
        jl.jl_load_file_string(CUSTOM_TASK_SPAWN, CUSTOM_TASK_SPAWN.length.toLong(),
            "JuliaThreads.kt::initSpawners", jl.main_module())
        jl.exceptionCheck()
        spawnersInitialized = true
    }

    /**
     * Creates a Julia function of the form:
     * ```julia
     * function spawner_<name_hash>(callback, funcArgs...)
     *     return Threads.@spawn ccall(callback, retType, (argsTypes...), cCallArgs...)
     * end
     * ```
     * Where `<name_hash>` is the hash code of the `name` of the callback interface.
     *
     * The generated code should be generic enough to accommodate most callbacks needs.
     *
     * `Threads.@spawn` is modified to handle some Linux/JVM/Julia interaction.
     */
    private fun createSpawner(name: String, retType: String, argsTypes: String, cCallArgs: String, funcArgs: String): jl_value_t {
        initSpawners()
        val spawnerFuncArgs = arrayOf("callback", funcArgs)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        val callArgs = arrayOf("callback", retType, argsTypes, cCallArgs)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        val nameHash = name.hashCode().toUInt()
        val spawnerCode = """
            function spawner_$nameHash($spawnerFuncArgs)
                return @juinko_task_spawn ccall($callArgs)
            end
        """.trimIndent()
        val spawnerFunc = jl.jl_load_file_string(spawnerCode, spawnerCode.length.toLong(),
            "JuliaThreads.kt::createSpawner($name)", jl.main_module())
        jl.exceptionCheck()
        spawners[name] = spawnerFunc!!
        return spawnerFunc
    }

    fun createSpawner(callbackName: String, callbackInfo: JuliaCallbackInfo): jl_value_t {
        return createSpawner(
            callbackName,
            callbackInfo.C_CALL_RETURN_TYPE,
            callbackInfo.C_CALL_ARGS_TYPES,
            callbackInfo.C_CALL_ARGS,
            callbackInfo.JULIA_FUNC_ARGS
        )
    }

    fun getSpawnerFunction(callbackName: String) = spawners[callbackName]

    inline fun <reified CB : JuliaCallbackBase> getOrCreateSpawner(): jl_value_t {
        val callbackName = CB::class.java.name
        val spawner = getSpawnerFunction(callbackName)
        if (spawner == null) {
            val callbackInfo = CB::class.companionObjectInstance!!
            return createSpawner(callbackName, callbackInfo as JuliaCallbackInfo)
        }
        return spawner
    }

    inline fun <reified CB : JuliaCallbackBase> spawn(callback: CB): jl_value_t {
        val spawner = getOrCreateSpawner<CB>()
        val callbackPtr = jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(callback))
        val task = jl.jl_call1(spawner, callbackPtr)
        if (task == null) {
            jl.exceptionCheck()
            throw Exception("Could not launch task as `Threads.@spawn` returned `null`")
        }
        return task
    }

    inline fun <reified CB : JuliaCallbackBase> spawn(callback: CB, arg1: jl_value_t): jl_value_t {
        val spawner = getOrCreateSpawner<CB>()
        val callbackPtr = jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(callback))
        val task = jl.jl_call2(spawner, callbackPtr, arg1)
        if (task == null) {
            jl.exceptionCheck()
            throw Exception("Could not launch task as `Threads.@spawn` returned `null`")
        }
        return task
    }

    inline fun <reified CB : JuliaCallbackBase> spawn(callback: CB, arg1: jl_value_t, arg2: jl_value_t): jl_value_t {
        val spawner = getOrCreateSpawner<CB>()
        val callbackPtr = jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(callback))
        val task = jl.jl_call3(spawner, callbackPtr, arg1, arg2)
        if (task == null) {
            jl.exceptionCheck()
            throw Exception("Could not launch task as `Threads.@spawn` returned `null`")
        }
        return task
    }

    /**
     * Reuse the given `stack` to spawn a task for the `callback`. The first element of the `stack` is set to the
     * function pointer for the `Threads.@spawn` call, therefore the arguments to the callback should be placed starting
     * at the index `1` and not `0`.
     */
    inline fun <reified CB : JuliaCallbackBase> spawn(callback: CB, stack: GCStack): jl_value_t {
        val spawner = getOrCreateSpawner<CB>()

        val callbackPtr = jl.jl_box_voidpointer(CallbackReference.getFunctionPointer(callback))
        if (Pointer.nativeValue(stack[0]) != 0L) {
            throw Exception("'spawn' uses the zero-th element of the stack to call the spawner function to avoid copying the stack.")
        }
        stack[0] = callbackPtr

        val task = jl.jl_call(spawner, stack.array(), stack.size)

        if (task == null) {
            jl.exceptionCheck()
            throw Exception("Could not launch task as `Threads.@spawn` returned `null`")
        }
        return task
    }

    fun isTaskDone(task: jl_value_t) = jl.jl_call1(isTaskDoneFunction.value, task) == jl.jl_true()

    fun waitFor(task: jl_value_t): jl_value_t {
        val retVal = jl.jl_call1(fetchFunction.value, task)
        if (retVal == null) {
            jl.exceptionCheck()
            throw Exception("Unknown exception in task, which returned `null`")
        }
        return retVal
    }

    fun waitForAll(tasks: Collection<jl_value_t>) {
        val notCompleted = tasks.toMutableList()

        waitLoop@ while (notCompleted.isNotEmpty()) {
            for ((i, task) in notCompleted.withIndex()) {
                if (jl.jl_call1(isTaskDoneFunction.value, task) == jl.jl_false())
                    continue

                val retVal = jl.jl_call1(fetchFunction.value, task)
                if (retVal == null) {
                    jl.exceptionCheck()
                    throw Exception("Unknown exception in task $i, which returned `null`")
                }

                notCompleted.removeAt(i)
                continue@waitLoop
            }

            if (notCompleted.isNotEmpty())
                Thread.sleep(5)
        }
    }

    /**
     * Waits for each task in the stack from `stack[first]` to `stack[first+n-1]`, and replaces each task in the stack
     * with their return value.
     */
    fun waitForAll(stack: GCStack, first: Int = 0, n: Int = stack.size) {
        val notCompleted = mutableListOf<Int>()
        for (i in 0 until n)
            notCompleted.add(first + i)

        waitLoop@ while (notCompleted.isNotEmpty()) {
            for ((pos, i) in notCompleted.withIndex()) {
                if (jl.jl_call1(isTaskDoneFunction.value, stack[i]) == jl.jl_false())
                    continue

                val retVal = jl.jl_call1(fetchFunction.value, stack[i])
                if (retVal == null) {
                    jl.exceptionCheck()
                    throw Exception("Unknown exception in task $i, which returned `null`")
                }
                stack[i] = retVal

                notCompleted.removeAt(pos)
                continue@waitLoop
            }

            if (notCompleted.isNotEmpty())
                Thread.sleep(5)
        }
    }

    fun sleep(ms: Int) {
        jl.jl_call1(sleepFunction.value, jl.jl_box_float64(ms / 1000.0))
    }
}