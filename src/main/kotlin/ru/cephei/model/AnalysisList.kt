package ru.cephei.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Контейнер для результатов анализа всех чанков.
 *
 * Выходной тип LLM-ноды analyzer — передаётся в ноду reporter
 * для генерации финального отчёта.
 *
 * @param analyses список результатов анализа — по одному [ChunkAnalysis] на чанк
 */
@Serializable
@LLMDescription(
    "Container holding analysis results for all log chunks. " +
    "Must contain one ChunkAnalysis entry per analyzed chunk."
)
data class AnalysisList(

    @property:LLMDescription(
        "List of analysis results, one per log chunk. " +
        "Each item contains the chunk ID and its detailed analysis result."
    )
    val analyses: List<ChunkAnalysis>
) {
    /** Суммарное количество ошибок по всем чанкам. */
    val totalErrors: Int get() = analyses.sumOf { it.result.errorCount }

    /** Количество чанков, в которых найдены проблемы. */
    val chunksWithIssues: Int get() = analyses.count { it.hasIssues() }
}
