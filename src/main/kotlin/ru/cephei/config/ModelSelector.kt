package ru.cephei.config

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Результат выбора модели: готовый executor, модель и человекочитаемое имя.
 *
 * @param executor настроенный [MultiLLMPromptExecutor] для выбранного провайдера
 * @param model    модель, которую получит [ai.koog.agents.core.agent.AIAgent]
 * @param displayName отображаемое имя для логов
 */
data class ModelConfig(
    val executor: MultiLLMPromptExecutor,
    val model: LLModel,
    val displayName: String,
)

/**
 * Интерактивный выбор LLM-модели в терминале перед запуском агента.
 *
 * Поддерживаемые варианты:
 * - Облачные: OpenAI GPT-4o / GPT-4o-mini, Anthropic Claude Sonnet 4.5 / Haiku 4.5
 * - Локальные: LMStudio (OpenAI-совместимый API на localhost)
 *
 * Использование:
 * ```kotlin
 * val config = ModelSelector.select()
 * val agent = AIAgent(executor = config.executor, llmModel = config.model, ...)
 * ```
 */
object ModelSelector {

    // ── Пункты меню ─────────────────────────────────────────────────────────────

    private sealed class Entry(val label: String) {

        /** Облачная модель OpenAI. */
        class OpenAI(label: String, val model: LLModel) : Entry(label)

        /** Облачная модель Anthropic. */
        class Anthropic(label: String, val model: LLModel) : Entry(label)

        /** Локальная модель через LMStudio (OpenAI-совместимый сервер). */
        object LMStudio : Entry("LMStudio (локальная)      — localhost, OpenAI-совместимый API")
    }

    private val menu: List<Entry> = listOf(
        Entry.OpenAI(   "OpenAI GPT-4o             — требует OPENAI_API_KEY",    OpenAIModels.Chat.GPT4o),
        Entry.OpenAI(   "OpenAI GPT-4o-mini         — требует OPENAI_API_KEY",   OpenAIModels.Chat.GPT4oMini),
        Entry.Anthropic("Anthropic Claude Sonnet 4.5 — требует ANTHROPIC_API_KEY", AnthropicModels.Sonnet_4_5),
        Entry.Anthropic("Anthropic Claude Haiku 4.5  — требует ANTHROPIC_API_KEY", AnthropicModels.Haiku_4_5),
        Entry.LMStudio,
    )

    // ── Публичный API ────────────────────────────────────────────────────────────

    /**
     * Показывает меню выбора модели и возвращает сконфигурированный [ModelConfig].
     *
     * Функция читает ввод из stdin. При некорректном вводе предлагает повторить.
     * Если требуемый env-ключ не задан — выбрасывает [IllegalStateException].
     *
     * @return [ModelConfig] с executor-ом и выбранной моделью
     */
    fun select(): ModelConfig {
        printMenu()
        val index = readChoice(menu.size)
        println()
        return buildConfig(menu[index])
    }

    // ── Приватные хелперы ────────────────────────────────────────────────────────

    private fun printMenu() {
        println("╔══════════════════════════════════════════════════╗")
        println("║           Выбор модели для анализа лога          ║")
        println("╚══════════════════════════════════════════════════╝")
        println()
        println("  Облачные модели:")
        menu.forEachIndexed { i, entry ->
            if (i == 4) println()   // разделитель перед «Локальные»
            if (i == 4) println("  Локальные модели:")
            println("    ${i + 1}. ${entry.label}")
        }
        println()
        print("  Выбор [1]: ")
    }

    /** Читает номер пункта из stdin; повторяет запрос при невалидном вводе. */
    private fun readChoice(max: Int): Int {
        while (true) {
            val line = readlnOrNull()?.trim()
            if (line.isNullOrEmpty()) return 0                          // Enter → дефолт (1-й пункт)
            val n = line.toIntOrNull()
            if (n != null && n in 1..max) return n - 1
            print("  Введите число от 1 до $max: ")
        }
    }

    /** Строит [ModelConfig] по выбранному пункту меню. */
    private fun buildConfig(entry: Entry): ModelConfig = when (entry) {

        is Entry.OpenAI -> {
            val executor = AgentConfig.createOpenAIExecutor()
            ModelConfig(executor, entry.model, entry.label.substringBefore(" —").trim())
        }

        is Entry.Anthropic -> {
            val executor = AgentConfig.createAnthropicExecutor()
            ModelConfig(executor, entry.model, entry.label.substringBefore(" —").trim())
        }

        is Entry.LMStudio -> buildLMStudioConfig()
    }

    /** Задаёт дополнительные параметры LMStudio через stdin. */
    private fun buildLMStudioConfig(): ModelConfig {
        print("  URL сервера LMStudio [http://localhost:1234]: ")
        val baseUrl = readlnOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "http://localhost:1234"

        print("  Имя загруженной модели [lmstudio-local]: ")
        val modelName = readlnOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "lmstudio-local"

        println()
        val (executor, model) = AgentConfig.createLMStudioExecutor(baseUrl, modelName)
        return ModelConfig(executor, model, "LMStudio / $modelName @ $baseUrl")
    }
}
