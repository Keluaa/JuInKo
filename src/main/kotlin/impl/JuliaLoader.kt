package com.keluaa.juinko.impl

import com.keluaa.juinko.*
import com.keluaa.juinko.types.JuliaOptions
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import java.util.logging.Logger

class JuliaLoader {
    companion object {
        private val LOG = Logger.getLogger(JuliaLoader::class.java.name)

        private var LIB_JULIA: NativeLibrary? = null
        private var LIB_JULIA_INTERNAL: NativeLibrary? = null
        private var INSTANCE: JuliaImplBase? = null

        fun isLibJuliaLoaded() = LIB_JULIA != null

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
            if (LIB_JULIA == null)
                throw Exception("Julia library is not loaded. Call 'loadLibrary()' first.")
            return JuliaOptions.getOptions(LIB_JULIA!!)
        }

        fun setupOptions() {
            if (!Platform.isWindows()) {
                // We get a SIGSEGV randomly at shutdown on Linux if this is ON
                getOptions().handle_signals = false
            }
        }

        private fun load(init: Boolean) {
            loadLibrary()

            val libVersion = JuliaVersion.get()
            INSTANCE = if (libVersion < JuliaVersion(1, 7, 2)) {
                throw VersionException("Julia versions below 1.7.2 are not supported")
            } else if (libVersion < JuliaVersion(1, 9, 0)) {
                JuliaImpl_1_7_2()
            } else {
                if (libVersion == JuliaVersion(1, 9, 0) || libVersion == JuliaVersion(1, 9, 1)) {
                    // TODO: check if this warning is correct, if not, also adjust the doc of [Julia.runInJuliaThread]
                    LOG.warning("Julia 1.9.0 and 1.9.1 have unsafe thread adoption mechanism which could " +
                            "randomly fail. Use preferably Julia 1.9.2 or later.")
                }
                JuliaImpl_1_9_0()
            }

            setupOptions()

            INSTANCE!!.initJulia(LIB_JULIA!!, LIB_JULIA_INTERNAL!!)

            if (init) {
                INSTANCE!!.initConstants()
            }
        }

        internal fun get(init: Boolean): Julia {
            if (INSTANCE == null) load(init)
            return INSTANCE!!
        }

        fun get(): Julia = get(true)
    }
}