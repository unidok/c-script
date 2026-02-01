package me.unidok.lang

import java.lang.Exception

class CodeWriter() {
    private var array = LongArray(10)

    var size = 0
        private set

    private fun ensureCapacity(minSize: Int = size + 1) {
        if (minSize > array.size) {
            array = array.copyInto(LongArray(newCapacity(array.size, minSize, 8192)), endIndex = size)
        }
    }

    fun writeAt(index: Int, code: Long) {
        array[index] = code
    }

    fun write(code: Int) {
        write(code.toLong())
    }

    fun write(code: Long) {
        ensureCapacity()
        array[size++] = code
    }

    fun write(code: Float) {
        write(code.toBits().toLong())
    }

    fun write(code: Double) {
        write(code.toBits())
    }

    fun write(code: CodeWriter) {
        val newSize = size + code.size
        ensureCapacity(newSize)
        code.array.copyInto(array, endIndex = newSize)
        size = newSize
    }

    fun close(): LongArray {
        if (array.size == size) return array
        return array.copyInto(LongArray(size), endIndex = size)
    }
}

class CompiledProgram(
    val main: Int,
    val constants: Array<String>,
    val variables: LongArray,
    val functions: Array<LongArray>,
) {
    fun execute(
        maxStackMemory: Int,
        maxStackTrace: Int,
        maxHeapMemory: Int
    ) {
        executeProgram(
            main,
            constants,
            variables,
            functions,
            maxStackMemory / Long.SIZE_BYTES,
            maxStackTrace,
            maxHeapMemory / Long.SIZE_BYTES
        )
    }
}

class Program(
    val variables: List<VariableNode>,
    val functions: List<FunctionNode>,
) : Context {
    private val constants = ArrayList<String>()
    val variablesMap = HashMap<String, VariableIr>()
    val functionsMap = HashMap<String, ArrayList<FunctionIr>>()
    val main = CodeWriter()
    var variablesAmount = 0

    override val parent: Context?
        get() = null

    override fun declareSymbol(symbol: Symbol): Boolean {
        val name = symbol.name
        return when (symbol) {
            is VariableIr -> variablesMap.put(name, symbol) != null
            is FunctionIr -> {
                val list = functionsMap[name]
                when {
                    list == null -> {
                        functionsMap.put(name, arrayListOf(symbol))
                        true
                    }
                    list.contains(symbol) -> false
                    else -> list.add(symbol)
                }
            }
            else -> exception("$symbol")
        }
    }

    override fun getSymbol(name: String): Symbol? {
        return variablesMap[name] ?: functionsMap[name]?.last()
    }

    fun constant(constant: String): Int {
        val index = constants.indexOf(constant)
        if (index == -1) {
            constants.add(constant)
            return constants.size - 1
        }
        return index
    }

    @Suppress("UNCHECKED_CAST")
    fun compile(): CompiledProgram {
        for (variable in variables) {
            TODO()
            variable.name
            variablesMap
        }

        var main = -1
        var index = 0
        val functionIrs = ArrayList<FunctionIr>()
        for (function in this.functions) {
            if (function.name == "main" && function.args.isEmpty()) {
                main = index
            }
            val function = function.getIr(this)
            if (!declareSymbol(function)) {
                exception("Function $function already declared")
            }
            functionIrs.add(function)
            function.index = index++
        }

        if (main == -1) {
            exception("Main function not found")
        }

        val variables = LongArray(variablesMap.size)
        val functions = arrayOfNulls<LongArray>(functionIrs.size)

        index = 0
        for (function in functionIrs) {
            val code = CodeWriter()
            code.write(0)
            code.write(function.initCode)
            for (expr in function.body) {
                expr.compile(function, code)
            }
            val variablesAmount = function.variablesAmount
            if (variablesAmount != 0) {
                code.writeAt(0, variablesAmount.toLong())
            }
            functions[index++] = code.close()
        }

        return CompiledProgram(main, constants.toTypedArray(), variables, functions as Array<LongArray>)
    }
}

class CompilationException(message: String?) : Exception(message)

fun newCapacity(capacity: Int, minCapacity: Int, maxCapacity: Int): Int {
    if (capacity >= maxCapacity) throw IllegalStateException("Capacity over $maxCapacity")

    val newSize = capacity + (capacity shr 1) // Увеличиваем на 50%

    if (newSize < minCapacity) {
        return minCapacity
    }

    if (newSize > maxCapacity) {
        return maxCapacity
    }

    return newSize
}