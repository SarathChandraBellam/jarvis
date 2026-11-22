package com.jarvis.hud.ai

import com.jarvis.hud.contracts.OpenRouterClient
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.OpenRouterModelInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Failure raised (inside a [Result]) by [OkHttpOpenRouterClient]; message is user-friendly. */
class OpenRouterException(
  val httpCode: Int?,
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

/**
 * OpenRouter REST client over OkHttp. All calls run on Dispatchers.IO and never throw
 * (except coroutine cancellation): failures are returned as [Result.failure] with an
 * [OpenRouterException] carrying a friendly message.
 */
class OkHttpOpenRouterClient(
  private val http: OkHttpClient = defaultHttpClient(),
  private val baseUrl: String = DEFAULT_BASE_URL,
) : OpenRouterClient {

  override suspend fun chat(
    apiKey: String,
    modelId: String,
    systemPrompt: String,
    messages: List<Pair<String, String>>,
  ): Result<String> = chatInternal(apiKey, modelId, systemPrompt, messages, temperature = 0.3, maxTokens = null)

  internal suspend fun chatInternal(
    apiKey: String,
    modelId: String,
    systemPrompt: String,
    messages: List<Pair<String, String>>,
    temperature: Double?,
    maxTokens: Int?,
  ): Result<String> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext Result.failure(OpenRouterException(null, "No OpenRouter API key configured."))
    try {
      val all = buildList {
        if (systemPrompt.isNotBlank()) add(ChatMessageDto("system", systemPrompt))
        messages.forEach { (role, content) -> add(ChatMessageDto(normalizeRole(role), content)) }
      }
      val payload = json.encodeToString(ChatRequestDto.serializer(), ChatRequestDto(modelId, all, temperature, maxTokens))
      val request = Request.Builder()
        .url("$baseUrl/chat/completions")
        .applyHeaders(apiKey)
        .post(payload.toRequestBody(JSON_MEDIA))
        .build()
      http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          return@withContext Result.failure(httpError(response.code, body, modelId))
        }
        val parsed = runCatching { json.decodeFromString(ChatResponseDto.serializer(), body) }.getOrNull()
          ?: return@withContext Result.failure(OpenRouterException(response.code, "Malformed response from OpenRouter."))
        parsed.error?.let { err ->
          return@withContext Result.failure(httpError(err.code ?: response.code, body, modelId))
        }
        val content = parsed.choices.firstOrNull()?.message?.content?.trim()
        if (content.isNullOrEmpty()) {
          Result.failure(OpenRouterException(response.code, "Model '$modelId' returned an empty response."))
        } else {
          Result.success(content)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(networkError(e))
    }
  }

  override suspend fun listModels(apiKey: String): Result<List<OpenRouterModelInfo>> = withContext(Dispatchers.IO) {
    try {
      val builder = Request.Builder().url("$baseUrl/models").get()
      if (apiKey.isNotBlank()) builder.applyHeaders(apiKey)
      http.newCall(builder.build()).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return@withContext Result.failure(httpError(response.code, body, null))
        val parsed = runCatching { json.decodeFromString(ModelsResponseDto.serializer(), body) }.getOrNull()
          ?: return@withContext Result.failure(OpenRouterException(response.code, "Malformed model list from OpenRouter."))
        Result.success(parsed.data.map { it.toInfo() }.sortedWith(compareByDescending<OpenRouterModelInfo> { it.isFree }.thenBy { it.id }))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(networkError(e))
    }
  }

  override suspend fun testConnection(apiKey: String, modelId: String): ConnectionTestResult {
    if (apiKey.isBlank()) return ConnectionTestResult(false, "No API key configured. Enter your OpenRouter key first.")
    val start = System.nanoTime()
    val result = chatInternal(
      apiKey = apiKey,
      modelId = modelId,
      systemPrompt = "You are a connectivity probe. Reply with the single word ONLINE.",
      messages = listOf("user" to "Status?"),
      temperature = 0.0,
      maxTokens = 8,
    )
    val latency = (System.nanoTime() - start) / 1_000_000
    return result.fold(
      onSuccess = { ConnectionTestResult(true, "Uplink established with $modelId. Latency ${latency}ms.", latency) },
      onFailure = { ConnectionTestResult(false, it.message ?: "Connection failed.", latency) },
    )
  }

  private fun Request.Builder.applyHeaders(apiKey: String): Request.Builder = this
    .header("Authorization", "Bearer ${apiKey.trim()}")
    .header("HTTP-Referer", "https://jarvis.local")
    .header("X-Title", "Jarvis")
    .header("Content-Type", "application/json")

  private fun normalizeRole(role: String): String = when (role.lowercase()) {
    "user", "assistant", "system" -> role.lowercase()
    "jarvis", "ai", "bot", "model" -> "assistant"
    else -> "user"
  }

  companion object {
    const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    internal val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
      explicitNulls = false
      coerceInputValues = true
    }

    fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

    internal fun extractErrorMessage(body: String): String? = runCatching {
      json.decodeFromString(ErrorEnvelopeDto.serializer(), body).error?.message
    }.getOrNull()?.takeIf { it.isNotBlank() }

    internal fun httpError(code: Int, body: String, modelId: String?): OpenRouterException {
      val detail = extractErrorMessage(body)
      val msg = when (code) {
        401 -> "Authentication failed: OpenRouter rejected the API key. Check it in settings."
        402 -> "Insufficient OpenRouter credits for this request. Add credits or choose a free model."
        403 -> "Request refused by OpenRouter${detail?.let { ": $it" } ?: "."}"
        404 -> "Model ${modelId?.let { "'$it' " } ?: ""}not found on OpenRouter."
        408 -> "OpenRouter timed out. Please retry."
        429 -> "Rate limited by OpenRouter. Stand by and retry shortly."
        in 500..599 -> "OpenRouter upstream error ($code). Please retry shortly."
        else -> "OpenRouter error $code${detail?.let { ": $it" } ?: "."}"
      }
      return OpenRouterException(code, msg)
    }

    internal fun networkError(e: Exception): OpenRouterException = when (e) {
      is UnknownHostException -> OpenRouterException(null, "Network uplink unavailable: cannot reach openrouter.ai.", e)
      is SocketTimeoutException -> OpenRouterException(null, "Connection to OpenRouter timed out.", e)
      is IOException -> OpenRouterException(null, "Network error: ${e.message ?: "connection failed"}.", e)
      else -> OpenRouterException(null, "Unexpected error: ${e.message ?: e.javaClass.simpleName}.", e)
    }

    internal fun isFreeModel(id: String, prompt: String?, completion: String?): Boolean =
      id.endsWith(":free") || (isZero(prompt) && isZero(completion))

    private fun isZero(v: String?): Boolean = v != null && v.toDoubleOrNull() == 0.0
  }
}

@Serializable
internal data class ChatMessageDto(val role: String, val content: String)

@Serializable
internal data class ChatRequestDto(
  val model: String,
  val messages: List<ChatMessageDto>,
  val temperature: Double? = null,
  @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
internal data class ChatResponseDto(
  val choices: List<ChoiceDto> = emptyList(),
  val error: ErrorDto? = null,
)

@Serializable
internal data class ChoiceDto(val message: ChoiceMessageDto? = null)

@Serializable
internal data class ChoiceMessageDto(val content: String? = null)

@Serializable
internal data class ErrorEnvelopeDto(val error: ErrorDto? = null)

@Serializable
internal data class ErrorDto(val message: String? = null, val code: Int? = null)

@Serializable
internal data class ModelsResponseDto(val data: List<ModelDto> = emptyList())

@Serializable
internal data class ModelDto(
  val id: String,
  val name: String? = null,
  @SerialName("context_length") val contextLength: Int? = null,
  val pricing: PricingDto? = null,
) {
  fun toInfo() = OpenRouterModelInfo(
    id = id,
    name = name ?: id,
    contextLength = contextLength,
    isFree = OkHttpOpenRouterClient.isFreeModel(id, pricing?.prompt, pricing?.completion),
  )
}

@Serializable
internal data class PricingDto(val prompt: String? = null, val completion: String? = null)
