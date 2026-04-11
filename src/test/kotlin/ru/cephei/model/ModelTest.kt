package ru.cephei.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для доменных моделей проекта.
 *
 * Проверяет вычисляемые свойства и методы:
 * - [ChunkList.totalContentSize]
 * - [AnalysisList.totalErrors]
 * - [AnalysisList.chunksWithIssues]
 * - [ChunkAnalysis.hasIssues]
 */
class ModelTest {

    // ─── Фабричные методы для тестовых данных ─────────────────────────────────

    private fun makeAnalysisResult(
        summary: String = "",
        issues: List<String> = emptyList(),
        errorCount: Int = 0,
        hasRootCause: Boolean = false
    ) = AnalysisResult(summary, issues, errorCount, hasRootCause)

    private fun makeChunkAnalysis(
        chunkId: Int = 0,
        issues: List<String> = emptyList(),
        errorCount: Int = 0
    ) = ChunkAnalysis(chunkId, makeAnalysisResult(issues = issues, errorCount = errorCount))

    // ─── LogChunk ─────────────────────────────────────────────────────────────

    @Test
    fun `LogChunk хранит id и content`() {
        val chunk = LogChunk(id = 3, content = "some log text")
        assertEquals(3, chunk.id)
        assertEquals("some log text", chunk.content)
    }

    // ─── ChunkList ────────────────────────────────────────────────────────────

    @Test
    fun `ChunkList totalContentSize равен нулю для пустого списка`() {
        val list = ChunkList(emptyList())
        assertEquals(0, list.totalContentSize)
    }

    @Test
    fun `ChunkList totalContentSize суммирует длины всех чанков`() {
        val chunks = listOf(
            LogChunk(0, "abc"),    // 3
            LogChunk(1, "de"),     // 2
            LogChunk(2, "fghij")   // 5
        )
        val list = ChunkList(chunks)
        assertEquals(10, list.totalContentSize)
    }

    @Test
    fun `ChunkList totalContentSize для одного чанка равен длине контента`() {
        val chunk = LogChunk(0, "hello world")
        val list = ChunkList(listOf(chunk))
        assertEquals(11, list.totalContentSize)
    }

    // ─── AnalysisResult ───────────────────────────────────────────────────────

    @Test
    fun `AnalysisResult со значениями по умолчанию имеет errorCount=0 и hasRootCause=false`() {
        val result = AnalysisResult(summary = "ok", issues = emptyList())
        assertEquals(0, result.errorCount)
        assertFalse(result.hasRootCause)
    }

    @Test
    fun `AnalysisResult хранит список проблем`() {
        val issues = listOf("issue 1", "issue 2", "issue 3")
        val result = makeAnalysisResult(issues = issues, errorCount = 3)
        assertEquals(3, result.issues.size)
        assertEquals(3, result.errorCount)
    }

    // ─── ChunkAnalysis ────────────────────────────────────────────────────────

    @Test
    fun `ChunkAnalysis hasIssues возвращает false если issues пустой`() {
        val analysis = makeChunkAnalysis(issues = emptyList())
        assertFalse(analysis.hasIssues())
    }

    @Test
    fun `ChunkAnalysis hasIssues возвращает true если есть хотя бы одна проблема`() {
        val analysis = makeChunkAnalysis(issues = listOf("DB timeout at 09:02"))
        assertTrue(analysis.hasIssues())
    }

    @Test
    fun `ChunkAnalysis hasIssues возвращает true для нескольких проблем`() {
        val analysis = makeChunkAnalysis(issues = listOf("issue A", "issue B", "issue C"))
        assertTrue(analysis.hasIssues())
    }

    @Test
    fun `ChunkAnalysis хранит chunkId`() {
        val analysis = makeChunkAnalysis(chunkId = 7)
        assertEquals(7, analysis.chunkId)
    }

    // ─── AnalysisList ─────────────────────────────────────────────────────────

    @Test
    fun `AnalysisList totalErrors равен нулю для пустого списка`() {
        val list = AnalysisList(emptyList())
        assertEquals(0, list.totalErrors)
    }

    @Test
    fun `AnalysisList totalErrors суммирует errorCount всех чанков`() {
        val list = AnalysisList(
            listOf(
                makeChunkAnalysis(chunkId = 0, errorCount = 3),
                makeChunkAnalysis(chunkId = 1, errorCount = 0),
                makeChunkAnalysis(chunkId = 2, errorCount = 5)
            )
        )
        assertEquals(8, list.totalErrors)
    }

    @Test
    fun `AnalysisList chunksWithIssues равен нулю если все чанки чистые`() {
        val list = AnalysisList(
            listOf(
                makeChunkAnalysis(chunkId = 0, issues = emptyList()),
                makeChunkAnalysis(chunkId = 1, issues = emptyList())
            )
        )
        assertEquals(0, list.chunksWithIssues)
    }

    @Test
    fun `AnalysisList chunksWithIssues считает только чанки с проблемами`() {
        val list = AnalysisList(
            listOf(
                makeChunkAnalysis(chunkId = 0, issues = listOf("error A")),
                makeChunkAnalysis(chunkId = 1, issues = emptyList()),
                makeChunkAnalysis(chunkId = 2, issues = listOf("error B", "error C"))
            )
        )
        assertEquals(2, list.chunksWithIssues)
    }

    @Test
    fun `AnalysisList chunksWithIssues равен размеру списка если все чанки с проблемами`() {
        val list = AnalysisList(
            listOf(
                makeChunkAnalysis(chunkId = 0, issues = listOf("e1")),
                makeChunkAnalysis(chunkId = 1, issues = listOf("e2"))
            )
        )
        assertEquals(2, list.chunksWithIssues)
    }

    // ─── Report ───────────────────────────────────────────────────────────────

    @Test
    fun `Report хранит все поля корректно`() {
        val report = Report(
            content = "## Summary\nAll good",
            totalErrors = 5,
            criticalIssues = listOf("DB down"),
            recommendations = listOf("Restart DB", "Check logs"),
            isValid = true
        )

        assertEquals("## Summary\nAll good", report.content)
        assertEquals(5, report.totalErrors)
        assertEquals(listOf("DB down"), report.criticalIssues)
        assertEquals(2, report.recommendations.size)
        assertTrue(report.isValid)
    }

    @Test
    fun `Report isValid может быть false`() {
        val report = Report(
            content = "",
            totalErrors = 0,
            criticalIssues = emptyList(),
            recommendations = emptyList(),
            isValid = false
        )
        assertFalse(report.isValid)
    }
}
