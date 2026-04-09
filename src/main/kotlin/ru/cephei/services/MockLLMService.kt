package ru.cephei.services

class MockLLMService : LLMService {
    override suspend fun sendPrompt(prompt: String): String {
        return "LLM response: $prompt"
    }
}