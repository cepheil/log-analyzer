package ru.cephei

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import kotlinx.coroutines.runBlocking
import ru.cephei.config.AgentConfig
import ru.cephei.strategy.createLogAnalysisStrategy
import java.io.File

/**
 * Точка входа в приложение — Этап 4: граф-стратегия анализа лога.
 *
 * Агент выполняет детерминированный пайплайн через граф Koog:
 *   1. Читает и делит лог-файл на чанки (без LLM)
 *   2. LLM анализирует каждый чанк → [AnalysisList]
 *   3. LLM агрегирует результаты → финальный Markdown-отчёт [Report]
 *
 * Запуск:
 *   ```
 *   set OPENAI_API_KEY=sk-...
 *   ./gradlew run --args="sample.log"
 *   ```
 *
 * Если аргумент не передан — агент запрашивает путь к файлу интерактивно.
 */
fun main(args: Array<String>) = runBlocking {

    println("=== Log Analyzer Agent — Этап 4: граф-стратегия ===\n")

    // Определяем путь к лог-файлу: из аргументов или интерактивно
    val logFilePath = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: run {
            print("Путь к лог-файлу (Enter = sample.log): ")
            readlnOrNull()?.takeIf { it.isNotBlank() } ?: "sample.log"
        }

    val logFile = File(logFilePath)
    if (!logFile.exists()) {
        System.err.println("Ошибка: файл не найден — $logFilePath")
        return@runBlocking
    }

    println("Анализирую: ${logFile.absolutePath}")
    println()

    // Создаём граф-стратегию: chunker → analyzer → reporter
    val strategy = createLogAnalysisStrategy()

    // Создаём агента с граф-стратегией (ToolRegistry не нужен для граф-нод без тулов)
    val agent = AIAgent(
        promptExecutor = AgentConfig.createExecutor(),
        llmModel       = AgentConfig.defaultModel(),
        systemPrompt   = AgentConfig.SYSTEM_PROMPT,
        toolRegistry   = ToolRegistry.EMPTY,
        strategy       = strategy,
        maxIterations  = AgentConfig.MAX_ITERATIONS,
    ) {
        install(EventHandler) {

            onAgentStarting { ctx ->
                println("[Agent] Старт: ${ctx.agent.id}")
            }

            onToolCallStarting { ctx ->
                println("[Tool]  → ${ctx.toolName}  args=${ctx.toolArgs}")
            }

            onToolCallCompleted { ctx ->
                // toolResult имеет тип JsonElement — обрезаем для читаемости
                val preview = ctx.toolResult.toString().take(200)
                println("[Tool]  ← ${ctx.toolName}: $preview")
            }

            onAgentCompleted { ctx ->
                println("[Agent] Завершение: ${ctx.agentId}")
            }
        }
    }

    println("--- Запуск агента ---\n")

    val report = agent.run(logFilePath)

    println("\n" + "═".repeat(60))
    println("ФИНАЛЬНЫЙ ОТЧЁТ")
    println("═".repeat(60))
    println(report)
    println("═".repeat(60))
    println("\n=== Анализ завершён ===")
}
