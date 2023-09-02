package com.github.keluaa.juinko.impl

import com.github.keluaa.juinko.*
import com.github.keluaa.juinko.types.JuliaOptions
import com.sun.jna.*
import com.sun.jna.win32.W32APIOptions
import java.util.logging.Logger

/**
 * Manages the loading and initialization of Julia.
 */
class JuliaLoader {

    interface Kernel32 : Library {
        companion object {
            val INSTANCE: Kernel32? =
                if (Platform.isWindows())
                    Native.load("Kernel32", Kernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
                else
                    null
        }

        fun GetDllDirectory(length: Int, buffer: Pointer): Int
        fun SetDllDirectory(path: String): Boolean

        fun GetDllDirectory(): String {
            val count = GetDllDirectory(0, Pointer.NULL)
            val buffer = Memory(count.toLong() + 1) // +1 for '\0'
            if (GetDllDirectory(count, buffer) != count)
                throw Exception("GetDllDirectory fail")
            return buffer.getWideString(0)
        }
    }

    companion object {
        private val LOG = Logger.getLogger(JuliaLoader::class.java.name)

        private var LIB_JULIA: NativeLibrary? = null
        private var LIB_JULIA_INTERNAL: NativeLibrary? = null
        private var INSTANCE: JuliaImplBase? = null

        private var PREV_DLL_DIRECTORY_PATH: String? = null

        fun isLibJuliaLoaded() = LIB_JULIA != null
        fun isJuliaInitialized() = INSTANCE != null

        /**
         * Overrides the shared library load path to Julia's bin directory, ensuring `jl_init` will find them all.
         *
         * `juliaup` does not rely on `PATH` (or `Path` on Windows) or any other environment variable, but Julia needs
         * to have a path to its libraries, so how does it work?
         * When running Julia from its executable, all OSes try at some point to search libraries in the directory of
         * the exe.
         * BUT, when running Julia from another program, everything breaks when dynamically loading a library with
         * `dlopen` (or `LoadLibraryEx`), since the OS only searches around the current program, not in the same
         * directory as 'libjulia'.
         *
         * In Java, it is unreliable/not portable to change the environment variables of the current process.
         * Therefore, we must rely on OS functions to do that.
         *
         * On Windows, Julia uses `LoadLibraryEx` with the `LOAD_WITH_ALTERED_SEARCH_PATH` flag. This means that our
         * only option (I pass over the very convoluted DLL search path mechanism) is to use `SetDllDirectory`, in order
         * to add the Julia bin dir to the search path.
         * Note: `AddDllDirectory` only works with the `LOAD_LIBRARY_SEARCH_USER_DIRS` flag.
         *
         * Once the library search path is corrected, it is safe to call `jl_init`.
         * If the search path is wrong, the process aborts, usually with an error such as 'could not find libpcre2-8',
         * which ends up being the first library that Julia loads this way.
         *
         * An alternative would be to manually load all libraries in the Julia bin dir before `jl_init`, but the current
         * fix is certainly more correct and reliable.
         *
         */
        fun setLibraryLoadPath() {
            if (Platform.isWindows()) {
                PREV_DLL_DIRECTORY_PATH = Kernel32.INSTANCE?.GetDllDirectory()
                Kernel32.INSTANCE?.SetDllDirectory(JuliaPath.JULIA_LIB_DIR)
            }
        }

        /**
         * Undoes [setLibraryLoadPath], in case the Dll load path is being used by the current process.
         * `jl_init` should load all libraries required by Julia, therefore the load path doesn't matter afterward.
         */
        fun resetLibraryLoadPath() {
            if (PREV_DLL_DIRECTORY_PATH == null) return

            if (Platform.isWindows()) {
                Kernel32.INSTANCE?.SetDllDirectory(PREV_DLL_DIRECTORY_PATH!!)
            }
        }

        private fun checkStringEncoding() {
            val encoding = System.getProperty("jna.encoding", null)
            if (encoding == null) {
                LOG.info("Set 'jna.encoding' to 'UTF-8' for compatibility with Julia")
                System.setProperty("jna.encoding", "UTF-8")
            } else if (encoding.uppercase() != "UTF8" && encoding.uppercase() != "UTF-8") {
                throw Exception("The system property 'jna.encoding' is set to $encoding. Julia supports only UTF-8.")
            }
        }

        fun loadLibrary() {
            if (LIB_JULIA != null) return

            // TODO: load the JuliaImpl with a custom ClassLoader, which could then be freed when unloading the Julia
            //  lib is necessary, and allow to load multiple instances at the same time. JuliaVersion might also need
            //  to be reworked, as well as all classes depending on it to initialize static fields.

            checkStringEncoding()

            LIB_JULIA = NativeLibrary.getInstance(JuliaPath.LIB_JULIA)
            JuliaVersion.setJuliaVersion(LIB_JULIA!!)
            if (JuliaVersion < JuliaVersion(1, 7))
                throw VersionException("Julia versions before 1.7 are not supported")

            LIB_JULIA_INTERNAL = NativeLibrary.getInstance(JuliaPath.LIB_JULIA_INTERNAL)

            LOG.info("Loading Julia version ${JuliaVersion.get()} from lib at ${LIB_JULIA!!.file}")
        }

        fun getOptions(): JuliaOptions {
            loadLibrary()
            return JuliaOptions.getOptions(LIB_JULIA!!)
        }

        fun setupOptions() {
            if (!Platform.isWindows()) {
                // We get a SIGSEGV randomly at shutdown on Linux if this is ON
                getOptions().handle_signals = false
            }
        }

        fun doChecks() {
            if (!Platform.is64Bit())
                throw Exception("JuInKo does not support 32-bit architectures")

            val copyStacks = (System.getenv("JULIA_COPY_STACKS") ?: "0").toInt()
            if (copyStacks != 0) {
                LOG.warning("The environment variable 'JULIA_COPY_STACKS' is not set to 0, " +
                            "this will most likely lead to segmentation faults on Linux.")
            }

            val libVersion = JuliaVersion.get()
            if (JuliaVersion(1, 9, 0) <= libVersion && libVersion < JuliaVersion(1, 9, 2)) {
                // See https://github.com/JuliaLang/julia/pull/49934
                LOG.warning("Julia 1.9.0 and 1.9.1 have unsafe thread adoption mechanism which could " +
                            "randomly fail. Use preferably Julia 1.9.2 or later.")
            }
        }

        private fun load(init: Boolean) {
            loadLibrary()

            doChecks()

            val libVersion = JuliaVersion.get()
            INSTANCE = if (libVersion < JuliaVersion(1, 7, 2)) {
                throw VersionException("Julia versions below 1.7.2 are not supported")
            } else if (libVersion < JuliaVersion(1, 9)) {
                JuliaImpl_1_7_2()
            } else if (libVersion < JuliaVersion(1, 10)) {
                JuliaImpl_1_9_0()
            } else {
                JuliaImpl_1_10_0()
            }

            LOG.info("Julia interface version: ${INSTANCE!!.javaClass.simpleName}")

            setupOptions()

            setLibraryLoadPath()
            INSTANCE!!.initJulia(LIB_JULIA!!, LIB_JULIA_INTERNAL!!)
            resetLibraryLoadPath()

            if (init) {
                INSTANCE!!.initConstants()
            }
        }

        internal fun get(init: Boolean): Julia {
            if (INSTANCE == null) load(init)
            return INSTANCE!!
        }

        /**
         * Returns the current [Julia] instance, if it exists.
         * If not, loads the Julia library and calls `jl_init`.
         * Use [loadLibrary] and [getOptions] to set the [JuliaOptions] before Julia is initialized.
         */
        fun get(): Julia = get(true)
    }
}