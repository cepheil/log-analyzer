package ru.cephei.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Контейнер для списка чанков лога.
 *
 * Используется как типизированный промежуточный результат между
 * детерминированной нодой чанкинга и LLM-нодой анализа.
 *
 * @Serializable требуется, поскольку класс может передаваться
 * в подграфы через finish-тул Koog.
 *
 * @param chunks список чанков лог-файла
 */
@Serializable
@LLMDescription("Container for log file chunks prepared for analysis.")
data class ChunkList(

    @property:LLMDescription(
        "List of log chunks. Each chunk contains an ID and a portion of the log file content."
    )
    val chunks: List<LogChunk>
) {
    /** Суммарный объём контента во всех чанках (в символах). */
    val totalContentSize: Int get() = chunks.sumOf { it.content.length }
}
