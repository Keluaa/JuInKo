package com.keluaa.juinko.impl

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
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
            val options = arrayOf(
                ::pathFromProperties,
                ::pathFromBinDir,
                ::pathFromJulia,
                ::pathFromCmdLine
            )

            var path: String? = null
            for (option in options) {
                path = option()
                if (path != null && checkForLibs(cleanPath(path)))
                    break
            }

            if (path == null)
                throw FileNotFoundException("Could not get the path to the Julia bin dir from neither the ENV or command line.")

            val libs = libsPaths(cleanPath(path))
            JULIA_BIN_PATH = libs.first
            JULIA_INTERNAL_BIN_PATH = libs.second
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

        private fun cleanPath(path: String): String {
            return path.replace("\\\\", "/")
                       .replace('\\', '/')
        }

        private fun libsPaths(path: String): Pair<String, String> {
            // On Windows 'System.mapLibraryName' does not add the 'lib' prefix of the Julia libs
            val prefix = if (System.getProperty("os.name").startsWith("Windows")) "lib" else ""

            val libJuliaPath = path + '/' + prefix + System.mapLibraryName(JULIA_LIB_NAME)
            val libJuliaInternalPath = path + '/' + prefix + System.mapLibraryName(JULIA_INTERNAL_LIB_NAME)

            return Pair(libJuliaPath, libJuliaInternalPath)
        }

        private fun checkForLibs(path: String): Boolean {
            val libs = libsPaths(path)
            return Files.exists(Path(libs.first)) && Files.exists(Path(libs.second))
        }
    }
}