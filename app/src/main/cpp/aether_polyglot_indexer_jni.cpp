#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

#if __has_include(<jni.h>)
#include <jni.h>
#define AETHER_HAS_REAL_JNI 1
#else
#define AETHER_HAS_REAL_JNI 0
typedef void* JNIEnv;
typedef void* jobject;
typedef const char* jstring;
typedef char* jbyteArray;
typedef int32_t jint;
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
#endif

namespace aether_polyglot {

struct NativeSymbolRecord {
    std::string kind;
    std::string name;
    int32_t line = 1;
};

static std::string trim_copy(const std::string& s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) start++;
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) end--;
    return s.substr(start, end - start);
}

static std::string extract_identifier_after(const std::string& line, const std::string& keyword) {
    size_t pos = line.find(keyword);
    if (pos == std::string::npos) return "";
    // Ensure keyword is at start or preceded by whitespace
    if (pos > 0 && !std::isspace(static_cast<unsigned char>(line[pos - 1]))) return "";
    size_t idx = pos + keyword.size();
    if (idx >= line.size() || !std::isspace(static_cast<unsigned char>(line[idx]))) return "";
    while (idx < line.size() && std::isspace(static_cast<unsigned char>(line[idx]))) idx++;
    // Handle receiver in Go: func (r *Repo) Name(
    if (keyword == "func" && idx < line.size() && line[idx] == '(') {
        size_t close = line.find(')', idx);
        if (close == std::string::npos) return "";
        idx = close + 1;
        while (idx < line.size() && std::isspace(static_cast<unsigned char>(line[idx]))) idx++;
    }
    size_t start = idx;
    while (idx < line.size() &&
           (std::isalnum(static_cast<unsigned char>(line[idx])) || line[idx] == '_')) {
        idx++;
    }
    return idx > start ? line.substr(start, idx - start) : "";
}

std::vector<NativeSymbolRecord> scan_source_symbols(const std::string& content) {
    std::vector<NativeSymbolRecord> out;
    std::istringstream stream(content);
    std::string raw_line;
    int32_t line_no = 0;

    static const std::pair<const char*, const char*> rules[] = {
        {"class", "CLASS"},
        {"interface", "INTERFACE"},
        {"struct", "STRUCT"},
        {"fun", "FUNCTION"},
        {"def", "FUNCTION"},
        {"function", "FUNCTION"},
        {"fn", "FUNCTION"},
        {"func", "FUNCTION"}
    };

    while (std::getline(stream, raw_line)) {
        line_no++;
        std::string line = trim_copy(raw_line);
        if (line.empty() || line.rfind("//", 0) == 0 || line.rfind("#", 0) == 0) continue;
        for (const auto& rule : rules) {
            std::string ident = extract_identifier_after(line, rule.first);
            if (!ident.empty()) {
                out.push_back(NativeSymbolRecord{rule.second, ident, line_no});
                break;
            }
        }
    }
    return out;
}

std::string serialize_symbols_json(const std::vector<NativeSymbolRecord>& symbols) {
    std::ostringstream oss;
    oss << "[";
    for (size_t i = 0; i < symbols.size(); ++i) {
        if (i > 0) oss << ",";
        oss << "{\"kind\":\"" << symbols[i].kind
            << "\",\"name\":\"" << symbols[i].name
            << "\",\"line\":" << symbols[i].line << "}";
    }
    oss << "]";
    return oss.str();
}

} // namespace aether_polyglot

extern "C" {

JNIEXPORT int32_t aether_c_index_symbols(
    const char* source_code,
    char* out_json_buffer,
    int32_t out_buffer_capacity
) {
    if (!source_code) return -1;
    auto symbols = aether_polyglot::scan_source_symbols(std::string(source_code));
    std::string json = aether_polyglot::serialize_symbols_json(symbols);
    if (out_json_buffer && out_buffer_capacity > 0) {
        size_t copy_len = std::min(json.size(), static_cast<size_t>(out_buffer_capacity - 1));
        std::memcpy(out_json_buffer, json.data(), copy_len);
        out_json_buffer[copy_len] = '\0';
    }
    return static_cast<int32_t>(symbols.size());
}

JNIEXPORT jint JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeIndexSymbols(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring source_code,
    jbyteArray out_buffer,
    jint out_buffer_capacity
) {
#if AETHER_HAS_REAL_JNI
    if (!env || !source_code || !out_buffer || out_buffer_capacity <= 0) return -1;
    const char* c_src = env->GetStringUTFChars(source_code, nullptr);
    std::vector<char> temp(static_cast<size_t>(out_buffer_capacity), 0);
    int32_t count = aether_c_index_symbols(c_src, temp.data(), out_buffer_capacity);
    env->ReleaseStringUTFChars(source_code, c_src);
    if (count >= 0) {
        env->SetByteArrayRegion(
            out_buffer,
            0,
            static_cast<jsize>(std::strlen(temp.data())),
            reinterpret_cast<const jbyte*>(temp.data())
        );
    }
    return count;
#else
    (void)env;
    return aether_c_index_symbols(source_code, out_buffer, out_buffer_capacity);
#endif
}

} // extern "C"
