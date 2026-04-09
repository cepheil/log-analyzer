package ru.cephei.tools

class ToolRegistry(private val tools: List<Tool>) {
    fun findTool(name: String): Tool? {
        return tools.find { it.name == name }
    }
    fun getAllTools(): List<Tool> = tools
}