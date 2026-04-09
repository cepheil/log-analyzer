package ru.cephei.tools

interface Tool {
    val name: String
    val description: String

    suspend fun execute(input: String): String
}