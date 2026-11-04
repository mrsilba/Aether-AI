package com.aether.local.inference

import android.util.Log

/**
 * Native JNI bridge connecting AETHER AI with the optimized C++ GGUF inference engine
 * (aether_llama_jni.cpp) and polyglot code indexer (aether_polyglot_indexer_jni.cpp).
 *
 * Provides safe dynamic binding with graceful fallback when the native shared library
 * is not compiled or present in the current target runtime (e.g. JVM unit tests or
 * pure Android runtimes without precompiled NDK binaries).
 */
object LlamaCppNativeBridge {
    private const val TAG = "LlamaCppNativeBridge"

    @Volatile
    private var isNativeLibraryLoaded = false

    @Volatile
    private var loadedLibraryName: String? = null

    init {
        // Try CMakeLists library name first, then fallback to alternate name
        val candidateNames = listOf("aether_llama_jni", "aether_llama")
        for (name in candidateNames) {
            try {
                System.loadLibrary(name)
                isNativeLibraryLoaded = true
                loadedLibraryName = name
                Log.i(TAG, "Native library 'lib$name.so' successfully loaded.")
                break
            } catch (e: UnsatisfiedLinkError) {
                // Try next candidate
            } catch (e: Exception) {
                Log.w(TAG, "Exception attempting to load native library '$name': ${e.message}")
            }
        }
        if (!isNativeLibraryLoaded) {
            Log.d(TAG, "Native library (libaether_llama_jni.so) not on library path; operating in high-performance runtime fallback mode.")
        }
    }

    val isAvailable: Boolean get() = isNativeLibraryLoaded
    val activeLibraryName: String? get() = loadedLibraryName

    fun getNativeEngineInfo(): String {
        return if (isNativeLibraryLoaded) {
            "Native C++ llama.cpp Engine Active (Loaded: lib$loadedLibraryName.so, Target: ARM64 NEON/DotProd)"
        } else {
            "Runtime Fallback Engine Active (Native NDK binary not linked in current container; GGUF parser operational)"
        }
    }

    external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        threads: Int
    ): Long

    external fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        outBuffer: ByteArray,
        outBufferCapacity: Int
    ): Int

    external fun nativeClearKvCache(handle: Long)

    external fun nativeCancel(handle: Long)

    external fun nativeUnloadModel(handle: Long): Boolean

    external fun nativeIndexSymbols(
        sourceCode: String,
        outBuffer: ByteArray,
        outBufferCapacity: Int
    ): Int
}
