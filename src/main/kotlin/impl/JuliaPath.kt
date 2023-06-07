package com.keluaa.juinko.impl

import com.sun.jna.Platform
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class JuliaPath {
    companion object {
        private val LOG: Logger = Logger.getLogger(JuliaPath::class.simpleName)

        private const val JULIA_LIB_NAME = "julia"
        private const val JULIA_INTERNAL_LIB_NAME = "julia-internal"

        val JULIA_LIB_DIR: String

        lateinit var LIB_JULIA: String
            private set

        lateinit var LIB_JULIA_INTERNAL: String
            private set

        init {
            val options = arrayOf(
                ::pathFromProperties,
                ::pathFromBinDir,
                ::pathFromJulia,
                ::pathFromCmdLine
            )

            var path: String? = null
            for (option in options) {
                path = option()
                if (path != null && tryPath(path))
                    break
            }

            if (path == null)
                throw FileNotFoundException("Could not get the path to the Julia lib dir from neither the ENV or command line.")

            path = File(path).path  // Normalize the path
            JULIA_LIB_DIR = path
        }

        private fun pathFromProperties(): String? = System.getProperty("juinko.julia_path", null)

        private fun pathFromBinDir(): String? {
            val jl_bindir = System.getenv("JULIA_BINDIR")
            if (jl_bindir != null) return Paths.get(jl_bindir, "../lib").toString()
            return null
        }

        private fun pathFromJulia(): String? {
            val jl_exe = System.getenv("JULIA")
            if (jl_exe != null) return Paths.get(Paths.get(jl_exe).parent.toString(), "../lib").toString()
            return null
        }

        private fun pathFromCmdLine(): String? {
            val juliaRunCmd = arrayOf(
                "julia", "--startup-file=no", "-q", "-O0", "-E",
                "unsafe_string(ccall(:jl_get_libdir, Cstring, ()))"
            )

            var path: String
            try {
                val process = Runtime.getRuntime().exec(juliaRunCmd)
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroy()
                    throw Exception("Timeout")
                }
                if (process.exitValue() != 0) {
                    throw Exception("Failed with exit code: ${process.exitValue()}")
                }
                path = String(process.inputStream.readAllBytes())
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Could not get Julia lib dir: '${juliaRunCmd.joinToString(" ")}}' failed with", e)
                return null
            }

            path = path.trim()
            path = path.substring(1, path.length - 1) // Remove the quotes
            return path
        }

        private fun tryPath(path: String): Boolean {
            val dir = File(path)
            if (!dir.isDirectory) return false

            var libJulia = System.mapLibraryName(JULIA_LIB_NAME)
            var libJuliaInternal = System.mapLibraryName(JULIA_INTERNAL_LIB_NAME)

            if (Platform.isWindows()) {
                libJulia = "lib$libJulia"
                libJuliaInternal = "lib$libJuliaInternal"
            }

            val possibleLibJulia = dir.list { _: File, s: String -> s.startsWith(libJulia) } ?: emptyArray()
            val possibleLibJuliaInternal = dir.list { _: File, s: String -> s.startsWith(libJuliaInternal) } ?: emptyArray()

            // Make sure to always load the same library each time
            possibleLibJulia.sort()
            possibleLibJuliaInternal.sort()

            if (possibleLibJulia.isNotEmpty()) {
                val foundLibJulia = possibleLibJulia.first()

                if (possibleLibJuliaInternal.isEmpty()) {
                    LOG.warning("Found 'libjulia' at '$foundLibJulia', but 'libjulia-internal' was not in the same place. Skipping this directory.")
                    return false
                }

                val foundLibJuliaInternal = possibleLibJuliaInternal.first()

                LIB_JULIA = foundLibJulia
                LIB_JULIA_INTERNAL = foundLibJuliaInternal

                return true
            }

            return false
        }
    }
}