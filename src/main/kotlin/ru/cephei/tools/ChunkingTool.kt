package ru.cephei.tools

import ru.cephei.model.LogChunk

/**
 * Утилита для разбивки текста лога на чанки фиксированного размера.
 *
 * Используется внутри стратегии агента (не является тулом для LLM).
 * Разбивает лог по маркерам даты формата `YYYY-MM-DD`, затем группирует
 * записи в чанки, не превышающие [maxChunkSize] символов.
 *
 * Это гарантирует, что каждый чанк:
 * - начинается с начала лог-записи (не обрывает строку посередине)
 * - не превышает лимит контекста LLM
 */
class ChunkingTool(

    /**
     * Максимальный размер одного чанка в символах.
     * Подобран под средний размер контекстного окна LLM (~2000 символов на чанк).
     */
    private val maxChunkSize: Int = 2000
) {

    /**
     * Разбивает текст лога на список [LogChunk].
     *
     * Алгоритм:
     * 1. Сначала делит по маркерам дат (каждая лог-запись начинается с даты)
     * 2. Затем объединяет записи в чанки, не превышающие [maxChunkSize]
     *
     * @param log полный текст лог-файла
     * @return список чанков с порядковыми идентификаторами
     */
    fun split(log: String): List<LogChunk> {
        val entries = splitByLogEntries(log)

        val chunks = mutableListOf<LogChunk>()
        val current = StringBuilder()
        var id = 0

        for (entry in entries) {
            // Если добавление записи превысит лимит — сохраняем текущий чанк и начинаем новый
            if (current.isNotEmpty() && current.length + entry.length > maxChunkSize) {
                chunks.add(LogChunk(id++, current.toString().trim()))
                current.clear()
            }
            current.append(entry)
        }

        // Добавляем оставшийся хвост
        if (current.isNotEmpty()) {
            chunks.add(LogChunk(id, current.toString().trim()))
        }

        return chunks
    }

    /**
     * Делит текст лога на отдельные записи по маркеру даты `YYYY-MM-DD`.
     *
     * Использует lookahead-регулярку, чтобы не удалять саму дату при сплите.
     *
     * @param log текст лога
     * @return список строк, каждая из которых — одна или несколько связанных лог-строк
     */
    fun splitByLogEntries(log: String): List<String> {
        return log.split(Regex("(?=\\d{4}-\\d{2}-\\d{2})"))
            .filter { it.isNotBlank() }
    }
}
