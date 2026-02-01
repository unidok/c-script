package me.unidok.lang

import java.io.File
import kotlin.time.measureTimedValue

fun main() {
    val program = Parser.parse(File("C:\\Users\\unidok\\IdeaProjects\\MyLang\\code\\test.c").readText()).compile()

    program.functions.forEachIndexed { index, code ->
        println("fun $index: " + code.joinToString())
    }

    val (_, time) = measureTimedValue {
        program.execute(
            maxStackMemory = 1024 * 1024,
            maxStackTrace = 100,
            maxHeapMemory = 0
        )
    }

    println("$time")
}