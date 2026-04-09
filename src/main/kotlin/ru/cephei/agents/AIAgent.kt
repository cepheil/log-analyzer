package ru.cephei.agents

import ru.cephei.tools.ToolRegistry


class AIAgent(
    private val name: String,
    private val executor: PromptExecutor,
    private val systemPrompt: String,
    private val toolRegistry: ToolRegistry
) {

    suspend fun run(userPrompt: String): String {
        // 🔥 Простейший парсинг: "tool:time"
        if (userPrompt.startsWith("tool:")) {
            val toolName = userPrompt.removePrefix("tool:")
            val tool = toolRegistry.findTool(toolName)

            return tool?.execute("") ?: "Tool not found: $toolName"
        }

        val fullPrompt = "$systemPrompt\n$userPrompt"
        return executor.execute(fullPrompt)
    }

}