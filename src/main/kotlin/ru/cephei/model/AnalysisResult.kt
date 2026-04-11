package ru.cephei.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Результат анализа одного чанка лога LLM-агентом.
 *
 * Используется как типизированный выходной результат подграфа анализа.
 * Аннотации [@LLMDescription] передаются агенту как часть JSON-схемы —
 * это направляет LLM что именно нужно заполнить в каждом поле.
 *
 * Важно: на свойствах data-класса используется `@property:LLMDescription`,
 * а не просто `@LLMDescription` — это особенность аннотационного таргетинга в Kotlin.
 *
 * @param summary краткое резюме найденных проблем в данном чанке
 * @param issues список конкретных проблем с указанием времени и компонента
 * @param errorCount количество строк уровня ERROR в чанке
 * @param hasRootCause удалось ли агенту определить корневую причину проблем
 */
@Serializable
@LLMDescription("Result of analyzing a single log chunk. Fill all fields based on the log content.")
data class AnalysisResult(

    @property:LLMDescription(
        "Brief summary of the most significant findings in this chunk. " +
        "1-3 sentences. Empty string if no issues found."
    )
    val summary: String,

    @property:LLMDescription(
        "List of specific problems found. Each item should include: " +
        "timestamp, affected component, and description of the issue. " +
        "Empty list if no issues found."
    )
    val issues: List<String>,

    @property:LLMDescription(
        "Number of ERROR-level log lines in this chunk."
    )
    val errorCount: Int = 0,

    @property:LLMDescription(
        "True if a likely root cause was identified for the errors in this chunk."
    )
    val hasRootCause: Boolean = false
)
