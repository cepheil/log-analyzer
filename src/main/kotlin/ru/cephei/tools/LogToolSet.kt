package ru.cephei.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File

/**
 * Набор тулов для работы с лог-файлами, доступных LLM-агенту.
 *
 * Реализует интерфейс [ToolSet] — Koog автоматически регистрирует
 * все методы, помеченные [@Tool], как вызываемые инструменты агента.
 *
 * Каждый тул снабжён аннотациями [@LLMDescription], которые Koog
 * передаёт LLM в виде JSON-схемы — это помогает модели понять,
 * когда и как использовать каждый инструмент.
 */
@LLMDescription("Tools for reading and inspecting log files")
class LogToolSet : ToolSet {

    /** Утилита разбивки лога на чанки — используется внутри тулов, не экспонируется LLM напрямую */
    private val chunkingTool = ChunkingTool()

    /**
     * Читает содержимое лог-файла по указанному пути.
     *
     * Агент вызывает этот тул когда пользователь указывает путь к файлу.
     * Для больших файлов (> 4000 символов) рекомендуется использовать
     * [getLogStats] перед полным чтением, чтобы оценить масштаб.
     *
     * @param filePath абсолютный или относительный путь к лог-файлу
     * @return содержимое файла в виде строки, либо сообщение об ошибке
     */
    @Tool
    @LLMDescription("Read the full content of a log file from disk. Use this to load the log before analysis.")
    fun readLogFile(
        @LLMDescription("Absolute or relative path to the log file, e.g. 'logs/app.log' or 'C:/logs/service.log'")
        filePath: String
    ): String {
        val file = File(filePath)

        if (!file.exists()) return "ERROR: File not found: $filePath"
        if (!file.isFile)   return "ERROR: Path is not a file: $filePath"
        if (!file.canRead()) return "ERROR: No read permission for: $filePath"

        return try {
            val content = file.readText()
            if (content.isBlank()) "WARNING: File is empty: $filePath"
            else content
        } catch (e: Exception) {
            "ERROR reading file '$filePath': ${e::class.simpleName} — ${e.message}"
        }
    }

    /**
     * Возвращает статистику по уровням логирования в тексте лога.
     *
     * Полезен как первый шаг анализа — агент может оценить масштаб проблем
     * (сколько ERROR, WARN, INFO) до полного разбора содержимого.
     *
     * @param logContent текст лога (результат [readLogFile] или его часть)
     * @return форматированная статистика по уровням логирования
     */
    @Tool
    @LLMDescription(
        "Get statistics about log levels in the log content. " +
        "Call this first to understand the scale of issues before deep analysis."
    )
    fun getLogStats(
        @LLMDescription("The raw log content text to analyze (result of readLogFile)")
        logContent: String
    ): String {
        if (logContent.isBlank()) return "ERROR: Empty log content provided"

        val lines = logContent.lines()

        // Подсчёт по уровням логирования (регистронезависимо)
        val errorCount = lines.count { it.contains("ERROR", ignoreCase = true) }
        val warnCount  = lines.count { it.contains("WARN",  ignoreCase = true) }
        val infoCount  = lines.count { it.contains("INFO",  ignoreCase = true) }
        val debugCount = lines.count { it.contains("DEBUG", ignoreCase = true) }

        // Оценка количества чанков для планирования анализа
        val chunks = chunkingTool.split(logContent)

        return """
            Log Statistics:
            ---------------
            Total lines  : ${lines.size}
            Total chars  : ${logContent.length}
            Total chunks : ${chunks.size} (for analysis)

            Log levels:
              ERROR : $errorCount
              WARN  : $warnCount
              INFO  : $infoCount
              DEBUG : $debugCount
        """.trimIndent()
    }

    /**
     * Возвращает конкретный чанк лога по его номеру.
     *
     * Используется когда лог слишком большой для единовременного анализа.
     * Агент может разбить работу: сначала [getLogStats], затем анализировать
     * каждый чанк по очереди через этот тул.
     *
     * @param logContent полный текст лога
     * @param chunkIndex номер чанка (начиная с 0)
     * @return текст запрошенного чанка или сообщение об ошибке
     */
    @Tool
    @LLMDescription(
        "Get a specific chunk of the log by index. Use this to analyze large logs piece by piece. " +
        "First call getLogStats to know the total number of chunks."
    )
    fun getLogChunk(
        @LLMDescription("The raw log content text (result of readLogFile)")
        logContent: String,
        @LLMDescription("Zero-based index of the chunk to retrieve (0 = first chunk)")
        chunkIndex: Int
    ): String {
        if (logContent.isBlank()) return "ERROR: Empty log content provided"

        val chunks = chunkingTool.split(logContent)

        if (chunks.isEmpty()) return "ERROR: Could not split log into chunks"
        if (chunkIndex < 0 || chunkIndex >= chunks.size) {
            return "ERROR: Chunk index $chunkIndex out of range. Valid range: 0..${chunks.size - 1}"
        }

        val chunk = chunks[chunkIndex]
        return "Chunk ${chunk.id + 1} of ${chunks.size}:\n${chunk.content}"
    }
}
