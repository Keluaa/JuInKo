package com.github.keluaa.juinko

import com.github.keluaa.juinko.impl.JuliaImplBase
import com.github.keluaa.juinko.impl.JuliaLoader
import java.util.logging.Logger
import kotlin.math.min

open class BaseTest {

    companion object {
        val LOG = Logger.getLogger("Test")

        /**
         * Some CI runners have less than 4 cores available
         */
        val JULIA_THREADS = min(4, Runtime.getRuntime().availableProcessors())

        lateinit var jl: Julia

        fun initJulia() {
            if (!JuliaLoader.isLibJuliaLoaded()) {
                JuliaLoader.loadLibrary()
                val options = JuliaLoader.getOptions()
                options.setNumThreads(JULIA_THREADS)
                LOG.info("Using $JULIA_THREADS Julia threads")
            }

            jl = JuliaLoader.get(false)
        }

        fun ensureImplConstantsNotInitialized() {
            if ((jl as JuliaImplBase).constantAreInitialized())
                throw RuntimeException("Cannot run tests if JuliaImplBase constants are initialized")
        }

        fun ensureImplConstantsInitialized() {
            if (!(jl as JuliaImplBase).constantAreInitialized()) {
                JuliaLoader.get()
                (jl as JuliaImplBase).initConstants()
            }
        }

        fun ensureJuliaHasThreads() {
            val threadCount = jl.threadsCount()
            if (threadCount != JULIA_THREADS) {
                LOG.warning("Tests expect $JULIA_THREADS Julia threads, got: $threadCount")
            }

            if (threadCount <= 1 || JULIA_THREADS == 1)
                throw RuntimeException("Cannot run threading test without threads")
        }
    }
}