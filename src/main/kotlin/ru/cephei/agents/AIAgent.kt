package ru.cephei.agents


class AIAgent(
    private val name: String,
    private val executor: PromptExecutor,
    private val systemPrompt: String
) {

    suspend fun run(userPrompt: String): String {
        val fullPrompt = "$systemPrompt\n$userPrompt"
        return executor.execute(fullPrompt)
    }

}