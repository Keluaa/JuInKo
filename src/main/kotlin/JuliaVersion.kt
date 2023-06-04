package com.keluaa.juinko

import com.sun.jna.NativeLibrary
import net.swiftzer.semver.SemVer

/**
 * Semantic versioning for Julia versions.
 *
 * After loading Julia with [JuliaLoader], the companion object behaves like the currently loaded Julia version. Using
 * the class before Julia is loaded will result in an [VersionException].
 */
class JuliaVersion : Comparable<JuliaVersion> {

    companion object : Comparable<JuliaVersion> {
        private var version: JuliaVersion? = null

        fun get() = version ?: throw IllegalStateException("Julia version uninitialized")

        fun setJuliaVersion(lib: NativeLibrary) {
            val libVersion = lib.getFunction("jl_ver_string").invokeString(emptyArray(), false)
            version = JuliaVersion(libVersion)
        }

        override fun compareTo(other: JuliaVersion): Int = get().compareTo(other)

        inline fun <T> before(ver: JuliaVersion, ifTrue: () -> T, ifFalse: () -> T): T =
            if (this < ver) ifTrue() else ifFalse()

        inline fun <T> after(ver: JuliaVersion, ifTrue: () -> T, ifFalse: () -> T): T =
            if (this > ver) ifTrue() else ifFalse()

        inline fun <T> equal(ver: JuliaVersion, ifTrue: () -> T, ifFalse: () -> T): T =
            if (this.compareTo(ver) == 0) ifTrue() else ifFalse()

        inline fun <T> until(ver: JuliaVersion, ifTrue: () -> T, ifFalse: () -> T): T =
            if (this <= ver) ifTrue() else ifFalse()

        inline fun <T> from(ver: JuliaVersion, ifTrue: () -> T, ifFalse: () -> T): T =
            if (this >= ver) ifTrue() else ifFalse()

        inline fun <T> before(str: String, ifTrue: () -> T, ifFalse: () -> T): T = before(JuliaVersion(str), ifTrue, ifFalse)
        inline fun <T> after(str: String, ifTrue: () -> T, ifFalse: () -> T): T = after(JuliaVersion(str), ifTrue, ifFalse)
        inline fun <T> equal(str: String, ifTrue: () -> T, ifFalse: () -> T): T = equal(JuliaVersion(str), ifTrue, ifFalse)
        inline fun <T> until(str: String, ifTrue: () -> T, ifFalse: () -> T): T = until(JuliaVersion(str), ifTrue, ifFalse)
        inline fun <T> from(str: String, ifTrue: () -> T, ifFalse: () -> T): T = from(JuliaVersion(str), ifTrue, ifFalse)
    }

    private val version: SemVer

    constructor(major: Int, minor: Int, patch: Int, preRelease: String) {
        version = SemVer(major, minor, patch, preRelease)
    }

    constructor(major: Int, minor: Int, patch: Int) {
        version = SemVer(major, minor, patch)
    }

    constructor(major: Int, minor: Int) {
        version = SemVer(major, minor, 0)
    }

    constructor(str: String) {
        val parsedVersion = SemVer.parseOrNull(str) ?: throw Exception("Invalid version string: $str")
        version = parsedVersion
    }

    constructor(ver: SemVer) {
        version = ver
    }

    override fun compareTo(other: JuliaVersion): Int = version.compareTo(other.version)

    override fun toString(): String = version.toString()
}
