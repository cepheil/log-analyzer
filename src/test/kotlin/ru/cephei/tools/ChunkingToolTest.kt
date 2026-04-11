package ru.cephei.tools

import ru.cephei.model.LogChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для [ChunkingTool] — утилиты разбивки лога на чанки.
 *
 * Проверяет два публичных метода:
 * - [ChunkingTool.splitByLogEntries] — разбивка по маркерам даты
 * - [ChunkingTool.split] — группировка записей в чанки по размеру
 */
class ChunkingToolTest {

    private val tool = ChunkingTool()

    // ─── splitByLogEntries ─────────────────────────────────────────────────────

    @Test
    fun `splitByLogEntries разбивает лог по маркерам даты`() {
        val log = """
            2026-04-10 08:00:01 INFO  [App] Starting
            2026-04-10 08:00:02 INFO  [DB] Connecting
            2026-04-10 08:01:00 ERROR [App] Failed
        """.trimIndent()

        val entries = tool.splitByLogEntries(log)

        assertEquals(3, entries.size)
        assertTrue(entries[0].startsWith("2026-04-10 08:00:01"))
        assertTrue(entries[1].startsWith("2026-04-10 08:00:02"))
        assertTrue(entries[2].startsWith("2026-04-10 08:01:00"))
    }

    @Test
    fun `splitByLogEntries возвращает пустой список для пустой строки`() {
        val entries = tool.splitByLogEntries("")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `splitByLogEntries возвращает одну запись если дат нет`() {
        val log = "Это просто текст без дат"
        val entries = tool.splitByLogEntries(log)
        assertEquals(1, entries.size)
    }

    @Test
    fun `splitByLogEntries сохраняет маркер даты в начале каждой записи`() {
        val log = "2026-01-01 INFO first\n2026-01-02 INFO second"
        val entries = tool.splitByLogEntries(log)
        // lookahead не удаляет дату
        assertTrue(entries.all { it.trimStart().matches(Regex("\\d{4}-\\d{2}-\\d{2}.*", RegexOption.DOT_MATCHES_ALL)) })
    }

    @Test
    fun `splitByLogEntries пропускает пустые сегменты`() {
        // Несколько пустых строк между записями не должны создавать лишние сегменты
        val log = "\n\n2026-04-10 08:00:01 INFO first\n\n2026-04-10 08:00:02 INFO second\n\n"
        val entries = tool.splitByLogEntries(log)
        assertEquals(2, entries.size)
    }

    // ─── split ────────────────────────────────────────────────────────────────

    @Test
    fun `split возвращает пустой список для пустого лога`() {
        val chunks = tool.split("")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `split возвращает один чанк если лог меньше maxChunkSize`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Starting\n"
        val chunks = tool.split(log)
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].id)
    }

    @Test
    fun `split присваивает последовательные ID чанкам`() {
        // Создаём лог с записями, которые суммарно превысят дефолтный maxChunkSize=2000
        val entry = "2026-04-10 08:00:01 INFO  [App] " + "X".repeat(600) + "\n"
        val log = entry.repeat(5) // каждая запись ~634 символа, 5 записей → 2+ чанка
        val chunks = tool.split(log)

        assertTrue(chunks.size > 1, "Ожидается более одного чанка")
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.id, "ID чанка должен совпадать с его позицией")
        }
    }

    @Test
    fun `split с маленьким maxChunkSize создаёт много чанков`() {
        val smallTool = ChunkingTool(maxChunkSize = 50)
        val log = """
            2026-04-10 08:00:01 INFO  entry one
            2026-04-10 08:00:02 INFO  entry two
            2026-04-10 08:00:03 INFO  entry three
        """.trimIndent()

        val chunks = smallTool.split(log)

        // При лимите 50 символов каждая запись должна уйти в отдельный чанк
        assertTrue(chunks.size >= 3, "При маленьком лимите ожидается >= 3 чанков")
    }

    @Test
    fun `split не теряет контент`() {
        val log = """
            2026-04-10 08:00:01 INFO  [App] Start
            2026-04-10 08:00:02 ERROR [DB] Fail
        """.trimIndent()

        val chunks = tool.split(log)
        val allContent = chunks.joinToString("\n") { it.content }

        assertTrue(allContent.contains("Start"), "Контент 'Start' должен сохраниться")
        assertTrue(allContent.contains("Fail"), "Контент 'Fail' должен сохраниться")
    }

    @Test
    fun `split не создаёт чанки с content превышающим maxChunkSize`() {
        val maxSize = 200
        val sizedTool = ChunkingTool(maxChunkSize = maxSize)

        // Каждая запись ~80 символов — помещается в лимит
        val log = (1..10).joinToString("") {
            "2026-04-10 08:00:0$it INFO  [App] Entry number $it — some log message here\n"
        }

        val chunks = sizedTool.split(log)

        // Ни один чанк не должен превышать maxChunkSize (с учётом trim)
        chunks.forEach { chunk ->
            assertTrue(
                chunk.content.length <= maxSize,
                "Чанк ${chunk.id} (${chunk.content.length} символов) превышает лимит $maxSize"
            )
        }
    }

    @Test
    fun `split trimmed content не содержит ведущих и завершающих пробелов`() {
        val log = "2026-04-10 08:00:01 INFO  [App] Test\n"
        val chunks = tool.split(log)
        assertEquals(1, chunks.size)
        assertEquals(chunks[0].content, chunks[0].content.trim())
    }
}
