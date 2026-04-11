package ru.cephei.config

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Кастомный провайдер для LMStudio — OpenAI-совместимый локальный сервер.
 *
 * Используется как ключ в [MultiLLMPromptExecutor], чтобы не конфликтовать
 * с настоящим [LLMProvider.OpenAI] при наличии обоих клиентов в одном executor-е.
 */
private class LMStudioLLMProvider : LLMProvider("lmstudio", "LMStudio")

/**
 * Центральная конфигурация агента: константы, фабрики executor-ов и моделей.
 *
 * ## Поддерживаемые провайдеры
 * | Провайдер | Метод              | Env-переменная       |
 * |-----------|--------------------|----------------------|
 * | OpenAI    | [createOpenAIExecutor]    | `OPENAI_API_KEY`     |
 * | Anthropic | [createAnthropicExecutor] | `ANTHROPIC_API_KEY`  |
 * | LMStudio  | [createLMStudioExecutor]  | не требуется         |
 *
 * API-ключи читаются исключительно из переменных окружения — не хардкодятся.
 */
object AgentConfig {

    /**
     * Максимальное количество итераций агента за один вызов `run`.
     * Защищает от бесконечных циклов при вызовах тулов.
     */
    const val MAX_ITERATIONS = 30

    /**
     * Системный промпт агента-аналитика логов.
     *
     * Определяет роль, приоритет уровней (ERROR > WARN > INFO) и
     * требование выводить отчёт в формате Markdown.
     */
    val SYSTEM_PROMPT = """
        You are an expert log analysis agent.
        Your job is to analyze application log files, identify errors, warnings,
        anomalies, and patterns, then produce a structured report in Markdown format.

        When analyzing logs:
        - Group related issues together
        - Prioritize ERRORS over WARNINGS over INFO messages
        - Identify root causes where possible
        - Suggest remediation steps for critical issues
        - Include timestamps and affected components in your report

        Always respond in the same language as the user's request.
    """.trimIndent()

    // ── Singleton провайдер для LMStudio ────────────────────────────────────────
    //
    // Используем один и тот же экземпляр как ключ в executor-е
    // и как `provider` в LLModel — иначе поиск по Map не сработает.

    /** Провайдер-синглтон для LMStudio. */
    val LMSTUDIO_PROVIDER: LLMProvider = LMStudioLLMProvider()

    // ── Фабрики executor-ов ─────────────────────────────────────────────────────

    /**
     * Создаёт executor только с OpenAI-клиентом.
     *
     * @throws IllegalStateException если `OPENAI_API_KEY` не задана
     */
    fun createOpenAIExecutor(): MultiLLMPromptExecutor {
        val key = requireEnv("OPENAI_API_KEY")
        return MultiLLMPromptExecutor(LLMProvider.OpenAI to OpenAILLMClient(key))
    }

    /**
     * Создаёт executor только с Anthropic-клиентом.
     *
     * @throws IllegalStateException если `ANTHROPIC_API_KEY` не задана
     */
    fun createAnthropicExecutor(): MultiLLMPromptExecutor {
        val key = requireEnv("ANTHROPIC_API_KEY")
        return MultiLLMPromptExecutor(LLMProvider.Anthropic to AnthropicLLMClient(key))
    }

    /**
     * Создаёт executor + модель для LMStudio (OpenAI-совместимый локальный сервер).
     *
     * LMStudio не требует реального API-ключа — передаём placeholder.
     * Кастомный [LMSTUDIO_PROVIDER] гарантирует, что этот клиент не перепутается
     * с реальным OpenAI, если оба окажутся в одном executor-е.
     *
     * @param baseUrl  базовый URL сервера LMStudio, например `http://localhost:1234`
     * @param modelId  имя загруженной модели, как показано в LMStudio UI
     * @return пара (executor, модель) — оба используют [LMSTUDIO_PROVIDER]
     */
    fun createLMStudioExecutor(
        baseUrl: String = "http://localhost:1234",
        modelId: String = "lmstudio-local",
    ): Pair<MultiLLMPromptExecutor, LLModel> {
        val client = OpenAILLMClient(
            apiKey   = "lm-studio",                         // LMStudio игнорирует ключ
            settings = OpenAIClientSettings(baseUrl = baseUrl),
        )
        val executor = MultiLLMPromptExecutor(LMSTUDIO_PROVIDER to client)
        val model = LLModel(
            provider     = LMSTUDIO_PROVIDER,
            id           = modelId,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
            ),
        )
        return executor to model
    }

    // ── Дефолтная модель (используется в тестах и CI без интерактивного выбора) ──

    /**
     * Дефолтная облачная модель — GPT-4o.
     * Используется только если [ModelSelector] не вызывается (например, в тестах).
     */
    fun defaultModel(): LLModel = OpenAIModels.Chat.GPT4o

    /**
     * Дефолтный executor с обоими провайдерами (OpenAI + Anthropic), если оба ключа заданы.
     * Используется для тестов и обратной совместимости.
     *
     * @throws IllegalStateException если `OPENAI_API_KEY` не задана
     */
    fun createExecutor(): MultiLLMPromptExecutor {
        val openAiKey     = requireEnv("OPENAI_API_KEY")
        val anthropicKey  = System.getenv("ANTHROPIC_API_KEY")

        return if (anthropicKey != null) {
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI    to OpenAILLMClient(openAiKey),
                LLMProvider.Anthropic to AnthropicLLMClient(anthropicKey),
            )
        } else {
            MultiLLMPromptExecutor(LLMProvider.OpenAI to OpenAILLMClient(openAiKey))
        }
    }

    // ── Утилиты ─────────────────────────────────────────────────────────────────

    private fun requireEnv(name: String): String =
        System.getenv(name)
            ?: error("Переменная окружения $name не задана. Установите её перед запуском:\n  Windows: set $name=...\n  Linux:   export $name=...")
}
