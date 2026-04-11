package ru.cephei.strategy

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.ext.agent.subgraphWithTask
import ru.cephei.model.AnalysisList
import ru.cephei.model.ChunkList
import ru.cephei.model.Report
import ru.cephei.tools.ChunkingTool
import java.io.File

/**
 * Создаёт граф-стратегию для пайплайна анализа лог-файла.
 *
 * ## Граф выполнения
 * ```
 * nodeStart (String: путь к файлу)
 *     ↓
 * chunker (детерминированная нода — читает файл, делит на чанки)
 *     ↓ ChunkList
 * analyzer (LLM-нода — анализирует каждый чанк)
 *     ↓ AnalysisList
 * reporter (LLM-нода — генерирует финальный отчёт)
 *     ↓ transformed { report.content }
 * nodeFinish (String: Markdown-отчёт)
 * ```
 *
 * ## Ключевые особенности
 * - `chunker` — детерминированная нода без LLM: просто читает файл и делит по дате
 * - `analyzer` и `reporter` — LLM-подграфы через [subgraphWithTask], каждый
 *   получает задачу в виде строки и возвращает типизированный `@Serializable` объект
 * - Koog автоматически создаёт «finish tool» для каждого подграфа по JSON-схеме
 *   выходного типа — LLM вызывает его, когда готова вернуть результат
 *
 * @param chunkingTool инструмент для разбивки лога на чанки (инжектируется для тестируемости)
 */
fun createLogAnalysisStrategy(
    chunkingTool: ChunkingTool = ChunkingTool()
): AIAgentGraphStrategy<String, String> = strategy("log-analysis") {

    // ─── Нода 1: детерминированная — читает файл и делит на чанки (LLM не вызывается) ────────
    //
    // `node<Input, Output>(name) { input -> output }` — простая трансформация без LLM.
    // Лямбда является suspend-расширением AIAgentGraphContextBase, поэтому
    // контекст доступен через `this`, а входной параметр — через `filePath`.
    val chunker by node<String, ChunkList>("chunk-log") { filePath ->
        val file = File(filePath)
        require(file.exists()) { "Log file not found: $filePath" }

        val content = file.readText()
        val chunks = chunkingTool.split(content)

        println("[chunker] Прочитан файл: $filePath (${content.length} символов)")
        println("[chunker] Разбито на ${chunks.size} чанков")

        ChunkList(chunks)
    }

    // ─── Нода 2: LLM-подграф — анализирует каждый чанк ──────────────────────────────────────
    //
    // `subgraphWithTask<Input, Output>(tools = ...) { input -> taskPrompt }`:
    //   - `tools` — тулы, доступные LLM в этом подграфе
    //   - лямбда возвращает строку-задачу, которую получает LLM
    //   - Koog автоматически добавляет «finish tool» с JSON-схемой AnalysisList
    //   - LLM вызывает finish tool, когда готова вернуть результат
    val analyzer by subgraphWithTask<ChunkList, AnalysisList>(
        tools = emptyList<Tool<*, *>>()
    ) { chunkList ->
        buildString {
            appendLine(
                "You are a log analysis expert. Analyze EACH of the following log chunks carefully " +
                "and return a structured AnalysisList with one ChunkAnalysis per chunk."
            )
            appendLine()
            appendLine("Total chunks: ${chunkList.chunks.size}")
            appendLine()

            chunkList.chunks.forEach { chunk ->
                appendLine("═══ CHUNK ID: ${chunk.id} ═══")
                appendLine(chunk.content)
                appendLine()
            }

            appendLine(
                "For each chunk fill in: summary (1-3 sentences), issues (list of specific problems " +
                "with timestamps and components), errorCount, and hasRootCause."
            )
            appendLine("If a chunk has no issues, set summary to empty string and issues to empty list.")
        }
    }

    // ─── Нода 3: LLM-подграф — агрегирует анализы в финальный отчёт ─────────────────────────
    val reporter by subgraphWithTask<AnalysisList, Report>(
        tools = emptyList<Tool<*, *>>()
    ) { analysisList ->
        buildString {
            appendLine(
                "You are a senior DevOps engineer. Based on the following log analysis results, " +
                "generate a comprehensive final Report in Markdown format."
            )
            appendLine()
            appendLine("Summary of chunk analyses:")
            analysisList.analyses.forEach { ca ->
                val status = if (ca.hasIssues()) "⚠ HAS ISSUES" else "✓ OK"
                appendLine("  Chunk ${ca.chunkId} [$status]: ${ca.result.summary.take(120)}")
                if (ca.result.issues.isNotEmpty()) {
                    ca.result.issues.forEach { issue -> appendLine("    • $issue") }
                }
            }
            appendLine()
            appendLine("Total ERROR entries across all chunks: ${analysisList.totalErrors}")
            appendLine("Chunks with issues: ${analysisList.chunksWithIssues} of ${analysisList.analyses.size}")
            appendLine()
            appendLine(
                "Generate a Report with: Markdown content (## Summary, ## Critical Issues, " +
                "## Warnings, ## Recommendations sections), totalErrors count, " +
                "criticalIssues list, recommendations list, and isValid=true."
            )
        }
    }

    // ─── Рёбра графа ─────────────────────────────────────────────────────────────────────────
    edge(nodeStart forwardTo chunker)
    edge(chunker forwardTo analyzer)
    edge(analyzer forwardTo reporter)

    // `transformed` конвертирует Report → String для nodeFinish
    edge(reporter forwardTo nodeFinish transformed { it.content })
}
