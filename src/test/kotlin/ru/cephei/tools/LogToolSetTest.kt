package ru.cephei.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для [LogToolSet] — набора тулов для работы с лог-файлами.
 *
 * Все тесты используют временные файлы через [createTempFile], которые
 * автоматически удаляются через [File.deleteOnExit] после завершения JVM.
 *
 * Проверяемые методы:
 * - [LogToolSet.readLogFile]  — чтение файла с диска
 * - [LogToolSet.getLogStats]  — статистика по уровням логирования
 * - [LogToolSet.getLogChunk]  — получение чанка по индексу
 */
class LogToolSetTest {

    private val toolSet = LogToolSet()

    /** Вспомогательный метод — создаёт временный файл с указанным содержимым. */
    private fun tempFileWith(content: String): File {
        val file = File.createTempFile("log-test-", ".log")
        file.deleteOnExit()
        file.writeText(content)
        return file
    }

    // ─── readLogFile ──────────────────────────────────────────────────────────

    @Test
    fun `readLogFile возвращает содержимое существующего файла`() {
        val content = "2026-04-10 08:00:01 INFO  [App] Test"
        val file = tempFileWith(content)

        val result = toolSet.readLogFile(file.absolutePath)

        assertEquals(content, result)
    }

    @Test
    fun `readLogFile возвращает ошибку для несуществующего файла`() {
        val result = toolSet.readLogFile("/nonexistent/path/to/file.log")

        assertTrue(result.startsWith("ERROR"), "Ожидается сообщение об ошибке, получено: $result")
        assertTrue(result.contains("not found"), "Сообщение должно содержать 'not found'")
    }

    @Test
    fun `readLogFile возвращает предупреждение для пустого файла`() {
        val file = tempFileWith("")

        val result = toolSet.readLogFile(file.absolutePath)

        assertTrue(result.startsWith("WARNING"), "Ожидается предупреждение для пустого файла")
        assertTrue(result.contains("empty"), "Сообщение должно содержать 'empty'")
    }

    @Test
    fun `readLogFile возвращает ошибку если путь является директорией`() {
        val dir = File(System.getProperty("java.io.tmpdir"))

        val result = toolSet.readLogFile(dir.absolutePath)

        assertTrue(result.startsWith("ERROR"), "Ожидается ошибка для директории")
        assertTrue(result.contains("not a file"), "Сообщение должно содержать 'not a file'")
    }

    @Test
    fun `readLogFile читает многострочный файл целиком`() {
        val content = """
            2026-04-10 08:00:01 INFO  line one
            2026-04-10 08:00:02 ERROR line two
            2026-04-10 08:00:03 WARN  line three
        """.trimIndent()
        val file = tempFileWith(content)

        val result = toolSet.readLogFile(file.absolutePath)

        assertEquals(content, result)
    }

    // ─── getLogStats ──────────────────────────────────────────────────────────

    @Test
    fun `getLogStats возвращает ошибку для пустой строки`() {
        val result = toolSet.getLogStats("")

        assertTrue(result.startsWith("ERROR"), "Ожидается ошибка для пустого контента")
    }

    @Test
    fun `getLogStats считает строки по уровням логирования`() {
        val log = """
            2026-04-10 08:00:01 INFO  [App] Start
            2026-04-10 08:00:02 ERROR [DB] Fail
            2026-04-10 08:00:03 WARN  [Net] Slow
            2026-04-10 08:00:04 DEBUG [App] Trace
            2026-04-10 08:00:05 ERROR [App] Another error
        """.trimIndent()

        val result = toolSet.getLogStats(log)

        assertTrue(result.contains("ERROR : 2"), "Должно быть 2 строки ERROR")
        assertTrue(result.contains("WARN  : 1"),  "Должна быть 1 строка WARN")
        assertTrue(result.contains("INFO  : 1"),  "Должна быть 1 строка INFO")
        assertTrue(result.contains("DEBUG : 1"),  "Должна быть 1 строка DEBUG")
    }

    @Test
    fun `getLogStats содержит информацию о чанках`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Start"

        val result = toolSet.getLogStats(log)

        assertTrue(result.contains("Total chunks"), "Должна быть информация о чанках")
    }

    @Test
    fun `getLogStats содержит количество строк и символов`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Start\n2026-04-10 08:00:02 INFO  [App] Stop"

        val result = toolSet.getLogStats(log)

        assertTrue(result.contains("Total lines"), "Должно быть количество строк")
        assertTrue(result.contains("Total chars"), "Должно быть количество символов")
    }

    @Test
    fun `getLogStats подсчёт регистронезависим`() {
        val log = "error happened\nERROR another\nError mixed"

        val result = toolSet.getLogStats(log)

        // Все три должны засчитаться как ERROR
        assertTrue(result.contains("ERROR : 3"), "Регистронезависимый поиск должен найти 3 строки")
    }

    // ─── getLogChunk ──────────────────────────────────────────────────────────

    @Test
    fun `getLogChunk возвращает первый чанк по индексу 0`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Entry"

        val result = toolSet.getLogChunk(log, 0)

        assertFalse(result.startsWith("ERROR"), "Ожидается успешный результат")
        assertTrue(result.contains("Entry"), "Чанк должен содержать текст записи")
    }

    @Test
    fun `getLogChunk возвращает ошибку для пустого контента`() {
        val result = toolSet.getLogChunk("", 0)

        assertTrue(result.startsWith("ERROR"), "Ожидается ошибка для пустого контента")
    }

    @Test
    fun `getLogChunk возвращает ошибку для отрицательного индекса`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Entry"

        val result = toolSet.getLogChunk(log, -1)

        assertTrue(result.startsWith("ERROR"), "Ожидается ошибка для отрицательного индекса")
        assertTrue(result.contains("out of range"), "Сообщение должно содержать 'out of range'")
    }

    @Test
    fun `getLogChunk возвращает ошибку для индекса за пределами диапазона`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Only one entry"

        val result = toolSet.getLogChunk(log, 99)

        assertTrue(result.startsWith("ERROR"), "Ожидается ошибка для слишком большого индекса")
        assertTrue(result.contains("out of range"), "Сообщение должно содержать 'out of range'")
    }

    @Test
    fun `getLogChunk включает номер чанка в ответ`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Entry"

        val result = toolSet.getLogChunk(log, 0)

        assertTrue(result.contains("Chunk"), "Ответ должен содержать слово 'Chunk'")
    }
}
