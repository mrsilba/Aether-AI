# AETHER AI — Cloud AI Multi-Provider Integration Report

## 1. Overview
The `CloudAiProviderManager` subsystem provides unified streaming inference across top-tier cloud LLM providers, while maintaining local-first sovereignty and strict security boundaries.

---

## 2. Integrated Providers & Endpoints

| Provider | Default Model | API Protocol | Streaming Protocol | Price / 1M Tokens | Status |
|---|---|---|---|---|---|
| **Google Gemini** | `gemini-2.5-flash` | REST v1beta | Server-Sent Events (`alt=sse`) | $0.075 | **ACTIVE & VERIFIED** |
| **Anthropic Claude** | `claude-3-5-sonnet-20241022` | REST v1 /messages | SSE (`content_block_delta`) | $3.000 | **ACTIVE & VERIFIED** |
| **OpenAI GPT** | `gpt-4o-mini` | REST v1 /chat/completions | SSE (`delta.content`) | $0.150 | **ACTIVE & VERIFIED** |
| **DeepSeek Cloud** | `deepseek-chat` | OpenAI-compatible API | SSE (`choices[0].delta`) | $0.140 | **ACTIVE & VERIFIED** |
| **OpenRouter** | `auto` | OpenAI-compatible router | SSE (`choices[0].delta`) | $0.200 | **ACTIVE & VERIFIED** |

---

## 3. Credential Security Architecture
- **Hardware-Backed AES-256-GCM Vault**: API keys are encrypted using Android Keystore keys generated with `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`.
- **Zero Plaintext Logs**: `SecretScanner` actively redacts key patterns (`AIza...`, `sk-...`, `ghp-...`) from all terminal streams, error dialogs, and diagnostic logs.
- **Dynamic Key Management**: Users can edit or revoke keys at any time via `MinimalSettingsScreen`.
