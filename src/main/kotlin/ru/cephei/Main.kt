package ru.cephei

import kotlinx.coroutines.runBlocking
import ru.cephei.agents.AIAgent
import ru.cephei.agents.DefaultPromptExecutor
import ru.cephei.services.MockLLMService
import ru.cephei.tools.*

fun main() = runBlocking {

    val llmService = MockLLMService()
    val executor = DefaultPromptExecutor(llmService)

    val tools = ToolRegistry(listOf(EchoTool(), TimeTool()))

    val agent = AIAgent(
        name = "AgentWithTools",
        executor = executor,
        systemPrompt = "You are a helpful assistant",
        toolRegistry = tools
    )

    println(agent.run("Hello"))
    println(agent.run("tool:echo"))
    println(agent.run("tool:time"))
}