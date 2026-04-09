package ru.cephei

import kotlinx.coroutines.runBlocking
import ru.cephei.agents.AIAgent
import ru.cephei.agents.DefaultPromptExecutor
import ru.cephei.services.MockLLMService

fun main() = runBlocking {

    val llmService = MockLLMService()
    val executor = DefaultPromptExecutor(llmService)

    val agent = AIAgent(
        name = "ProdAgent",
        executor = executor,
        systemPrompt = "You are a helpful assistant"
    )

    val result = agent.run("Hello!")
    println(result)
}