# Cloud AI Provider Setup Guide

AETHER AI includes a multi-provider Cloud AI engine allowing seamless switching between major LLM APIs. All credentials are encrypted on-device via **Android Keystore AES-256-GCM** and never logged or transmitted in cleartext.

---

## Supported Providers

| Provider | Supported Models | Configuration Key |
|---|---|---|
| **Google Gemini** | `gemini-2.5-pro`, `gemini-2.5-flash`, `gemini-1.5-pro` | Google AI Studio Key |
| **Anthropic Claude** | `claude-3-5-sonnet`, `claude-3-haiku` | Anthropic Console API Key |
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `o1-preview` | OpenAI Platform API Key |
| **DeepSeek** | `deepseek-chat`, `deepseek-coder` | DeepSeek Platform API Key |
| **Groq** | `llama-3.3-70b-versatile`, `mixtral-8x7b-32768` | GroqCloud Console API Key |

---

## How to Configure API Keys

1. Launch **AETHER AI**.
2. Tap the bottom navigation bar or drawer and open **Settings & Diagnostics**.
3. Under **Cloud AI Provider Credentials (AES-256-GCM Vault)**, select the provider you wish to configure.
4. Tap **Set Key** (or **Edit Key**).
5. Paste your API key and tap **Save Encrypted Key**.
6. The key status will update to **Active • Encrypted in Android Keystore**.

---

## Hardware-Backed Security Architecture
- Keys are encrypted with a 256-bit AES master key generated inside the device's hardware security module (TEE/StrongBox).
- Decrypted keys exist only in volatile heap memory during active HTTP request generation and are cleared when idle.
- Full SSRF prevention strictly blocks routing requests to non-whitelisted domains or loopback addresses.
