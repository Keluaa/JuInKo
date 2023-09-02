package com.github.keluaa.juinko

import com.github.keluaa.juinko.impl.JuliaLoader
import com.github.keluaa.juinko.impl.JuliaPath
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.nameWithoutExtension

/**
 * Mainly here to give info about the environment simply by looking at the logs.
 */
internal class LoadingTest: BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            System.setProperty("jna.debug_load", "true")
            if (JuliaLoader.isLibJuliaLoaded())
                throw RuntimeException("Cannot run tests if Julia library is loaded")
        }
    }

    private fun runCommand(vararg args: String): Pair<Int, String> {
        val process = Runtime.getRuntime().exec(args)
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroy()
            throw Exception("Timeout")
        }
        val stdout = String(process.inputStream.readAllBytes()).trim()
        return Pair(process.exitValue(), stdout)
    }

    @Test
    fun juliaFinding() {
        if (System.getProperty("juinko.julia_path", null) != null) {
            LOG.info("Finding Julia from the property 'juinko.julia_path': ${System.getProperty("juinko.julia_path")}")
        }

        if (System.getenv("JULIA") == null) {
            LOG.info("Finding Julia from the variable 'JULIA': ${System.getenv("JULIA")}")
        }

        val (hasJuliaUp, juliaUpPath) = runCommand(if (Platform.isWindows()) "where" else "which", "juliaup")
        if (hasJuliaUp != 0) {
            LOG.info("Finding Julia from the PATH")
        } else {
            LOG.info("Finding Julia using JuliaUp: '$juliaUpPath'")
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "JUINKO_TEST_SKIP_DL_LOAD", matches = "true")
    fun areJuliaLibsInDlPath() {
        val libName = Path.of(JuliaPath.LIB_JULIA).nameWithoutExtension
        val inPath = try {
            val lib = NativeLibrary.getInstance(libName)
            val libPath = lib.file.absolutePath
            LOG.info("From '$libName', loaded '$libPath'")
            if (libPath != JuliaPath.LIB_JULIA)
                throw RuntimeException("Cannot continue tests as the wrong library has been loaded: $libPath")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        LOG.info("Julia libraries can${if (inPath) " " else " not "}be loaded automatically")
    }
}