package com.keluaa.juinko

class VersionException: Exception {

    private val currentVersion: JuliaVersion = JuliaVersion.get()
    private val op: String
    private val expectedVersion: JuliaVersion
    private val context: String

    constructor(op: String, ver: JuliaVersion, cxt: String = "") {
        this.op = op
        expectedVersion = ver
        context = cxt
    }

    constructor(cxt: String = "") {
        op = ""
        expectedVersion = JuliaVersion(0, 0)
        context = cxt
    }

    override val message: String
        get() = buildString {
            if (op.isEmpty())
                append("Unsupported Julia version: v$currentVersion")
            else
                append("Unsatisfied Julia version constraint: v$currentVersion (loaded) $op v$expectedVersion")
            if (context.isNotEmpty()) append(": ", context)
        }
}