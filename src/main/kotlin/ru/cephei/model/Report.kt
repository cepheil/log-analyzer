package ru.cephei.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Финальный отчёт по анализу лог-файла.
 *
 * Является выходным результатом всего пайплайна агента.
 * LLM заполняет этот объект на последнем этапе стратегии —
 * агрегируя результаты анализа всех чанков в единый документ.
 *
 * @param content полный текст отчёта в формате Markdown
 * @param totalErrors суммарное количество ошибок по всем чанкам
 * @param criticalIssues список критических проблем, требующих немедленного внимания
 * @param recommendations список рекомендаций по устранению найденных проблем
 * @param isValid true, если анализ завершён без технических сбоев
 */
@Serializable
@LLMDescription(
    "Final analysis report aggregated from all log chunks. " +
    "Produce a comprehensive, actionable report in Markdown format."
)
data class Report(

    @property:LLMDescription(
        "Full analysis report in Markdown format. Must include: " +
        "## Summary, ## Critical Issues, ## Warnings, ## Recommendations sections."
    )
    val content: String,

    @property:LLMDescription(
        "Total number of ERROR-level entries found across all analyzed chunks."
    )
    val totalErrors: Int,

    @property:LLMDescription(
        "List of critical issues that require immediate attention. " +
        "Each item: one sentence describing the issue and its impact."
    )
    val criticalIssues: List<String>,

    @property:LLMDescription(
        "Actionable recommendations to fix or mitigate the identified problems. " +
        "Each item: one concrete action to take."
    )
    val recommendations: List<String>,

    @property:LLMDescription(
        "Set to true if the analysis completed successfully. " +
        "Set to false if the log was empty, unreadable, or analysis failed."
    )
    val isValid: Boolean
)
