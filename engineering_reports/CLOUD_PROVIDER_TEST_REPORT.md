# AETHER AI v3.0 — Cloud AI Provider Architecture & Security Audit

**Audit Date**: 2026-11-04  
**Subject**: Multi-LLM Cloud Streaming, Android Keystore Vault, Token Economics, and Resilience  
**Auditor**: Senior Cloud Infrastructure & Security Engineer  

---

## 1. Subsystem Architecture

`CloudAiProviderManager.kt` provides an unified, vendor-agnostic HTTP streaming interface across 5 major enterprise AI APIs:
1. **Google Gemini** (`gemini-2.5-flash`, `gemini-1.5-pro`)
2. **Anthropic Claude** (`claude-3-5-sonnet`, `claude-3-haiku`)
3. **OpenAI** (`gpt-4o`, `gpt-4o-mini`)
4. **DeepSeek** (`deepseek-chat`, `deepseek-coder`)
5. **OpenRouter** (Aggregated unified router)

---

## 2. Server-Sent Events (SSE) Streaming Verification

Each provider utilizes distinct streaming wire formats. The manager parses these directly from the raw chunked HTTP response body:

### 2.1 Google Gemini Protocol
- **Endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse&key={apiKey}`
- **Wire Format**: Lines beginning with `data: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}`
- **Parser Verification**: Tested against multiline JSON objects; extracts delta tokens while ignoring empty heartbeat events.

### 2.2 Anthropic Claude Protocol
- **Endpoint**: `https://api.anthropic.com/v1/messages` with header `anthropic-version: 2023-06-01`
- **Wire Format**: Event-driven SSE:
  ```
  event: content_block_delta
  data: {"type": "content_block_delta", "delta": {"type": "text_delta", "text": "..."}}
  ```
- **Parser Verification**: Filters on `content_block_delta` and extracts `delta.text` directly into the downstream flow.

### 2.3 OpenAI / DeepSeek / OpenRouter Protocol
- **Endpoint**: `/v1/chat/completions` with header `stream: true`
- **Wire Format**:
  ```
  data: {"choices": [{"delta": {"content": "..."}}]}
  data: [DONE]
  ```
- **Parser Verification**: Detects `[DONE]` termination token to gracefully close the coroutine channel.

---

## 3. Cryptographic Storage & Key Protection

To protect API credentials from being extracted by malicious on-device software or unprivileged processes:

1. **Hardware-Backed Master Key**:
   - `SecurityEngine.kt` uses the `AndroidKeyStore` provider to generate or retrieve an `AES/GCM/NoPadding` 256-bit key with key alias `"aether_master_cipher_key"`.
   - Key material is held inside the device's Secure Element (SE) / Hardware Security Module (HSM) and cannot be exported as plaintext bytes.
2. **Authenticated Encryption (AES-GCM)**:
   - Each credential is encrypted using a unique, cryptographically random 12-byte Initialization Vector (IV).
   - The cipher outputs a 128-bit authentication tag protecting against ciphertext tampering.
   - The IV is prepended in hex format to the ciphertext before storage in local encrypted preferences:
     $$\text{Stored Value} = \text{Hex}(\text{IV}) + \text{":"} + \text{Hex}(\text{Ciphertext} \parallel \text{Tag})$$
3. **Log Sanitization**:
   - `SecretScanner.redactSecrets()` runs on all outgoing and incoming log strings, masking OpenAI (`sk-...`), GitHub (`ghp_...`), and Google (`AIzaSy...`) key patterns with `[REDACTED]`.

---

## 4. Token Cost Accounting

The manager tracks token usage and computes accurate session costs:

| Provider / Model | Input Cost / 1M Tokens | Output Cost / 1M Tokens | Pricing Tier |
| :--- | :--- | :--- | :--- |
| **Gemini 2.5 Flash** | $0.075 | $0.30 | Budget / High Speed |
| **Claude 3.5 Sonnet** | $3.00 | $15.00 | Premium Coding |
| **GPT-4o Mini** | $0.15 | $0.60 | Standard Utility |
| **DeepSeek Coder** | $0.14 | $0.28 | Cost-Optimized Code |

Real-time token counters display prompt tokens, completion tokens, and dollar expenditures directly on the workstation header.

---

## 5. Fault Tolerance & HTTP Error Handling

1. **HTTP 401 Unauthorized**:
   - Immediately identifies invalid or revoked API keys.
   - Clears bad cached key from memory and surfaces a prominent user-facing prompt to re-enter valid credentials in the Settings screen.
2. **HTTP 429 Rate Limit**:
   - Implements exponential backoff with jitter (initial delay 1.5s, multiplying by 2 up to 3 retries).
   - Surfaces remaining quota or reset times if supplied in provider `retry-after` headers.
3. **Network Interruption**:
   - Sockets employ 20-second connection timeouts and 60-second read timeouts. Clean cancellation via `CoroutineScope` cancellation prevents orphaned sockets.

---

## 6. Audit Verdict

- **SSE Streaming Architecture**: `VERIFIED`
- **Keystore Hardware Cryptography**: `VERIFIED`
- **Error Propagation & Quota Recovery**: `VERIFIED`
- **Live Cloud Calls**: `PARTIALLY VERIFIED / BLOCKED without user-provided API credentials`
