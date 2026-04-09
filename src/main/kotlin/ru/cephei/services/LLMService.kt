package ru.cephei.services

interface LLMService {
    suspend fun sendPrompt(prompt: String): String
}