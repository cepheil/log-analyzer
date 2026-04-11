package ru.cephei.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Промежуточная модель — результат анализа одного конкретного чанка.
 *
 * Связывает [LogChunk] с его [AnalysisResult]: содержит идентификатор
 * чанка и полный результат анализа, выполненного LLM.
 *
 * Используется в графе стратегии для передачи данных между нодами:
 * - подграф анализа возвращает `List<ChunkAnalysis>`
 * - подграф генерации отчёта принимает этот список как входные данные
 *
 * @param chunkId идентификатор чанка (совпадает с [LogChunk.id])
 * @param result результат анализа данного чанка
 */
@Serializable
@LLMDescription("Analysis result for a specific log chunk, identified by its chunk ID.")
data class ChunkAnalysis(

    @property:LLMDescription(
        "The ID of the log chunk that was analyzed (zero-based index)."
    )
    val chunkId: Int,

    @property:LLMDescription(
        "The analysis result for this chunk."
    )
    val result: AnalysisResult
) {
    /**
     * Возвращает true если в данном чанке были найдены проблемы.
     * Удобен для фильтрации при агрегации результатов.
     */
    fun hasIssues(): Boolean = result.issues.isNotEmpty()
}
