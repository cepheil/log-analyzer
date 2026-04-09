package ru.cephei.tools

class EchoTool : Tool {

    override val name = "echo"

    override val description = "Returns the same input back"

    override suspend fun execute(input: String): String {
        return "Echo: $input"
    }
}