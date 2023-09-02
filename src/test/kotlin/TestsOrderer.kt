package com.github.keluaa.juinko

import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext


class TestsOrderer : ClassOrderer {

    companion object {
        private val ORDER = arrayOf(
            LoadingTest::class.simpleName,
            PointerOffsets::class.simpleName,
            JuliaImplTest::class.simpleName,
            StringsTest::class.simpleName,
            ArrayTest::class.simpleName,
            BufferTest::class.simpleName,
            ExceptionsTest::class.simpleName,
            GarbageCollectorTest::class.simpleName,
            GlobalMemoryTest::class.simpleName,
            ThreadingTest::class.simpleName
        )
    }

    override fun orderClasses(context: ClassOrdererContext) {
        context.classDescriptors.sortBy {
            val idx = ORDER.indexOf(it.displayName)
            if (idx == -1) throw NoSuchElementException(it.displayName)
            idx
        }
    }
}