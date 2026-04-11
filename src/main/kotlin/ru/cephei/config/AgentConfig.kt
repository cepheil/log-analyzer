package ru.cephei.config

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Центральная конфигурация агента.
 *
 * Хранит константы (промпты, лимиты итераций), фабричные методы для создания
 * LLM-executor-ов и выбора моделей.
 *
 * API-ключи читаются исключительно из переменных окружения:
 *   - OPENAI_API_KEY   — ключ OpenAI (обязателен)
 *   - ANTHROPIC_API_KEY — ключ Anthropic (опционально, для расширенного режима)
 *
 * Установить перед запуском:
 *   Windows : set OPENAI_API_KEY=sk-...
 *   Linux   : export OPENAI_API_KEY=sk-...
 */
object AgentConfig {

    /**
     * Максимальное количество итераций агента за один вызов [run].
     * Ограничивает глубину рекурсии при вызовах тулов, защищая от бесконечных циклов.
     */
    const val MAX_ITERATIONS = 30

    /**
     * Системный промпт для агента-аналитика логов.
     *
     * Определяет:
     * - роль агента (эксперт по анализу логов)
     * - ожидаемый формат ответа (Markdown-отчёт)
     * - приоритетность уровней (ERROR > WARN > INFO)
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

    /**
     * Создаёт [MultiLLMPromptExecutor] с поддержкой OpenAI и Anthropic.
     *
     * [MultiLLMPromptExecutor] автоматически маршрутизирует запросы к нужному провайдеру
     * на основе переданной модели — поэтому один executor поддерживает оба.
     *
     * @return executor, готовый к передаче в [AIAgent]
     * @throws IllegalStateException если OPENAI_API_KEY не задана
     */
    fun createExecutor(): MultiLLMPromptExecutor {
        val openAiKey = System.getenv("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY environment variable is not set. Please set it before running.")

        // Anthropic опционален — если ключ не задан, используется только OpenAI
        val anthropicKey = System.getenv("ANTHROPIC_API_KEY")

        return if (anthropicKey != null) {
            MultiLLMPromptExecutor(
                OpenAILLMClient(openAiKey),
                AnthropicLLMClient(anthropicKey)
            )
        } else {
            MultiLLMPromptExecutor(OpenAILLMClient(openAiKey))
        }
    }

    /**
     * Основная модель для анализа логов — GPT-4o.
     *
     * Хорошо справляется со структурированным выводом, анализом текста
     * и следованием сложным инструкциям.
     *
     * @return [LLModel] для передачи в [AIAgent]
     */
    fun defaultModel(): LLModel = OpenAIModels.Chat.GPT4o

    /**
     * Альтернативная модель — Anthropic Claude Sonnet 4.5.
     * Рекомендуется для задач с интенсивным вызовом тулов.
     *
     * @return [LLModel] для передачи в [AIAgent]
     */
    fun anthropicModel(): LLModel = AnthropicModels.Sonnet_4_5
}
