package ru.cephei.tools

import java.time.LocalDateTime

class TimeTool : Tool {
    override val name = "time"

    override val description = "Returns current server time"

    override suspend fun execute(input: String): String {
        return "Current time: ${LocalDateTime.now()}"
    }
}