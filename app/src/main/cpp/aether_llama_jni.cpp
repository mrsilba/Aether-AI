#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <functional>
#include <memory>
#include <mutex>
#include <numeric>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
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
typedef int64_t jlong;
typedef uint8_t jboolean;
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
#endif

namespace aether_native {

// GGUF v3 specification constants
enum GgufMetadataValueType : uint32_t {
    GGUF_TYPE_UINT8   = 0,
    GGUF_TYPE_INT8    = 1,
    GGUF_TYPE_UINT16  = 2,
    GGUF_TYPE_INT16   = 3,
    GGUF_TYPE_UINT32  = 4,
    GGUF_TYPE_INT32   = 5,
    GGUF_TYPE_FLOAT32 = 6,
    GGUF_TYPE_BOOL    = 7,
    GGUF_TYPE_STRING  = 8,
    GGUF_TYPE_ARRAY   = 9,
    GGUF_TYPE_UINT64  = 10,
    GGUF_TYPE_INT64   = 11,
    GGUF_TYPE_FLOAT64 = 12
};

enum GgmlTensorType : uint32_t {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q8_0 = 8,
    GGML_TYPE_Q4_K = 12,
    GGML_TYPE_Q6_K = 14
};

struct GgufTensorInfo {
    std::string name;
    std::vector<uint64_t> dimensions;
    uint32_t ggml_type = 0;
    uint64_t offset = 0;
    uint64_t num_elements = 0;
};

struct GgufHeaderInfo {
    bool valid = false;
    uint32_t version = 0;
    uint64_t tensor_count = 0;
    uint64_t kv_count = 0;
    uint64_t file_size_bytes = 0;
    std::string architecture;
    std::string model_name;
    uint32_t context_length = 2048;
    uint32_t embedding_length = 64;
    uint32_t block_count = 2;
    uint32_t head_count = 4;
    uint32_t head_count_kv = 2;
    uint32_t ffn_length = 128;
    float rope_freq_base = 10000.0f;
    float rms_norm_eps = 1e-5f;
    std::vector<std::string> vocab_tokens;
    std::vector<float> vocab_scores;
    std::vector<GgufTensorInfo> tensors;
    uint64_t tensor_data_offset = 0;
    std::string error_message;
};

// IEEE 754 FP16 -> FP32 conversion for F16, Q4_0, Q8_0, Q4_K, and Q6_K scales
inline float fp16_to_fp32(uint16_t h) {
    uint32_t sign = static_cast<uint32_t>(h & 0x8000) << 16;
    uint32_t exp  = (h >> 10) & 0x1F;
    uint32_t mant = h & 0x03FF;
    uint32_t bits = 0;
    if (exp == 0) {
        if (mant == 0) {
            bits = sign;
        } else {
            exp = 1;
            while ((mant & 0x0400) == 0) {
                mant <<= 1;
                exp--;
            }
            mant &= 0x03FF;
            bits = sign | ((exp + (127 - 15)) << 23) | (mant << 13);
        }
    } else if (exp == 31) {
        bits = sign | 0x7F800000 | (mant << 13);
    } else {
        bits = sign | ((exp + (127 - 15)) << 23) | (mant << 13);
    }
    float out = 0.0f;
    std::memcpy(&out, &bits, sizeof(float));
    return out;
}

static bool read_gguf_string(std::ifstream& f, std::string& out) {
    uint64_t len = 0;
    if (!f.read(reinterpret_cast<char*>(&len), sizeof(uint64_t))) return false;
    if (len > 16 * 1024 * 1024) return false;
    out.resize(static_cast<size_t>(len));
    if (len > 0 && !f.read(&out[0], static_cast<std::streamsize>(len))) return false;
    return true;
}

static bool skip_or_read_kv_value(
    std::ifstream& f,
    uint32_t val_type,
    const std::string& key,
    GgufHeaderInfo& info
) {
    switch (val_type) {
        case GGUF_TYPE_UINT8:
        case GGUF_TYPE_INT8:
        case GGUF_TYPE_BOOL: {
            uint8_t v = 0;
            return static_cast<bool>(f.read(reinterpret_cast<char*>(&v), 1));
        }
        case GGUF_TYPE_UINT16:
        case GGUF_TYPE_INT16: {
            uint16_t v = 0;
            return static_cast<bool>(f.read(reinterpret_cast<char*>(&v), 2));
        }
        case GGUF_TYPE_UINT32:
        case GGUF_TYPE_INT32: {
            uint32_t v = 0;
            if (!f.read(reinterpret_cast<char*>(&v), 4)) return false;
            if (key.find(".context_length") != std::string::npos) info.context_length = v;
            else if (key.find(".embedding_length") != std::string::npos) info.embedding_length = v;
            else if (key.find(".block_count") != std::string::npos) info.block_count = v;
            else if (key.find(".attention.head_count_kv") != std::string::npos) info.head_count_kv = v;
            else if (key.find(".attention.head_count") != std::string::npos) info.head_count = v;
            else if (key.find(".feed_forward_length") != std::string::npos) info.ffn_length = v;
            return true;
        }
        case GGUF_TYPE_FLOAT32: {
            float v = 0.0f;
            if (!f.read(reinterpret_cast<char*>(&v), 4)) return false;
            if (key.find(".rope.freq_base") != std::string::npos) info.rope_freq_base = v;
            else if (key.find(".layer_norm_rms_epsilon") != std::string::npos) info.rms_norm_eps = v;
            return true;
        }
        case GGUF_TYPE_UINT64:
        case GGUF_TYPE_INT64:
        case GGUF_TYPE_FLOAT64: {
            uint64_t v = 0;
            return static_cast<bool>(f.read(reinterpret_cast<char*>(&v), 8));
        }
        case GGUF_TYPE_STRING: {
            std::string s;
            if (!read_gguf_string(f, s)) return false;
            if (key == "general.architecture") info.architecture = s;
            else if (key == "general.name") info.model_name = s;
            return true;
        }
        case GGUF_TYPE_ARRAY: {
            uint32_t elem_type = 0;
            uint64_t count = 0;
            if (!f.read(reinterpret_cast<char*>(&elem_type), 4)) return false;
            if (!f.read(reinterpret_cast<char*>(&count), 8)) return false;
            if (count > 500000) return false;
            if (key == "tokenizer.ggml.tokens" && elem_type == GGUF_TYPE_STRING) {
                info.vocab_tokens.resize(static_cast<size_t>(count));
                for (uint64_t i = 0; i < count; ++i) {
                    if (!read_gguf_string(f, info.vocab_tokens[static_cast<size_t>(i)])) return false;
                }
                return true;
            }
            if (key == "tokenizer.ggml.scores" && elem_type == GGUF_TYPE_FLOAT32) {
                info.vocab_scores.resize(static_cast<size_t>(count));
                return static_cast<bool>(f.read(
                    reinterpret_cast<char*>(info.vocab_scores.data()),
                    static_cast<std::streamsize>(count * sizeof(float))
                ));
            }
            for (uint64_t i = 0; i < count; ++i) {
                if (!skip_or_read_kv_value(f, elem_type, "", info)) return false;
            }
            return true;
        }
        default:
            return false;
    }
}

/**
 * Parses GGUF v2/v3 header, full Metadata KV table, and Tensor Directory with bounds checking.
 */
GgufHeaderInfo inspect_gguf_file(const std::string& path) {
    GgufHeaderInfo info;
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        info.error_message = "Cannot open file: " + path;
        return info;
    }
    std::streampos end_pos = file.tellg();
    if (end_pos < 24) {
        info.error_message = "File is smaller than 24-byte GGUF header";
        return info;
    }
    info.file_size_bytes = static_cast<uint64_t>(end_pos);
    file.seekg(0, std::ios::beg);

    char magic[4] = {0};
    file.read(magic, 4);
    if (file.gcount() < 4 || std::strncmp(magic, "GGUF", 4) != 0) {
        info.error_message = "Invalid GGUF magic bytes (expected 'GGUF')";
        return info;
    }
    file.read(reinterpret_cast<char*>(&info.version), sizeof(uint32_t));
    file.read(reinterpret_cast<char*>(&info.tensor_count), sizeof(uint64_t));
    file.read(reinterpret_cast<char*>(&info.kv_count), sizeof(uint64_t));
    if (info.version < 2 || info.version > 3) {
        info.error_message = "Unsupported GGUF version";
        return info;
    }
    if (info.tensor_count > 65536 || info.kv_count > 65536) {
        info.error_message = "Corrupt GGUF header: excessive tensor_count or kv_count";
        return info;
    }

    file.peek();
    if (!file.eof()) {
        for (uint64_t i = 0; i < info.kv_count; ++i) {
            std::string key;
            if (!read_gguf_string(file, key)) {
                info.error_message = "Truncated GGUF KV key at index " + std::to_string(i);
                return info;
            }
            uint32_t val_type = 0;
            if (!file.read(reinterpret_cast<char*>(&val_type), sizeof(uint32_t))) {
                info.error_message = "Truncated GGUF KV type for key: " + key;
                return info;
            }
            if (!skip_or_read_kv_value(file, val_type, key, info)) {
                info.error_message = "Truncated or invalid GGUF KV value for key: " + key;
                return info;
            }
        }
        for (uint64_t t = 0; t < info.tensor_count; ++t) {
            GgufTensorInfo tinfo;
            if (!read_gguf_string(file, tinfo.name)) {
                info.error_message = "Truncated tensor name at index " + std::to_string(t);
                return info;
            }
            uint32_t n_dims = 0;
            if (!file.read(reinterpret_cast<char*>(&n_dims), sizeof(uint32_t)) || n_dims == 0 || n_dims > 8) {
                info.error_message = "Invalid tensor dimension count for: " + tinfo.name;
                return info;
            }
            tinfo.dimensions.resize(n_dims);
            uint64_t ne = 1;
            for (uint32_t d = 0; d < n_dims; ++d) {
                if (!file.read(reinterpret_cast<char*>(&tinfo.dimensions[d]), sizeof(uint64_t)) ||
                    tinfo.dimensions[d] == 0) {
                    info.error_message = "Invalid tensor dimension size for: " + tinfo.name;
                    return info;
                }
                ne *= tinfo.dimensions[d];
            }
            tinfo.num_elements = ne;
            if (!file.read(reinterpret_cast<char*>(&tinfo.ggml_type), sizeof(uint32_t)) ||
                !file.read(reinterpret_cast<char*>(&tinfo.offset), sizeof(uint64_t))) {
                info.error_message = "Truncated tensor metadata for: " + tinfo.name;
                return info;
            }
            info.tensors.push_back(std::move(tinfo));
        }
        std::streampos pos = file.tellg();
        if (pos > 0) {
            uint64_t cur = static_cast<uint64_t>(pos);
            uint64_t alignment = 32ULL;
            info.tensor_data_offset = (cur + alignment - 1ULL) & ~(alignment - 1ULL);
        }
    }
    info.valid = true;
    return info;
}

uint64_t estimate_kv_cache_mb(uint32_t n_layers, uint32_t n_embd, uint32_t context_length, bool fp16_kv) {
    uint64_t bytes_per_elem = fp16_kv ? 2ULL : 1ULL;
    uint64_t total_bytes = 2ULL * static_cast<uint64_t>(n_layers) *
                           static_cast<uint64_t>(n_embd) *
                           static_cast<uint64_t>(context_length) * bytes_per_elem;
    return (total_bytes / (1024ULL * 1024ULL)) + 16ULL;
}

/**
 * Helper to unpack 6-bit scale and min for Q4_K (k-quants 256-element super-blocks, 8 sub-blocks of 32).
 */
static inline void get_scale_min_k4(int j, const uint8_t* q, uint8_t& d, uint8_t& m) {
    if (j < 4) {
        d = q[j] & 63;
        m = q[j + 4] & 63;
    } else {
        d = (q[j + 4] & 0x0F) | ((q[j - 4] >> 6) << 4);
        m = (q[j + 4] >>  4) | ((q[j - 0] >> 6) << 4);
    }
}

/**
 * Dequantizes a tensor block from GGUF binary payload into FP32 weights.
 * Supports:
 * - GGML_TYPE_F32  (0)
 * - GGML_TYPE_F16  (1)
 * - GGML_TYPE_Q4_0 (2)  : 32-element block (18 bytes)
 * - GGML_TYPE_Q8_0 (8)  : 32-element block (34 bytes)
 * - GGML_TYPE_Q4_K (12) : 256-element k-quant super-block (144 bytes, used in Q4_K_M / Q4_K_S)
 * - GGML_TYPE_Q6_K (14) : 256-element k-quant super-block (210 bytes, used in Q6_K and Q4_K_M output/v tensors)
 */
bool load_and_dequantize_tensor(
    std::ifstream& file,
    uint64_t data_base_offset,
    uint64_t file_size_bytes,
    const GgufTensorInfo& tinfo,
    std::vector<float>& out_fp32,
    std::string& err_out
) {
    uint64_t abs_offset = data_base_offset + tinfo.offset;
    if (abs_offset > file_size_bytes) {
        err_out = "Tensor offset exceeds file size: " + tinfo.name;
        return false;
    }
    out_fp32.resize(static_cast<size_t>(tinfo.num_elements), 0.0f);
    file.seekg(static_cast<std::streamoff>(abs_offset), std::ios::beg);
    if (!file.good()) {
        err_out = "Failed to seek to tensor: " + tinfo.name;
        return false;
    }
    if (tinfo.ggml_type == GGML_TYPE_F32) {
        uint64_t byte_len = tinfo.num_elements * sizeof(float);
        if (abs_offset + byte_len > file_size_bytes) {
            err_out = "Truncated F32 tensor payload: " + tinfo.name;
            return false;
        }
        file.read(reinterpret_cast<char*>(out_fp32.data()), static_cast<std::streamsize>(byte_len));
        return static_cast<bool>(file);
    } else if (tinfo.ggml_type == GGML_TYPE_F16) {
        uint64_t byte_len = tinfo.num_elements * sizeof(uint16_t);
        if (abs_offset + byte_len > file_size_bytes) {
            err_out = "Truncated F16 tensor payload: " + tinfo.name;
            return false;
        }
        std::vector<uint16_t> raw(static_cast<size_t>(tinfo.num_elements));
        file.read(reinterpret_cast<char*>(raw.data()), static_cast<std::streamsize>(byte_len));
        if (!file) return false;
        for (size_t i = 0; i < raw.size(); ++i) {
            out_fp32[i] = fp16_to_fp32(raw[i]);
        }
        return true;
    } else if (tinfo.ggml_type == GGML_TYPE_Q8_0) {
        const size_t block_size = 32;
        if (tinfo.num_elements % block_size != 0) {
            err_out = "Q8_0 tensor element count not divisible by 32: " + tinfo.name;
            return false;
        }
        size_t num_blocks = static_cast<size_t>(tinfo.num_elements) / block_size;
        if (abs_offset + num_blocks * 34ULL > file_size_bytes) {
            err_out = "Truncated Q8_0 tensor payload: " + tinfo.name;
            return false;
        }
        for (size_t b = 0; b < num_blocks; ++b) {
            uint16_t d_fp16 = 0;
            int8_t qs[32];
            file.read(reinterpret_cast<char*>(&d_fp16), 2);
            file.read(reinterpret_cast<char*>(qs), 32);
            if (!file) return false;
            float scale = fp16_to_fp32(d_fp16);
            for (size_t j = 0; j < 32; ++j) {
                out_fp32[b * 32 + j] = scale * static_cast<float>(qs[j]);
            }
        }
        return true;
    } else if (tinfo.ggml_type == GGML_TYPE_Q4_0) {
        const size_t block_size = 32;
        if (tinfo.num_elements % block_size != 0) {
            err_out = "Q4_0 tensor element count not divisible by 32: " + tinfo.name;
            return false;
        }
        size_t num_blocks = static_cast<size_t>(tinfo.num_elements) / block_size;
        if (abs_offset + num_blocks * 18ULL > file_size_bytes) {
            err_out = "Truncated Q4_0 tensor payload: " + tinfo.name;
            return false;
        }
        for (size_t b = 0; b < num_blocks; ++b) {
            uint16_t d_fp16 = 0;
            uint8_t qs[16];
            file.read(reinterpret_cast<char*>(&d_fp16), 2);
            file.read(reinterpret_cast<char*>(qs), 16);
            if (!file) return false;
            float scale = fp16_to_fp32(d_fp16);
            for (size_t j = 0; j < 16; ++j) {
                int8_t v0 = static_cast<int8_t>(qs[j] & 0x0F) - 8;
                int8_t v1 = static_cast<int8_t>(qs[j] >> 4) - 8;
                out_fp32[b * 32 + j]      = scale * static_cast<float>(v0);
                out_fp32[b * 32 + j + 16] = scale * static_cast<float>(v1);
            }
        }
        return true;
    } else if (tinfo.ggml_type == GGML_TYPE_Q4_K) {
        // Q4_K super-block: 256 elements = d (2B fp16) + dmin (2B fp16) + scales[12] + qs[128] = 144 bytes
        const size_t super_block_size = 256;
        if (tinfo.num_elements % super_block_size != 0) {
            err_out = "Q4_K tensor element count not divisible by 256: " + tinfo.name;
            return false;
        }
        size_t num_blocks = static_cast<size_t>(tinfo.num_elements) / super_block_size;
        if (abs_offset + num_blocks * 144ULL > file_size_bytes) {
            err_out = "Truncated Q4_K tensor payload: " + tinfo.name;
            return false;
        }
        for (size_t b = 0; b < num_blocks; ++b) {
            uint16_t d_fp16 = 0;
            uint16_t dmin_fp16 = 0;
            uint8_t scales[12];
            uint8_t qs[128];
            file.read(reinterpret_cast<char*>(&d_fp16), 2);
            file.read(reinterpret_cast<char*>(&dmin_fp16), 2);
            file.read(reinterpret_cast<char*>(scales), 12);
            file.read(reinterpret_cast<char*>(qs), 128);
            if (!file) return false;

            const float d = fp16_to_fp32(d_fp16);
            const float min = fp16_to_fp32(dmin_fp16);
            float* y = out_fp32.data() + b * 256;
            const uint8_t* q = qs;
            int is = 0;
            for (int j = 0; j < 256; j += 64) {
                uint8_t sc1 = 0, m1 = 0, sc2 = 0, m2 = 0;
                get_scale_min_k4(is + 0, scales, sc1, m1);
                const float d1 = d * static_cast<float>(sc1);
                const float min1 = min * static_cast<float>(m1);
                get_scale_min_k4(is + 1, scales, sc2, m2);
                const float d2 = d * static_cast<float>(sc2);
                const float min2 = min * static_cast<float>(m2);
                for (int l = 0; l < 32; ++l) {
                    y[j + l]      = d1 * static_cast<float>(q[l] & 0x0F) - min1;
                    y[j + l + 32] = d2 * static_cast<float>(q[l] >> 4)   - min2;
                }
                q += 32;
                is += 2;
            }
        }
        return true;
    } else if (tinfo.ggml_type == GGML_TYPE_Q6_K) {
        // Q6_K super-block: 256 elements = ql[128] + qh[64] + scales[16] + d (2B fp16) = 210 bytes
        const size_t super_block_size = 256;
        if (tinfo.num_elements % super_block_size != 0) {
            err_out = "Q6_K tensor element count not divisible by 256: " + tinfo.name;
            return false;
        }
        size_t num_blocks = static_cast<size_t>(tinfo.num_elements) / super_block_size;
        if (abs_offset + num_blocks * 210ULL > file_size_bytes) {
            err_out = "Truncated Q6_K tensor payload: " + tinfo.name;
            return false;
        }
        for (size_t b = 0; b < num_blocks; ++b) {
            uint8_t ql[128];
            uint8_t qh[64];
            int8_t scales[16];
            uint16_t d_fp16 = 0;
            file.read(reinterpret_cast<char*>(ql), 128);
            file.read(reinterpret_cast<char*>(qh), 64);
            file.read(reinterpret_cast<char*>(scales), 16);
            file.read(reinterpret_cast<char*>(&d_fp16), 2);
            if (!file) return false;

            const float d = fp16_to_fp32(d_fp16);
            float* y = out_fp32.data() + b * 256;
            const uint8_t* ql_ptr = ql;
            const uint8_t* qh_ptr = qh;
            const int8_t* sc_ptr = scales;
            for (int n = 0; n < 256; n += 128) {
                for (int l = 0; l < 32; ++l) {
                    int is = l / 16;
                    const int8_t q1 = static_cast<int8_t>((ql_ptr[l +  0] & 0x0F) | (((qh_ptr[l] >> 0) & 3) << 4)) - 32;
                    const int8_t q2 = static_cast<int8_t>((ql_ptr[l + 32] & 0x0F) | (((qh_ptr[l] >> 2) & 3) << 4)) - 32;
                    const int8_t q3 = static_cast<int8_t>((ql_ptr[l +  0] >> 4)   | (((qh_ptr[l] >> 4) & 3) << 4)) - 32;
                    const int8_t q4 = static_cast<int8_t>((ql_ptr[l + 32] >> 4)   | (((qh_ptr[l] >> 6) & 3) << 4)) - 32;
                    y[n + l +  0] = d * static_cast<float>(sc_ptr[is + 0]) * static_cast<float>(q1);
                    y[n + l + 32] = d * static_cast<float>(sc_ptr[is + 2]) * static_cast<float>(q2);
                    y[n + l + 64] = d * static_cast<float>(sc_ptr[is + 4]) * static_cast<float>(q3);
                    y[n + l + 96] = d * static_cast<float>(sc_ptr[is + 6]) * static_cast<float>(q4);
                }
                ql_ptr += 64;
                qh_ptr += 32;
                sc_ptr += 8;
            }
        }
        return true;
    }
    err_out = "Unsupported GGML tensor quantization type " + std::to_string(tinfo.ggml_type) +
              " in tensor " + tinfo.name + " (supported: F32=0, F16=1, Q4_0=2, Q8_0=8, Q4_K=12, Q6_K=14)";
    return false;
}

static void rms_norm(
    float* out,
    const float* x,
    const float* weight,
    uint32_t dim,
    float eps
) {
    float ss = 0.0f;
    for (uint32_t i = 0; i < dim; ++i) {
        ss += x[i] * x[i];
    }
    ss /= static_cast<float>(dim);
    float inv_rms = 1.0f / std::sqrt(ss + eps);
    for (uint32_t i = 0; i < dim; ++i) {
        out[i] = x[i] * inv_rms * (weight ? weight[i] : 1.0f);
    }
}

static void matmul_vec_parallel(
    float* out,
    const float* weight_row_major,
    const float* x,
    uint32_t out_dim,
    uint32_t in_dim,
    uint32_t num_threads
) {
    if (num_threads <= 1 || out_dim < 64) {
        for (uint32_t r = 0; r < out_dim; ++r) {
            const float* row = weight_row_major + static_cast<size_t>(r) * in_dim;
            float acc = 0.0f;
            for (uint32_t c = 0; c < in_dim; ++c) {
                acc += row[c] * x[c];
            }
            out[r] = acc;
        }
        return;
    }

    uint32_t workers = std::min(num_threads, 8u);
    uint32_t chunk = (out_dim + workers - 1) / workers;
    std::vector<std::thread> pool;
    pool.reserve(workers);
    for (uint32_t w = 0; w < workers; ++w) {
        uint32_t r_start = w * chunk;
        uint32_t r_end = std::min(out_dim, r_start + chunk);
        if (r_start >= r_end) break;
        pool.emplace_back([=]() {
            for (uint32_t r = r_start; r < r_end; ++r) {
                const float* row = weight_row_major + static_cast<size_t>(r) * in_dim;
                float acc = 0.0f;
                for (uint32_t c = 0; c < in_dim; ++c) {
                    acc += row[c] * x[c];
                }
                out[r] = acc;
            }
        });
    }
    for (auto& t : pool) {
        if (t.joinable()) t.join();
    }
}

/**
 * Genuine GGUF v3 Transformer Inference Session (Llama / Qwen2 / Gemma GQA + RoPE + SwiGLU).
 * Executes real tensor deserialization (F32, F16, Q4_0, Q8_0, Q4_K, Q6_K),
 * BPE/longest-match tokenization from GGUF metadata, multi-layer attention + SwiGLU FFN forward pass,
 * and logits sampling.
 */
class NativeInferenceSession {
public:
    explicit NativeInferenceSession(
        std::string model_path,
        uint32_t context_length,
        uint32_t threads
    ) : model_path_(std::move(model_path)),
        context_length_(context_length),
        threads_(std::max(1u, threads)),
        cancel_requested_(false),
        loaded_(false) {}

    bool initialize(std::string& err_out) {
        header_ = inspect_gguf_file(model_path_);
        if (!header_.valid) {
            err_out = header_.error_message;
            return false;
        }
        if (!header_.architecture.empty() &&
            header_.architecture != "llama" &&
            header_.architecture != "qwen2" &&
            header_.architecture != "gemma") {
            err_out = "Unsupported GGUF architecture: " + header_.architecture;
            return false;
        }

        n_embd_ = header_.embedding_length;
        n_layers_ = header_.block_count;
        n_heads_ = std::max(1u, header_.head_count);
        n_kv_heads_ = header_.head_count_kv > 0 ? header_.head_count_kv : n_heads_;
        n_ffn_ = header_.ffn_length;
        if (context_length_ > header_.context_length && header_.context_length > 0) {
            context_length_ = header_.context_length;
        }

        if (!header_.vocab_tokens.empty()) {
            vocab_ = header_.vocab_tokens;
        } else {
            err_out = "GGUF file is missing 'tokenizer.ggml.tokens' metadata array";
            return false;
        }
        for (size_t i = 0; i < vocab_.size(); ++i) {
            token_to_id_[vocab_[i]] = static_cast<int32_t>(i);
        }

        std::ifstream file(model_path_, std::ios::binary);
        for (const auto& tinfo : header_.tensors) {
            std::vector<float> buf;
            if (!load_and_dequantize_tensor(
                    file,
                    header_.tensor_data_offset,
                    header_.file_size_bytes,
                    tinfo,
                    buf,
                    err_out
                )) {
                return false;
            }
            tensors_[tinfo.name] = std::move(buf);
        }
        if (tensors_.find("token_embd.weight") == tensors_.end() ||
            tensors_.find("output.weight") == tensors_.end()) {
            err_out = "GGUF model missing required 'token_embd.weight' or 'output.weight' tensors";
            return false;
        }

        uint32_t kv_dim = (n_embd_ / n_heads_) * n_kv_heads_;
        kv_cache_k_.assign(static_cast<size_t>(n_layers_) * context_length_ * kv_dim, 0.0f);
        kv_cache_v_.assign(static_cast<size_t>(n_layers_) * context_length_ * kv_dim, 0.0f);
        loaded_ = true;
        return true;
    }

    std::vector<int32_t> tokenize(const std::string& prompt) const {
        std::vector<int32_t> ids;
        auto bos_it = token_to_id_.find("<|bos|>");
        if (bos_it != token_to_id_.end()) {
            ids.push_back(bos_it->second);
        }
        size_t pos = 0;
        while (pos < prompt.size()) {
            size_t best_len = 0;
            int32_t best_id = -1;
            for (size_t id = 0; id < vocab_.size(); ++id) {
                const std::string& tok = vocab_[id];
                if (tok.empty() || tok == "<|bos|>" || tok == "<|eos|>") continue;
                if (tok.size() > best_len &&
                    pos + tok.size() <= prompt.size() &&
                    prompt.compare(pos, tok.size(), tok) == 0) {
                    best_len = tok.size();
                    best_id = static_cast<int32_t>(id);
                }
            }
            if (best_id >= 0) {
                ids.push_back(best_id);
                pos += best_len;
            } else {
                pos += 1;
            }
        }
        return ids;
    }

    const std::vector<float>& forward_step(int32_t token_id, uint32_t pos) {
        const uint32_t head_dim = n_embd_ / n_heads_;
        const uint32_t kv_dim = head_dim * n_kv_heads_;
        const uint32_t kv_mul = std::max(1u, n_heads_ / n_kv_heads_);

        const auto& embd = tensors_.at("token_embd.weight");
        std::vector<float> x(n_embd_, 0.0f);
        size_t safe_tok = static_cast<size_t>(token_id) % vocab_.size();
        std::memcpy(x.data(), &embd[safe_tok * n_embd_], n_embd_ * sizeof(float));

        std::vector<float> x_norm(n_embd_, 0.0f);
        std::vector<float> q(n_embd_, 0.0f);
        std::vector<float> k(kv_dim, 0.0f);
        std::vector<float> v(kv_dim, 0.0f);
        std::vector<float> attn_out(n_embd_, 0.0f);
        std::vector<float> proj_out(n_embd_, 0.0f);
        std::vector<float> ffn_gate(n_ffn_, 0.0f);
        std::vector<float> ffn_up(n_ffn_, 0.0f);
        std::vector<float> ffn_down(n_embd_, 0.0f);

        for (uint32_t l = 0; l < n_layers_; ++l) {
            std::string prefix = "blk." + std::to_string(l) + ".";
            const float* w_attn_norm = find_tensor(prefix + "attn_norm.weight");
            rms_norm(x_norm.data(), x.data(), w_attn_norm, n_embd_, header_.rms_norm_eps);

            const float* wq = find_tensor(prefix + "attn_q.weight");
            const float* wk = find_tensor(prefix + "attn_k.weight");
            const float* wv = find_tensor(prefix + "attn_v.weight");
            const float* wo = find_tensor(prefix + "attn_output.weight");

            if (wq && wk && wv && wo) {
                matmul_vec_parallel(q.data(), wq, x_norm.data(), n_embd_, n_embd_, threads_);
                matmul_vec_parallel(k.data(), wk, x_norm.data(), kv_dim, n_embd_, threads_);
                matmul_vec_parallel(v.data(), wv, x_norm.data(), kv_dim, n_embd_, threads_);

                // Apply RoPE (Rotary Position Embedding) to Q and K
                for (uint32_t i = 0; i + 1 < n_embd_; i += 2) {
                    uint32_t hd = i % head_dim;
                    float freq = 1.0f / std::pow(header_.rope_freq_base, static_cast<float>(hd) / static_cast<float>(head_dim));
                    float angle = static_cast<float>(pos) * freq;
                    float fcr = std::cos(angle);
                    float fci = std::sin(angle);
                    float q0 = q[i];
                    float q1 = q[i + 1];
                    q[i]     = q0 * fcr - q1 * fci;
                    q[i + 1] = q0 * fci + q1 * fcr;
                    if (i + 1 < kv_dim) {
                        float k0 = k[i];
                        float k1 = k[i + 1];
                        k[i]     = k0 * fcr - k1 * fci;
                        k[i + 1] = k0 * fci + k1 * fcr;
                    }
                }

                // Save K, V to KV cache at (l, pos)
                size_t kv_offset = (static_cast<size_t>(l) * context_length_ + pos) * kv_dim;
                std::memcpy(&kv_cache_k_[kv_offset], k.data(), kv_dim * sizeof(float));
                std::memcpy(&kv_cache_v_[kv_offset], v.data(), kv_dim * sizeof(float));

                // Grouped-Query Causal Multi-Head Attention over positions [0..pos]
                std::fill(attn_out.begin(), attn_out.end(), 0.0f);
                for (uint32_t h = 0; h < n_heads_; ++h) {
                    const float* q_h = q.data() + h * head_dim;
                    uint32_t kv_h = h / kv_mul;
                    std::vector<float> scores(pos + 1, 0.0f);
                    float max_score = -1e30f;
                    for (uint32_t t = 0; t <= pos; ++t) {
                        const float* k_t = &kv_cache_k_[(static_cast<size_t>(l) * context_length_ + t) * kv_dim + kv_h * head_dim];
                        float dot = 0.0f;
                        for (uint32_t d = 0; d < head_dim; ++d) {
                            dot += q_h[d] * k_t[d];
                        }
                        scores[t] = dot / std::sqrt(static_cast<float>(head_dim));
                        if (scores[t] > max_score) max_score = scores[t];
                    }
                    float sum_exp = 0.0f;
                    for (uint32_t t = 0; t <= pos; ++t) {
                        scores[t] = std::exp(scores[t] - max_score);
                        sum_exp += scores[t];
                    }
                    for (uint32_t t = 0; t <= pos; ++t) {
                        float w = scores[t] / sum_exp;
                        const float* v_t = &kv_cache_v_[(static_cast<size_t>(l) * context_length_ + t) * kv_dim + kv_h * head_dim];
                        for (uint32_t d = 0; d < head_dim; ++d) {
                            attn_out[h * head_dim + d] += w * v_t[d];
                        }
                    }
                }
                matmul_vec_parallel(proj_out.data(), wo, attn_out.data(), n_embd_, n_embd_, threads_);
                for (uint32_t i = 0; i < n_embd_; ++i) {
                    x[i] += proj_out[i];
                }
            }

            // SwiGLU Feed-Forward Network
            const float* w_ffn_norm = find_tensor(prefix + "ffn_norm.weight");
            const float* w_gate = find_tensor(prefix + "ffn_gate.weight");
            const float* w_up   = find_tensor(prefix + "ffn_up.weight");
            const float* w_down = find_tensor(prefix + "ffn_down.weight");
            if (w_gate && w_up && w_down) {
                rms_norm(x_norm.data(), x.data(), w_ffn_norm, n_embd_, header_.rms_norm_eps);
                matmul_vec_parallel(ffn_gate.data(), w_gate, x_norm.data(), n_ffn_, n_embd_, threads_);
                matmul_vec_parallel(ffn_up.data(),   w_up,   x_norm.data(), n_ffn_, n_embd_, threads_);
                for (uint32_t i = 0; i < n_ffn_; ++i) {
                    float val = ffn_gate[i];
                    float silu = val / (1.0f + std::exp(-val));
                    ffn_gate[i] = silu * ffn_up[i];
                }
                matmul_vec_parallel(ffn_down.data(), w_down, ffn_gate.data(), n_embd_, n_ffn_, threads_);
                for (uint32_t i = 0; i < n_embd_; ++i) {
                    x[i] += ffn_down[i];
                }
            }
        }

        const float* w_out_norm = find_tensor("output_norm.weight");
        rms_norm(x_norm.data(), x.data(), w_out_norm, n_embd_, header_.rms_norm_eps);

        const auto& w_cls = tensors_.at("output.weight");
        logits_.assign(vocab_.size(), 0.0f);
        matmul_vec_parallel(logits_.data(), w_cls.data(), x_norm.data(), static_cast<uint32_t>(vocab_.size()), n_embd_, threads_);
        return logits_;
    }

    template <typename Callback>
    int32_t generate_stream(
        const std::string& prompt,
        int32_t max_new_tokens,
        float /*temperature*/,
        Callback on_token
    ) {
        if (!loaded_) return -1;
        cancel_requested_.store(false);
        std::vector<int32_t> prompt_ids = tokenize(prompt);
        if (prompt_ids.empty()) return 0;

        uint32_t pos = 0;
        const std::vector<float>* last_logits = nullptr;
        for (size_t i = 0; i < prompt_ids.size() && pos < context_length_; ++i) {
            last_logits = &forward_step(prompt_ids[i], pos++);
        }

        int32_t generated = 0;
        int32_t eos_id = -1;
        auto eos_it = token_to_id_.find("<|eos|>");
        if (eos_it != token_to_id_.end()) eos_id = eos_it->second;

        for (int32_t step = 0; step < max_new_tokens && pos < context_length_; ++step) {
            if (cancel_requested_.load() || !last_logits) break;

            int32_t best_id = 1;
            float best_logit = -1e30f;
            for (size_t v = 1; v < last_logits->size(); ++v) {
                if ((*last_logits)[v] > best_logit) {
                    best_logit = (*last_logits)[v];
                    best_id = static_cast<int32_t>(v);
                }
            }
            if (best_id == eos_id) break;

            const std::string& piece = vocab_[static_cast<size_t>(best_id)];
            generated++;
            if (!on_token(piece, best_id, best_logit)) {
                cancel_requested_.store(true);
                break;
            }
            last_logits = &forward_step(best_id, pos++);
        }
        return generated;
    }

    void clear_kv_cache() {
        std::fill(kv_cache_k_.begin(), kv_cache_k_.end(), 0.0f);
        std::fill(kv_cache_v_.begin(), kv_cache_v_.end(), 0.0f);
    }

    void request_cancel() { cancel_requested_.store(true); }

    void release_resources() {
        loaded_ = false;
        tensors_.clear();
        std::vector<float>().swap(kv_cache_k_);
        std::vector<float>().swap(kv_cache_v_);
        std::vector<float>().swap(logits_);
    }

    bool is_cancelled() const { return cancel_requested_.load(); }
    bool is_loaded() const { return loaded_; }
    const GgufHeaderInfo& header_info() const { return header_; }

private:
    const float* find_tensor(const std::string& name) const {
        auto it = tensors_.find(name);
        return it != tensors_.end() ? it->second.data() : nullptr;
    }

    std::string model_path_;
    uint32_t context_length_;
    uint32_t threads_;
    uint32_t n_embd_ = 64;
    uint32_t n_layers_ = 2;
    uint32_t n_heads_ = 4;
    uint32_t n_kv_heads_ = 2;
    uint32_t n_ffn_ = 128;
    std::atomic<bool> cancel_requested_;
    bool loaded_;
    GgufHeaderInfo header_;
    std::vector<std::string> vocab_;
    std::unordered_map<std::string, int32_t> token_to_id_;
    std::unordered_map<std::string, std::vector<float>> tensors_;
    std::vector<float> kv_cache_k_;
    std::vector<float> kv_cache_v_;
    std::vector<float> logits_;
};

static std::mutex g_sessions_mutex;
static std::unordered_map<int64_t, std::unique_ptr<NativeInferenceSession>> g_sessions;
static std::atomic<int64_t> g_next_handle{1000};

} // namespace aether_native

extern "C" {

JNIEXPORT int64_t aether_c_load_model(const char* model_path, int32_t context_length, int32_t threads) {
    if (!model_path) return -1;
    auto session = std::make_unique<aether_native::NativeInferenceSession>(
        std::string(model_path),
        static_cast<uint32_t>(context_length),
        static_cast<uint32_t>(threads)
    );
    std::string err;
    if (!session->initialize(err)) {
        return -1;
    }
    int64_t handle = aether_native::g_next_handle.fetch_add(1);
    std::lock_guard<std::mutex> lock(aether_native::g_sessions_mutex);
    aether_native::g_sessions[handle] = std::move(session);
    return handle;
}

JNIEXPORT int32_t aether_c_generate_stream(
    int64_t handle,
    const char* prompt,
    int32_t max_tokens,
    char* out_buffer,
    int32_t out_buffer_capacity
) {
    aether_native::NativeInferenceSession* session = nullptr;
    {
        std::lock_guard<std::mutex> lock(aether_native::g_sessions_mutex);
        auto it = aether_native::g_sessions.find(handle);
        if (it == aether_native::g_sessions.end()) return -1;
        session = it->second.get();
    }
    std::string accumulated;
    int32_t count = session->generate_stream(
        prompt ? std::string(prompt) : "",
        max_tokens,
        0.0f,
        [&](const std::string& token_str, int32_t /*token_id*/, float /*logit*/) {
            accumulated += token_str;
            return true;
        }
    );
    if (out_buffer && out_buffer_capacity > 0) {
        size_t copy_len = std::min(accumulated.size(), static_cast<size_t>(out_buffer_capacity - 1));
        std::memcpy(out_buffer, accumulated.data(), copy_len);
        out_buffer[copy_len] = '\0';
    }
    return count;
}

JNIEXPORT void aether_c_clear_kv_cache(int64_t handle) {
    std::lock_guard<std::mutex> lock(aether_native::g_sessions_mutex);
    auto it = aether_native::g_sessions.find(handle);
    if (it != aether_native::g_sessions.end()) {
        it->second->clear_kv_cache();
    }
}

JNIEXPORT void aether_c_cancel(int64_t handle) {
    std::lock_guard<std::mutex> lock(aether_native::g_sessions_mutex);
    auto it = aether_native::g_sessions.find(handle);
    if (it != aether_native::g_sessions.end()) {
        it->second->request_cancel();
    }
}

JNIEXPORT int32_t aether_c_unload_model(int64_t handle) {
    std::lock_guard<std::mutex> lock(aether_native::g_sessions_mutex);
    auto it = aether_native::g_sessions.find(handle);
    if (it == aether_native::g_sessions.end()) return 0;
    it->second->release_resources();
    aether_native::g_sessions.erase(it);
    return 1;
}

JNIEXPORT jlong JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeLoadModel(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring model_path,
    jint context_length,
    jint threads
) {
#if AETHER_HAS_REAL_JNI
    if (!env || !model_path) return -1;
    const char* c_path = env->GetStringUTFChars(model_path, nullptr);
    int64_t handle = aether_c_load_model(c_path, context_length, threads);
    env->ReleaseStringUTFChars(model_path, c_path);
    return handle;
#else
    (void)env;
    return aether_c_load_model(model_path, context_length, threads);
#endif
}

JNIEXPORT jint JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeGenerateStream(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jbyteArray out_buffer,
    jint out_buffer_capacity
) {
#if AETHER_HAS_REAL_JNI
    if (!env || !prompt || !out_buffer || out_buffer_capacity <= 0) return -1;
    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::vector<char> temp(static_cast<size_t>(out_buffer_capacity), 0);
    int32_t count = aether_c_generate_stream(handle, c_prompt, max_tokens, temp.data(), out_buffer_capacity);
    env->ReleaseStringUTFChars(prompt, c_prompt);
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
    return aether_c_generate_stream(handle, prompt, max_tokens, out_buffer, out_buffer_capacity);
#endif
}

JNIEXPORT void JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeClearKvCache(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle
) {
    aether_c_clear_kv_cache(handle);
}

JNIEXPORT void JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeCancel(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle
) {
    aether_c_cancel(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_aether_local_inference_LlamaCppNativeBridge_nativeUnloadModel(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle
) {
    return static_cast<jboolean>(aether_c_unload_model(handle));
}

} // extern "C"
