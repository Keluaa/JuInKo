package com.keluaa.juinko

import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext


class TestsOrderer : ClassOrderer {

    companion object {
        private val ORDER = arrayOf(
            PointerOffsets::class.simpleName,
            JuliaImplTest::class.simpleName,
            StringsTest::class.simpleName,
            ArrayTest::class.simpleName,
            ExceptionsTest::class.simpleName,
            GarbageCollectorTest::class.simpleName,
            GlobalMemoryTest::class.simpleName,
            ThreadingTest::class.simpleName
        )
    }

    override fun orderClasses(context: ClassOrdererContext) {
        context.classDescriptors.sortBy { ORDER.indexOf(it.displayName) }
    }
}