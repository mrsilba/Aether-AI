package com.aether.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Structured file metadata model representing a Google Drive v3 file resource.
 */
data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val isFolder: Boolean,
    val webViewLink: String? = null
)

/**
 * Paginated response wrapper for Drive v3 file queries.
 */
data class DriveFileListResponse(
    val files: List<DriveFileItem>,
    val nextPageToken: String?
)

/**
 * Direct HTTP Client for Google Drive REST API v3.
 * Adheres to official v3 specification with query filters, pagination,
 * atomic downloads, and Google Workspace export MIME translations.
 */
class GoogleDriveApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val DRIVE_V3_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val GOOGLE_DOC_MIME_TYPE = "application/vnd.google-apps.document"
        const val GOOGLE_SHEET_MIME_TYPE = "application/vnd.google-apps.spreadsheet"

        // Default export formats for Google Workspace docs
        const val EXPORT_MIME_MARKDOWN = "text/markdown"
        const val EXPORT_MIME_PLAINTEXT = "text/plain"
        const val EXPORT_MIME_PDF = "application/pdf"
    }

    /**
     * Lists or browses files within a folder with pagination support.
     * Query conforms to Drive v3 query language: 'folderId' in parents and trashed = false.
     */
    suspend fun listFiles(
        accessToken: String,
        folderId: String? = null,
        pageToken: String? = null,
        pageSize: Int = 25
    ): Result<DriveFileListResponse> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = DRIVE_V3_FILES_ENDPOINT.toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext Result.failure(IllegalStateException("Invalid Drive v3 URL"))

            val parentQuery = if (folderId.isNullOrBlank() || folderId == "root") {
                "'root' in parents and trashed = false"
            } else {
                "'$folderId' in parents and trashed = false"
            }

            urlBuilder.addQueryParameter("q", parentQuery)
            urlBuilder.addQueryParameter("pageSize", pageSize.coerceIn(1, 100).toString())
            urlBuilder.addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, size, modifiedTime, webViewLink)")
            urlBuilder.addQueryParameter("orderBy", "folder, name")

            if (!pageToken.isNullOrBlank()) {
                urlBuilder.addQueryParameter("pageToken", pageToken)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Drive API Error HTTP ${response.code}: $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val filesArray = json.optJSONArray("files")
            val nextToken = json.optString("nextPageToken").ifBlank { null }

            val list = mutableListOf<DriveFileItem>()
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val mime = obj.optString("mimeType", "application/octet-stream")
                    val isFolder = mime == FOLDER_MIME_TYPE
                    list.add(
                        DriveFileItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            mimeType = mime,
                            sizeBytes = obj.optLong("size", 0L),
                            modifiedTime = obj.optString("modifiedTime", ""),
                            isFolder = isFolder,
                            webViewLink = obj.optString("webViewLink", "").ifBlank { null }
                        )
                    )
                }
            }

            Result.success(DriveFileListResponse(files = list, nextPageToken = nextToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads an ordinary binary file via Google Drive v3 alt=media streaming.
     * Uses atomic temporary file writes (.part) to prevent corrupted partial files.
     */
    suspend fun downloadBinaryFile(
        accessToken: String,
        fileId: String,
        targetFile: File,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        try {
            targetFile.parentFile?.mkdirs()
            val url = "$DRIVE_V3_FILES_ENDPOINT/$fileId?alt=media"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: response.message
                return@withContext Result.failure(IllegalStateException("Download failed (HTTP ${response.code}): $err"))
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty response body"))
            val totalBytes = body.contentLength()

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(partFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var accumulated = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                accumulated += bytesRead
                onProgress?.invoke(accumulated, totalBytes)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (targetFile.exists()) targetFile.delete()
            if (partFile.renameTo(targetFile)) {
                Result.success(targetFile)
            } else {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Exports a Google Workspace Document (Docs, Sheets, Slides) to an open file format.
     */
    suspend fun exportWorkspaceDocument(
        accessToken: String,
        fileId: String,
        exportMimeType: String,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val url = "$DRIVE_V3_FILES_ENDPOINT/$fileId/export?mimeType=$exportMimeType"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Export failed HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty body"))
            FileOutputStream(targetFile).use { fos ->
                body.byteStream().copyTo(fos)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
