# koog-api-reference.md — Справочник API Koog 0.7.3

Источники (верифицировано):
- Реальные .class файлы из Gradle-кэша (0.7.3)
- Рабочий код из вебинара («вебинар по koog.txt»)

Использовать вместо повторного фетча документации.

---

## Зависимости (build.gradle.kts)

```kotlin
dependencies {
    implementation("ai.koog:koog-agents:$koog_version")       // всё ядро + features
    implementation("ai.koog:agents-ext-jvm:$koog_version")    // файловые тулы, shell, search
    implementation("ch.qos.logback:logback-classic:1.5.16")   // логирование
}
```

`koog-agents` транзитивно включает: `agents-core`, `agents-tools`, `agents-features-event-handler`,
`prompt-executor-llms-all`, `prompt-executor-openai-client`, `prompt-executor-anthropic-client`.

---

## Создание агента (синтаксис из вебинара — ПРОВЕРЕН)

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels

val agent = AIAgent(
    promptExecutor = MultiLLMPromptExecutor(
        OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY"))
    ),
    systemPrompt   = "You are a helpful assistant",
    toolRegistry   = ToolRegistry { tool(myTool) },
    strategy       = singleRunStrategy(),          // или graphStrategy
    maxIterations  = 30,
    llmModel       = OpenAIModels.Chat.GPT4o,
) {
    // trailing lambda — установка features
    install(EventHandler) {
        onToolCallStarting { ctx -> println("Tool: ${ctx.toolName}(${ctx.toolArgs})") }
        onAgentFinished   { name, result -> println("Done: $name") }
    }
}

val result: String = agent.run("Hello")
```

---

## Executor-ы

| Функция | Пакет | Описание |
|---------|-------|----------|
| `MultiLLMPromptExecutor(vararg LLMClient)` | `ai.koog.prompt.executor.llms` | Несколько провайдеров, маршрутизация по модели |
| `simpleOpenAIExecutor(apiKey)` | `ai.koog.prompt.executor.llms.all` | Только OpenAI, SingleLLMPromptExecutor |
| `simpleAnthropicExecutor(apiKey)` | `ai.koog.prompt.executor.llms.all` | Только Anthropic |

Рекомендуется `MultiLLMPromptExecutor` — позволяет переключать модель в рантайме.

---

## Модели

```kotlin
// OpenAI — пакет: ai.koog.prompt.executor.clients.openai
OpenAIModels.Chat.GPT4o
OpenAIModels.Chat.GPT4oMini

// Anthropic — пакет: ai.koog.prompt.executor.clients.anthropic
AnthropicModels.Sonnet_4_5   // рекомендуется для tool calling
AnthropicModels.Sonnet_4
AnthropicModels.Opus_4
AnthropicModels.Haiku_4_5
```

---

## Стратегии

```kotlin
// Простая стратегия (пакет: ai.koog.agents.core.agent)
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.agent.ToolCalls

singleRunStrategy()                         // параллельные tool calls (default)
singleRunStrategy(ToolCalls.SEQUENTIAL)     // последовательные tool calls

// Граф-стратегия (пакеты из вебинара)
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory

strategy<String, String>("name") {
    val step1 by subgraphWithTask<String, OutputType>(tools = allTools) { input ->
        "Prompt for step1: $input"
    }
    val step2 by subgraphWithTask<OutputType, FinalType>(tools = someTools) { output ->
        "Prompt for step2: $output"
    }
    val decide by nodeDoNothing<FinalType>()

    edge(nodeStart forwardTo step1)
    edge(step1 forwardTo step2)
    edge(step2 forwardTo decide onCondition { it.hasProblems() })
    edge(step2 forwardTo nodeFinish onCondition { !it.hasProblems() } transformed { "Done!" })
    edge(decide forwardTo step1)
}
```

---

## ToolRegistry

```kotlin
import ai.koog.agents.core.tools.ToolRegistry

ToolRegistry()                    // пустой
ToolRegistry { tool(myTool) }     // DSL с тулами
ToolRegistry.builder()            // явный builder
```

---

## Тулы: annotation-based (ПРОВЕРЕНО из jar)

```kotlin
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Description of the tool set")
class MyTools : ToolSet {

    @Tool
    @LLMDescription("What this tool does")
    fun myTool(
        @LLMDescription("What this param means") input: String
    ): String = "result"
}
```

**Важно для data class:** использовать `@property:LLMDescription(...)`, а не просто `@LLMDescription`:
```kotlin
@Serializable
@LLMDescription("Summary of changes")
data class ChangesSummary(
    @property:LLMDescription("Why changes were made")   // ← @property: обязателен!
    val goal: String,
    @property:LLMDescription("List of changes")
    val changes: List<String>
)
```

---

## Ext-тулы из agents-ext-jvm (ПРОВЕРЕНО из вебинара)

```kotlin
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.search.RegexSearchTool
import ai.koog.agents.ext.tool.shell.ExecuteShellCommandTool
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
import ai.koog.agents.ext.tool.shell.BraveModeConfirmationHandler
import ai.koog.rag.base.files.JVMFileSystemProvider

val fs   = JVMFileSystemProvider.ReadOnly
val fsRW = JVMFileSystemProvider.ReadWrite

val readFile      = ReadFileTool(fs)
val writeFile     = WriteFileTool(fsRW)
val listDir       = ListDirectoryTool(fs)
val search        = RegexSearchTool(fs)
val executeCommand = ExecuteShellCommandTool(JvmShellCommandExecutor(), BraveModeConfirmationHandler())
```

---

## EventHandler (features)

```kotlin
import ai.koog.agents.features.eventHandler.feature.EventHandler

install(EventHandler) {
    onAgentStarted  { agentName -> ... }
    onToolCallStarting { ctx -> println(ctx.toolName, ctx.toolArgs) }
    onToolCallResult   { ctx -> println(ctx.toolResult) }
    onAgentFinished { name, result -> ... }
}
```

---

## History Compression (для длинных сессий)

```kotlin
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

val compressionStrategy = RetrieveFactsFromHistory(
    Concept("project-structure", "What is the structure of this project?", FactType.MULTIPLE),
    Concept("agent-goal",        "What is the primary goal of this agent?", FactType.SINGLE),
)

val compress by nodeLLMCompressHistory<MyType>(strategy = compressionStrategy)

// В графе:
edge(test forwardTo compress onCondition { llm.historyTooLong() })
edge(compress forwardTo nextNode)
```

---

## Версия в проекте

`gradle.properties`: `koog_version=0.7.3`
