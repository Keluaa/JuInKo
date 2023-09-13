
# JuInKo

[![Release](https://jitpack.io/v/Keluaa/JuInKo.svg)](https://jitpack.io/#Keluaa/JuInKo)

JuInKo (for *Julia In Kotlin*) allows you to embed Julia into your 
Kotlin/Java project using [JNA](https://github.com/java-native-access/jna)
bindings.
The API is made to be very close to the Julia C API, with some common patterns
made easier to write thanks to Kotlin.

Currently only supports the Julia version 1.9 and above.

Bindings for Julia 1.7 can be used but without multi-threading support.

## Adding JuInKo to your project

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
     implementation("com.github.Keluaa:JuInKo:1.0")
}
```

## Usage

### Loading Julia

`JuliaPath` attempts 3 methods to find the Julia libraries, in this order:
 - the `"juinko.julia_path"` JVM system property
 - the `JULIA_BINDIR` environment variable
 - the `JULIA` environment variable
 - or from the command line (and therefore the `PATH`): `julia -E "unsafe_string(ccall(:jl_get_libdir, Cstring, ()))"`

The last method implicitly supports [juliaup](https://github.com/JuliaLang/juliaup),
and will handle the `JULIAUP_CHANNEL` environment variable for example.

Then `JuliaLoader.get()` allows to access and initialize the `Julia` interface instance
corresponding to the library found by `JuliaPath`.
Depending on the `JuliaVersion` loaded, not all functions will be available.

`JuliaVersion` is set to the currently loaded version of Julia, and can be compared
with as you would with a `VersionNumber` in Julia.

Both `libjulia` and `libjulia-internal` are loaded, but not all of their functions are
made available: they are implemented as they are needed.
Do not hesitate to create a new issue for this, most functions are simple to implement,
but doing them all is cumbersome.

Julia options must be set before `JuliaLoader.get()` initializes Julia.
The workflow should look like this:

```kotlin
JuliaLoader.loadLibrary()
val jloptions = JuliaLoader.getOptions()
// Set the options here...
val jl = JuliaLoader.get()
```

### The `Julia.kt` interface

It serves as the equivalent of `julia.h` and `julia-internal.h` (and a few other things).
It is version independent: functions/variables defined in `v1.10` will also be defined if
the loaded Julia version is in `v1.9`, but will raise a `VersionException` if called.

Common global C variables are directly available (e.g. `jl_main_module`, `jl_nothing`, etc...),
and more specific ones can be accessed through `Julia.getGlobal` and `Julia.getGlobalVarPtr`.

Some common macros defined in `julia.h` were transformed into functions.

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
While the main JVM thread is one of those, any other JVM thread is unsafe. 

As from Julia 1.9, `jl_adopt_thread` allows to set up any thread to use the Julia API.
The `Julia.runAsJuliaThread` function will call `jl_adopt_thread` if needed, as well as
handle some important GC shenanigans.

```kotlin
jl.runAsJuliaThread {
    val jl_tid = jl.jl_threadid()
    val jvm_tid = Thread.currentThread().id
    println("jl_tid: $jl_tid, jvm_tid: $jvm_tid")
}
```

#### Notes about multi-threading

Programming good and reliable multi-threading will require you to understand the basics of how the
Julia Garbage Collector works.

I recommend to read [the documentation of `Julia.runAsJuliaThread`](src/main/kotlin/Julia.kt),
as well as at the [multi-threading tests](src/test/kotlin/ThreadingTest.kt).

#### Debugging multi-threading

You will make mistakes, as we all do.
The [DebuggingUtils](src/main/kotlin/DebuggingUtils.kt) object holds some useful functions to help you in your quest.

### Exceptions

`Julia.exceptionCheck` encapsulates `jl_current_exception` to throw a `JuliaException`
with the message and traceback of the Julia exception (note: this involves doing allocations,
use it safely in a `GCStack` context).

It is impossible to have an equivalent of the `JL_TRY` and `JL_CATCH` macros in the JVM,
since they rely on the `setjmp` and `longjmp` C functions.

Any Julia API function calling `jl_error` will result in a JVM crash with
`"fatal: error thrown and no exception handler available"` as an error message.
Most C functions have a wrapper defined in `jlapi.c` which surrounds the call with a
try-catch block.
Use those instead or do your call through a `jl_call`.
