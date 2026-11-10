package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.util.SampleDocumentsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "DocumentAiService"

class DocumentAiService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeDocumentLayout(
        bitmap: Bitmap,
        presetId: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 1. Fast-path for built-in sample presets to enable immediate testing
        if (!presetId.isNullOrBlank()) {
            Log.d(TAG, "Loading pre-engineered layout for preset: $presetId")
            return@withContext SampleDocumentsProvider.getPresetLayoutJson(presetId)
        }

        // 2. Validate GEMINI_API_KEY
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_") || apiKey.contains("API_KEY", ignoreCase = true)) {
            throw IllegalStateException(
                "Gemini API key is required to analyze scanned documents. " +
                "Please configure your GEMINI_API_KEY in the Secrets panel in AI Studio."
            )
        }

        // 3. Prepare optimized bitmap and Base64 encoding
        val scaledBitmap = prepareOptimizedBitmap(bitmap)
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val promptText = """
You are an expert Document Layout and Formatting Reconstruction Engine specializing in Microsoft Word (.docx) fidelity.
Analyze the provided document scan image and reconstruct its exact visual structure, layout, typography, and tabular data.

Instructions:
1. Extract the main document title and determine the document type (e.g., Invoice, Report, Contract, Letter, Paper, Memo).
2. Segment the document into sequential layout blocks maintaining the original reading order.
3. For Headings: specify the headingLevel (1, 2, or 3), alignment (LEFT, CENTER, RIGHT), bold styling, and any accent colors (colorHex).
4. For Paragraphs: decompose the text into 'runs' to capture inline formatting (bold words, italics, underlined terms, text colors).
5. For Tables: accurately identify all column headers and row cells. Preserve numerical values and table grid structure.
6. For Lists: determine whether they are bulleted (BULLET) or numbered (NUMBERED), and extract all items.
7. For Key-Value sections (e.g. metadata, billing headers, dates, invoice numbers): extract them as keyValuePairs.
8. For Highlighted or Callout boxes: represent them as CALLOUT blocks.
9. For Horizontal lines or dividers: represent them as DIVIDER.

Output a valid JSON object matching this schema:
{
  "title": "Document title string",
  "documentType": "Invoice | Report | Contract | Form | Letter | Article | Memo",
  "confidenceScore": 0.95,
  "blocks": [
    {
      "type": "HEADING | PARAGRAPH | TABLE | LIST | KEY_VALUE | CALLOUT | DIVIDER",
      "headingLevel": 1,
      "alignment": "LEFT | CENTER | RIGHT",
      "text": "plain text if applicable",
      "bold": true,
      "colorHex": "#1E3A8A",
      "runs": [
        {
          "text": "run text segment",
          "bold": false,
          "italic": false,
          "underline": false,
          "colorHex": null
        }
      ],
      "listType": "BULLET | NUMBERED",
      "listItems": ["item 1", "item 2"],
      "tableHeaders": ["Header 1", "Header 2"],
      "tableRows": [["Row 1 Col 1", "Row 1 Col 2"]],
      "keyValuePairs": [{"key": "Label", "value": "Value"}],
      "calloutTitle": "Optional title"
    }
  ]
}
        """.trimIndent()

        // 4. Build JSON request payload for Gemini REST API
        val rootJson = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                        put(JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            }
                            put("inline_data", inlineData)
                        })
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)

            val genConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
            }
            put("generationConfig", genConfig)
        }

        val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = rootJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorObj = JSONObject(responseBody ?: "").optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP error ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                Log.e(TAG, "Gemini API failed with error: $errorMsg")
                throw IllegalStateException("Gemini API error ($errorMsg)")
            }

            if (responseBody.isNullOrBlank()) {
                throw IllegalStateException("Received empty response from Gemini API.")
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("Model returned no candidate response.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts")
            val rawText = partsArr?.optJSONObject(0)?.optString("text")

            if (rawText.isNullOrBlank()) {
                throw IllegalStateException("Gemini returned empty text layout content.")
            }

            // Clean up any potential markdown code fences
            val cleaned = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            cleaned
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document AI layout analysis failed", e)
            throw e
        }
    }

    private fun prepareOptimizedBitmap(src: Bitmap): Bitmap {
        val maxDim = 1600
        if (src.width <= maxDim && src.height <= maxDim) {
            return src
        }
        val ratio = src.width.toFloat() / src.height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(src, newWidth, newHeight, true)
    }
}
