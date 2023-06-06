package com.keluaa.juinko.impl

import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.Path

class JuliaPath {
    companion object {
        private val LOG: Logger = Logger.getLogger(JuliaPath::class.simpleName)

        private const val JULIA_LIB_NAME = "julia"
        private const val JULIA_INTERNAL_LIB_NAME = "julia-internal"
        val JULIA_BIN_PATH: String
        val JULIA_INTERNAL_BIN_PATH: String

        init {
            var path = pathFromProperties()
            if (path == null)
                path = pathFromEnv()
            if (path == null)
                path = pathFromCmdLine()
            if (path == null)
                throw FileNotFoundException("Could not get the path to the Julia bin dir from neither the ENV or command line.")

            path = path.replace("\\\\", "/")
                       .replace('\\', '/')

            // On Windows 'System.mapLibraryName' does not add the 'lib' prefix of the Julia libs
            val prefix = if (System.getProperty("os.name").startsWith("Windows")) "lib" else ""

            JULIA_BIN_PATH = path + '/' + prefix + System.mapLibraryName(JULIA_LIB_NAME)
            JULIA_INTERNAL_BIN_PATH = path + '/' + prefix + System.mapLibraryName(JULIA_INTERNAL_LIB_NAME)
        }

        private fun pathFromProperties(): String? = System.getProperty("juinko.julia_path", null)

        private fun pathFromEnv(): String? {
            val jl_bindir = System.getenv("JULIA_BINDIR")
            if (jl_bindir != null) return jl_bindir

            val jl_exe = System.getenv("JULIA")
            if (jl_exe != null) return Path(jl_exe).parent.toString()

            return null
        }

        private fun pathFromCmdLine(): String? {
            val juliaRunCmd = "julia --startup-file=no -q -O0 -E"
            val getLibDir = "unsafe_string(ccall(:jl_get_libdir, Cstring, ()))"
            val juliaGetLibDirCmd = "$juliaRunCmd \"$getLibDir\""

            var path: String
            try {
                val process = Runtime.getRuntime().exec(juliaGetLibDirCmd)
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroy()
                    throw Exception("Timeout")
                }
                if (process.exitValue() != 0) {
                    throw Exception("Failed with exit code: ${process.exitValue()}")
                }
                path = String(process.inputStream.readAllBytes())
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Could not get Julia lib dir: '$juliaGetLibDirCmd' failed with", e)
                return null
            }

            path = path.trim()
            path = path.substring(1, path.length - 1) // Remove the quotes
            return path
        }
    }
}