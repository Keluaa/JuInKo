
# JuInKo

JuInKo (for *Julia In Kotlin*) allows you to embed Julia into your 
Kotlin/Java project using [JNA](https://github.com/java-native-access/jna)
bindings.
The API is made to be very close to the Julia C API, with some common patterns
made easier to write thanks to Kotlin.

Currently only supports the latest Julia version: 1.9.0

Bindings for Julia 1.7 to 1.8 can be used but without multi-threading support.


## Usage

### Loading Julia

`JuliaPath` attempts 3 methods to find the Julia libraries, in this order:
 - the `"juinko.julia_path"` system property
 - the `JULIA_BINDIR` environment variable
 - the `JULIA` environment variable
 - or from the command line (and therefore the `PATH`): `julia -E "unsafe_string(ccall(:jl_get_libdir, Cstring, ()))"`

Then `JuliaLoader.get()` allows to access and initialize the `Julia` interface instance
corresponding to the library found by `JuliaPath`.
Depending on the `JuliaVersion` loaded, not all functions will be available.

`JuliaVersion` is set to the currently loaded version of Julia, and can be compared
with as you would with a `VersionNumber` in Julia.

Both `libjulia` and `libjulia-internal` are loaded, but not all of their functions are
made available.

### Garbage Collector Management

`GCStack` provides a context to manage a GC stack, without the need to use any
`JL_GC_PUSH` or `JL_GC_POP`:

```kotlin
val jl = JuliaLoader.get()
val result = GCStack(jl, 2).use { stack ->
    stack[0] = jl.jl_box_int64(1)
    stack[1] = jl.jl_box_int64(2)
    val resultBoxed = jl.jl_call(jl.getBaseObj("+"), stack.array(), 2)
    jl.jl_unbox_int64(resultBoxed!!)
}
println(result)  // 3
```

`GlobalMemory` (accessible from any `Julia` instance) can be used to store any
value with a longer lifetime (with immutables being handled differently from mutables).


### Multi-threading

The Julia API can only be called from threads which are set up by Julia.
While the main JVM thread (which loaded the library) is one of those, any
other JVM thread might be unsafe. 

As from Julia 1.9, `jl_adopt_thread` allows to set up any thread to use the Julia API.
The `Julia::runInJuliaThread` function will call `jl_adopt_thread` if needed, as well as
handle some GC shenanigans. 

```kotlin
jl.runInJuliaThread {
    val jl_tid = jl.jl_threadid()
    val jvm_tid = Thread.currentThread().id
    println("jl_tid: $jl_tid, jvm_tid: $jvm_tid")
}
```
