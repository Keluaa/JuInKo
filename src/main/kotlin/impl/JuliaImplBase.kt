package com.github.keluaa.juinko.impl

import com.github.keluaa.juinko.*
import com.github.keluaa.juinko.types.JuliaStruct
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

    fun removedIn(removedVersion: JuliaVersion): Nothing {
        val callerName = StackWalker.getInstance()
            .walk { stream -> stream.skip(1).findFirst().get() }
            .methodName
        throw VersionException("<", removedVersion, "intrinsic function '$callerName' was removed in a previous version")
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

    private lateinit var m_jl_n_threadpools_ptr: Pointer
    private lateinit var m_jl_n_threads_ptr: Pointer
    private lateinit var m_jl_n_threads_per_pool_ptr: Pointer

    private lateinit var m_jl_all_tls_states_size_ptr: Pointer
    private lateinit var m_jl_all_tls_states_ptr: Pointer
    private lateinit var m_jl_gc_running_ptr: Pointer

    override fun jl_main_module(): jl_module_t = m_jl_main_module
    override fun jl_base_module(): jl_module_t = m_jl_base_module
    override fun jl_core_module(): jl_module_t = m_jl_core_module

    override fun jl_emptysvec(): jl_svec_t = m_jl_emptysvec
    override fun jl_emptytuple(): jl_value_t = m_jl_emptytuple
    override fun jl_true(): jl_value_t = m_jl_true
    override fun jl_false(): jl_value_t = m_jl_false
    override fun jl_nothing(): jl_value_t = m_jl_nothing

    override fun jl_n_threadpools(): Int = m_jl_n_threadpools_ptr.getInt(0)
    override fun jl_n_threads(): Int = m_jl_n_threads_ptr.getInt(0)
    override fun jl_n_threads_per_pool(): Array<Int> = m_jl_n_threads_per_pool_ptr.getPointer(0).getIntArray(0, jl_n_threadpools()).toTypedArray()
    override fun jl_n_threads_per_pool(pool: Int) = m_jl_n_threads_per_pool_ptr.getPointer(0).getInt(pool * 4L)

    override fun jl_all_tls_states_size() = m_jl_all_tls_states_size_ptr.getInt(0)
    override fun jl_all_tls_states(): Array<Pointer> = m_jl_all_tls_states_ptr.getPointer(0).getPointerArray(0)
    override fun jl_all_tls_states(tid: Int): Pointer = m_jl_all_tls_states_ptr.getPointer(0).getPointer(tid * 8L)

    override fun jl_gc_running() = m_jl_gc_running_ptr.getInt(0) == 1

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

    final override fun getGlobalVarPtr(symbol: String, internal: Boolean): Pointer {
        val lib = if (internal) libInternal else lib
        val address = lib.getGlobalVariableAddress(symbol)
        if (address == null || Pointer.nativeValue(address) == 0L)
            throw NullPointerException("Could not find $symbol in '${lib.file.absolutePath}'")
        return address
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

        m_jl_main_module = getGlobal<jl_value_t>("jl_main_module")
        m_jl_base_module = getGlobal<jl_value_t>("jl_base_module")
        m_jl_core_module = getGlobal<jl_value_t>("jl_core_module")
        m_jl_emptysvec = getGlobal<jl_value_t>("jl_emptysvec")
        m_jl_emptytuple = getGlobal<jl_value_t>("jl_emptytuple")
        m_jl_true = getGlobal<jl_value_t>("jl_true")
        m_jl_false = getGlobal<jl_value_t>("jl_false")
        m_jl_nothing = getGlobal<jl_value_t>("jl_nothing")
        m_jl_n_threads_ptr = getGlobalVarPtr("jl_n_threads")

        if (JuliaVersion >= JuliaVersion(1, 9)) {
            m_jl_n_threadpools_ptr = getGlobalVarPtr("jl_n_threadpools")
            m_jl_n_threads_per_pool_ptr = getGlobalVarPtr("jl_n_threads_per_pool")
        }

        m_jl_gc_running_ptr = getGlobalVarPtr("jl_gc_running", internal = true)
        m_jl_all_tls_states_size_ptr = getGlobalVarPtr("jl_all_tls_states_size", internal = true)
        m_jl_all_tls_states_ptr = getGlobalVarPtr("jl_all_tls_states", internal = true)

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
