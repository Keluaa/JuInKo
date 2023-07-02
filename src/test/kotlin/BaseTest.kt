package com.github.keluaa.juinko

import com.github.keluaa.juinko.impl.JuliaImplBase
import com.github.keluaa.juinko.impl.JuliaLoader

open class BaseTest {

    companion object {
        const val JULIA_THREADS = 4

        lateinit var jl: Julia

        fun initJulia() {
            if (!JuliaLoader.isLibJuliaLoaded()) {
                JuliaLoader.loadLibrary()
                val options = JuliaLoader.getOptions()
                options.setNumThreads(JULIA_THREADS)
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
            if (jl.threadsCount() != JULIA_THREADS)
                throw RuntimeException("Tests expect $JULIA_THREADS Julia threads, got: ${jl.threadsCount()}")
        }
    }
}