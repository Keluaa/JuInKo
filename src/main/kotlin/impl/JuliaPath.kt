package com.github.keluaa.juinko.impl

import com.sun.jna.Platform
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Finds and holds the paths to the main Julia libraries.
 * Once this class is loaded, the paths are set cannot be changed.
 *
 * To use a custom path to Julia, use the system property `juinko.julia_path`:
 * ```kotlin
 *     // "pathToJuliaBinDir" is a path to a directory containing "libjulia"
 *     System.setProperty("juinko.julia_path", "pathToJuliaBinDir")
 *     // Then it is safe to load and use `JuliaPath`:
 *     println(JuliaPath.LIB_JULIA)
 * ```
 */
class JuliaPath {
    companion object {
        private val LOG: Logger = Logger.getLogger(JuliaPath::class.simpleName)

        private const val JULIA_LIB_NAME = "julia"
        private const val JULIA_INTERNAL_LIB_NAME = "julia-internal"

        /**
         * Path to the directory containing `libjulia` as well as most libraries Julia relies on.
         */
        val JULIA_LIB_DIR: String

        lateinit var LIB_JULIA: String
            private set

        lateinit var LIB_JULIA_INTERNAL: String
            private set

        init {
            val options = arrayOf(
                ::pathFromProperties,
                ::pathFromJulia,
                ::pathFromJuliaLib,
                ::pathFromCmdLine
            )

            var path: String? = null
            for (option in options) {
                path = option()
                if (path != null && tryPath(path))
                    break
            }

            if (path == null)
                throw FileNotFoundException("Could not get the path to the Julia lib directory from neither the ENV or command line.")

            path = File(path).path  // Normalize the path
            JULIA_LIB_DIR = path
        }

        private fun cleanPath(path: String?): String? {
            return if (path == null)
                null
            else if (path.startsWith('"') && path.endsWith('"')) {
                path.substring(1 until path.length-1)
            } else {
                path
            }
        }

        private fun pathFromProperties(): String? = cleanPath(System.getProperty("juinko.julia_path", null))

        private fun pathFromJulia(): String? {
            val jl_exe = cleanPath(System.getenv("JULIA"))
            if (jl_exe != null) return Paths.get(jl_exe).parent.toString()
            return null
        }

        private fun pathFromJuliaLib(): String? {
            val jl_exe_dir = pathFromJulia()
            if (jl_exe_dir != null) return Paths.get(jl_exe_dir, "../lib").toString()
            return null
        }

        private fun pathFromCmdLine(): String? {
            // Note that we don't bother trying the simpler `which julia` command first, because of juliaup.
            val juliaRunCmd = arrayOf(
                "julia", "--startup-file=no", "-q", "-O0", "-E",
                "unsafe_string(ccall(:jl_get_libdir, Cstring, ()))"
            )

            var path: String
            try {
                val process = Runtime.getRuntime().exec(juliaRunCmd)
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroy()
                    throw Exception("Timeout")
                }
                if (process.exitValue() != 0) {
                    throw Exception("Failed with exit code: ${process.exitValue()}")
                }
                path = String(process.inputStream.readAllBytes())
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Could not get Julia lib dir: " +
                        "'${juliaRunCmd.joinToString(" ")}}' failed with", e)
                return null
            }

            path = path.trim()
            path = path.substring(1, path.length - 1) // Remove the quotes
            return path
        }

        private fun tryPath(path: String): Boolean {
            val dir = File(path)
            if (!dir.isDirectory) return false

            val subDir = File(path, "julia")

            var libJulia = System.mapLibraryName(JULIA_LIB_NAME)
            var libJuliaInternal = System.mapLibraryName(JULIA_INTERNAL_LIB_NAME)

            if (Platform.isWindows()) {
                libJulia = "lib$libJulia"
                libJuliaInternal = "lib$libJuliaInternal"
            }

            val possibleLibJulia = dir.list { _: File, s: String -> s.startsWith(libJulia) } ?: emptyArray()
            var possibleLibJuliaInternal = dir.list { _: File, s: String -> s.startsWith(libJuliaInternal) } ?: emptyArray()

            possibleLibJulia.forEachIndexed { i, s -> possibleLibJulia[i] = File(dir.absolutePath, s).absolutePath }
            possibleLibJuliaInternal.forEachIndexed { i, s -> possibleLibJuliaInternal[i] = File(dir.absolutePath, s).absolutePath }

            if (possibleLibJuliaInternal.isEmpty() && subDir.isDirectory) {
                // Try in the 'lib/julia' folder
                possibleLibJuliaInternal = subDir.list { _: File, s: String -> s.startsWith(libJuliaInternal) } ?: emptyArray()
                possibleLibJuliaInternal.forEachIndexed { i, s -> possibleLibJuliaInternal[i] = File(subDir.absolutePath, s).absolutePath }
            }

            // Make sure to always load the same library each time
            possibleLibJulia.sort()
            possibleLibJuliaInternal.sort()

            if (Platform.isWindows()) {
                // Ignore static libraries
                possibleLibJulia.filter { l -> !l.endsWith(".a") }
                possibleLibJuliaInternal.filter { l -> !l.endsWith(".a") }
            }

            if (possibleLibJulia.isNotEmpty()) {
                val foundLibJulia = possibleLibJulia.first()

                if (possibleLibJuliaInternal.isEmpty()) {
                    LOG.warning("Found '$libJulia' at '$foundLibJulia', but '$libJuliaInternal' " +
                                "was not in the same place. Skipping this directory.")
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