package ru.cephei.agents

import ru.cephei.services.LLMService

class DefaultPromptExecutor(private val llmService: LLMService) : PromptExecutor {
    override suspend fun execute(prompt: String): String {
        return llmService.sendPrompt(prompt)
    }
}