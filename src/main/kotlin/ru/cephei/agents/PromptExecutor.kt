package ru.cephei.agents

interface PromptExecutor {
    suspend fun execute(prompt: String): String
}