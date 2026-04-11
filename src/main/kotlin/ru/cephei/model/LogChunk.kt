package ru.cephei.model

import kotlinx.serialization.Serializable

/**
 * Один чанк лог-файла — фрагмент текста фиксированного размера.
 *
 * Создаётся утилитой [ChunkingTool] при разбивке большого лога.
 * Передаётся в подграф анализа как входные данные.
 *
 * @param id порядковый номер чанка (начиная с 0)
 * @param content текстовое содержимое чанка
 */
@Serializable
data class LogChunk(
    val id: Int,
    val content: String
)
