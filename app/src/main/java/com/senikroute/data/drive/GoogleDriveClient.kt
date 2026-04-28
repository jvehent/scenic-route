package com.senikroute.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Drive v3 REST client — only the two operations we actually need (find-or-create
 * a folder by name, upload a text file to that folder). Avoids pulling in the official
 * Google Drive Java client, which is ~5 MB of transitive jars (Apache HttpClient, gRPC,
 * Guava-lite) for what amounts to two HTTP calls.
 *
 * All methods take an OAuth2 access token from [GoogleDriveAuth]. They throw on HTTP
 * failure so the caller can distinguish auth errors (401 → re-auth) from network errors
 * (retry) from malformed responses (give up).
 */
@Singleton
class GoogleDriveClient @Inject constructor() {

    /**
     * Look up a folder by [name] under [parentId] (or "My Drive" if null), or create one
     * if it doesn't exist. Returns the folder's Drive file ID. Filenames in Drive are
     * NOT unique — if there are multiple matches we return the most recently modified.
     */
    suspend fun findOrCreateFolder(
        accessToken: String,
        name: String,
        parentId: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
        val q = "mimeType = 'application/vnd.google-apps.folder' and name = '${esc(name)}' " +
            "and trashed = false$parentClause"
        val listUrl = "https://www.googleapis.com/drive/v3/files?" +
            "q=${URLEncoder.encode(q, "UTF-8")}" +
            "&fields=files(id,name,modifiedTime)" +
            "&pageSize=10" +
            "&spaces=drive"
        val listJson = httpGet(listUrl, accessToken)
        val files = JSONObject(listJson).optJSONArray("files")
        if (files != null && files.length() > 0) {
            // Sort by modifiedTime desc and pick the first — Drive's response order varies.
            var best: JSONObject? = null
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                if (best == null) {
                    best = f
                } else if (f.optString("modifiedTime") > best.optString("modifiedTime")) {
                    best = f
                }
            }
            return@withContext best!!.getString("id")
        }
        // Not found — create it.
        val createBody = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) put("parents", org.json.JSONArray().apply { put(parentId) })
        }.toString()
        val createUrl = "https://www.googleapis.com/drive/v3/files?fields=id"
        val createResp = httpPostJson(createUrl, accessToken, createBody)
        JSONObject(createResp).getString("id")
    }

    /**
     * Upload [content] as a new file in [parentId] with the given [fileName] and
     * [mimeType]. Uses the Drive multipart upload endpoint so metadata + content are
     * sent in a single request. Returns the new file's Drive ID.
     */
    suspend fun uploadFile(
        accessToken: String,
        parentId: String,
        fileName: String,
        mimeType: String,
        content: String,
    ): String = withContext(Dispatchers.IO) {
        val boundary = "senik-${System.nanoTime()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().apply { put(parentId) })
            put("mimeType", mimeType)
        }.toString()
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)

        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Content-Length", body.size.toString())
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Drive upload failed: HTTP $code: ${err.take(500)}")
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(resp).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(url: String, accessToken: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Drive GET failed: HTTP $code: ${err.take(500)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPostJson(url: String, accessToken: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Drive POST failed: HTTP $code: ${err.take(500)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Escape single quotes in Drive query string values (Drive's q syntax uses ' as delimiter). */
    private fun esc(s: String): String = s.replace("'", "\\'")
}
