cmake_minimum_required(VERSION 3.22.1)
project("aether_llama_jni" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(aether_llama_jni SHARED
    aether_llama_jni.cpp
    aether_polyglot_indexer_jni.cpp
)

find_library(log-lib log)
if(log-lib)
    target_link_libraries(aether_llama_jni ${log-lib})
endif()
