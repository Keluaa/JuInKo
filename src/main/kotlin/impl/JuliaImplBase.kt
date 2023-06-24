package com.keluaa.juinko.impl

import com.keluaa.juinko.*
import com.keluaa.juinko.types.JuliaStruct
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.*

abstract class JuliaImplBase: Julia {

    fun unavailable(availableVersion: JuliaVersion): Nothing {
        val callerName = StackWalker.getInstance()
            .walk { stream -> stream.skip(1).findFirst().get() }
            .methodName
        throw VersionException("≥", availableVersion, "intrinsic function '$callerName' is unavailable in this version")
    }

    lateinit var lib: NativeLibrary
    lateinit var libInternal: NativeLibrary

    /*
     * Julia globals & getters
     */

    private lateinit var m_jl_main_module: jl_module_t
    private lateinit var m_jl_base_module: jl_module_t
    private lateinit var m_jl_core_module: jl_module_t

    private lateinit var m_jl_emptysvec: jl_module_t
    private lateinit var m_jl_emptytuple: jl_module_t
    private lateinit var m_jl_true: jl_module_t
    private lateinit var m_jl_false: jl_module_t
    private lateinit var m_jl_nothing: jl_module_t

    private var m_jl_n_threadpools: Int = 1
    private var m_jl_n_threads: Int = 0
    private var m_jl_n_threads_per_pool: Array<Int> = arrayOf(0)

    override fun jl_main_module(): jl_module_t = m_jl_main_module
    override fun jl_base_module(): jl_module_t = m_jl_base_module
    override fun jl_core_module(): jl_module_t = m_jl_core_module

    override fun jl_emptysvec(): jl_svec_t = m_jl_emptysvec
    override fun jl_emptytuple(): jl_value_t = m_jl_emptytuple
    override fun jl_true(): jl_value_t = m_jl_true
    override fun jl_false(): jl_value_t = m_jl_false
    override fun jl_nothing(): jl_value_t = m_jl_nothing

    override fun jl_n_threadpools(): Int = m_jl_n_threadpools
    override fun jl_n_threads(): Int = m_jl_n_threads
    override fun jl_n_threads_per_pool(): Array<Int> = m_jl_n_threads_per_pool

    /*
     * Utilities
     */

    private lateinit var permMemory: GlobalMemory
    override val memory
        get() = permMemory

    private lateinit var errorBuffer: IOBuffer
    override fun errorBuffer(): IOBuffer = errorBuffer

    private val mainObjects = Hashtable<String, jl_value_t>()
    private val baseObjects = Hashtable<String, jl_value_t>()
    private val coreObjects = Hashtable<String, jl_value_t>()

    override fun getMainObj(name: String): jl_value_t {
        var obj = mainObjects[name]
        if (obj == null) {
            obj = getModuleObj(m_jl_main_module, name)
            mainObjects[name] = obj
        }
        return obj
    }

    override fun getBaseObj(name: String): jl_value_t {
        var obj = baseObjects[name]
        if (obj == null) {
            obj = getModuleObj(m_jl_base_module, name)
            baseObjects[name] = obj
        }
        return obj
    }

    override fun getCoreObj(name: String): jl_value_t {
        var obj = coreObjects[name]
        if (obj == null) {
            obj = getModuleObj(m_jl_core_module, name)
            coreObjects[name] = obj
        }
        return obj
    }

    override fun exceptionCheck() {
        val exception = jl_exception_occurred()
        if (exception != null) {
            synchronized(errorBuffer) {
                jl_call2(getBaseObj("showerror"), errorBuffer.pointer, exception)
                jl_exception_clear()
                throw JuliaException(errorBuffer)
            }
        }
    }

    final override fun getGlobalVar(symbol: String): Pointer {
        // Important detail: 'getGlobalVariableAddress' returns a pointer to the address of the symbol, not a pointer to
        // the symbol. See https://github.com/JuliaLang/julia/issues/36092#issuecomment-733292636
        return lib.getGlobalVariableAddress(symbol).getPointer(0)
            ?: throw NullPointerException("Could not find the '$symbol' symbol in '${lib.file.absolutePath}'")
    }

    inline fun <reified T> getGlobal(symbol: String, offset: Long = 0L, internal: Boolean = false): T {
        val lib = if (internal) libInternal else lib
        val address = lib.getGlobalVariableAddress(symbol)
        if (address == null || Pointer.nativeValue(address) == 0L)
            throw NullPointerException("Could not find $symbol in '${lib.file.absolutePath}'")

        return when (T::class) {
            Byte::class       -> address.getByte(offset) as T
            Short::class      -> address.getShort(offset) as T
            Int::class        -> address.getInt(offset) as T
            Long::class       -> address.getLong(offset) as T
            Pointer::class    -> address.getPointer(offset) as T
            jl_value_t::class -> address.getPointer(offset) as T
            else -> {
                throw Exception("Unknown type: ${T::class.java.name}")
            }
        }
    }

    /*
     * Interfaces to the other libraries
     */

    fun getVarargsImplClass() = Julia.Varargs::class.java

    private lateinit var varargsImpl: Julia.Varargs
    override val varargs: Julia.Varargs
        get() = varargsImpl

    /**
     * Must be called first in constructors of the implementing class.
     */
    internal fun initJulia(libJulia: NativeLibrary, libInternal: NativeLibrary) {
        this.lib = libJulia
        this.libInternal = libInternal

        jl_init()

        m_jl_main_module = getGlobalVar("jl_main_module")
        m_jl_base_module = getGlobalVar("jl_base_module")
        m_jl_core_module = getGlobalVar("jl_core_module")
        m_jl_emptysvec = getGlobalVar("jl_emptysvec")
        m_jl_emptytuple = getGlobalVar("jl_emptytuple")
        m_jl_true = getGlobalVar("jl_true")
        m_jl_false = getGlobalVar("jl_false")
        m_jl_nothing = getGlobalVar("jl_nothing")
        m_jl_n_threads = getGlobal<Int>("jl_n_threads")

        if (JuliaVersion >= JuliaVersion(1, 9)) {
            m_jl_n_threadpools = getGlobal<Int>("jl_n_threadpools")
            m_jl_n_threads = getGlobal<Int>("jl_n_threads")

            val n_threads_per_pool = getGlobal<Pointer>("jl_n_threads_per_pool")
            m_jl_n_threads_per_pool = n_threads_per_pool.getIntArray(0, m_jl_n_threadpools).toTypedArray()
        }

        varargsImpl = Native.load(JuliaPath.LIB_JULIA, getVarargsImplClass())

        JuliaStruct.jl = this
    }

    internal fun initConstants() {
        permMemory = GlobalMemory(this)
        errorBuffer = IOBuffer(this)
        permMemory.insert(errorBuffer.pointer)
    }

    internal fun constantAreInitialized() = ::permMemory.isInitialized
}