# AETHER AI v1.0.0 — Known Limitations & Technical Scope

This document provides transparent, unembellished documentation of the current capabilities, operational boundaries, and known limitations of AETHER AI v1.0.0.

---

## 1. Physical Device vs. Container Execution
- **Physical Device Pilot**: In-app physical device testing on Samsung Galaxy S22 Ultra (Exynos 2200 / Snapdragon 8 Gen 1) is planned for owner execution following release packaging.
- **Hardware Keystore**: Under automated JVM Robolectric test environments, `AndroidKeyStore` provider is mocked via a secure software cryptographic fallback; on physical Android devices, hardware-backed StrongBox/TEE Keystore is engaged.

---

## 2. On-Device Local Inference (GGUF)
- **Model Sizes**: Models larger than 4B parameters (e.g., 7B or 14B) require 8GB+ of available memory and may trigger Android Low Memory Killer (LMK) during intensive multi-turn contexts. We recommend 1.5B to 3B models for stable operation.
- **Initial State**: A fresh install has no bundled 2GB GGUF model; the UI accurately indicates "No Model Loaded" until the user downloads or imports a model.

---

## 3. Git Operations
- **Network Protocol**: HTTPS cloning with Personal Access Tokens (PAT) is fully supported. SSH key negotiation is planned for future releases.
- **Large Repositories**: Cloning multi-gigabyte repositories with extensive history may exhaust internal storage or encounter timeout limits; shallow clones (`--depth 1`) are recommended.

---

## 4. Google Drive v3 REST API
- **Live OAuth Flow**: In standalone APK installations without an embedded Google Services JSON pointing to a registered GCP project, OAuth Bearer tokens must be entered manually or generated via Google Cloud Console. Android Storage Access Framework (SAF) is provided as an immediate, zero-credential alternative.
