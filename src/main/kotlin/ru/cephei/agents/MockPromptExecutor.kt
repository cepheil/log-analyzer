package ru.cephei.agents

class MockPromptExecutor : PromptExecutor{

    override suspend fun execute(prompt: String): String {
        return "Mock response to: $prompt"
    }
}