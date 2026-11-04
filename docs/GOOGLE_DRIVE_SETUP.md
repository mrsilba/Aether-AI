# Google Drive v3 REST API Integration & Setup Guide

AETHER AI connects to Google Drive via official Google Drive REST API v3 endpoints, adhering to least-privilege security principles and Android Keystore AES-256-GCM hardware encryption.

---

## 1. Google Cloud Console Setup

To enable Google Drive integration for personal or enterprise use:

1. Visit the [Google Cloud Console](https://console.cloud.google.com).
2. Create or select a Google Cloud Project (e.g., `Aether-Dev-Workspace`).
3. Navigate to **APIs & Services** > **Library**.
4. Search for **Google Drive API** and click **Enable**.
5. Navigate to **APIs & Services** > **OAuth Consent Screen**:
   - Choose User Type: **External** (or Internal for Workspace organizations).
   - Fill in App Name: `AETHER AI` and Developer Contact Email.
   - Click **Add or Remove Scopes** and select:
     - `https://www.googleapis.com/auth/drive.file` (Per-file access created or opened by app)
     - `https://www.googleapis.com/auth/drive.readonly` (Read-only metadata and file viewing)
   - Add your Google account under **Test Users**.
6. Navigate to **APIs & Services** > **Credentials**:
   - Click **Create Credentials** > **OAuth Client ID**.
   - Select Application Type: **Android** (or Web application for testing token generation).
   - Package name: `com.aistudio.aetherai.kqwzrt`
   - Enter your release or debug SHA-1 signing fingerprint.

---

## 2. In-App Authentication Options

### Option A: Direct OAuth Bearer Token (Fastest Setup)
1. Generate an OAuth Bearer token using Google OAuth 2.0 Playground or gcloud CLI:
   ```bash
   gcloud auth application-default print-access-token
   ```
2. In AETHER AI, open **More** > **Cloud & Drive Integrations**.
3. Tap **Configure OAuth**.
4. Enter your email and paste the Bearer token (`ya29...`).
5. Tap **Store Encrypted Token**.
6. Tokens are immediately encrypted using Android Keystore AES-256-GCM and stored in private SharedPreferences.

### Option B: Android Storage Access Framework (SAF) Fallback
If you prefer not to configure Google Cloud OAuth credentials:
1. Open Google Drive on your Android phone and download the project or archive.
2. In AETHER AI, go to **Cloud & Drive Integrations** > **Android Storage Access Framework (SAF)**.
3. Import files or ZIP archives directly through Android's zero-permission system DocumentProvider.
