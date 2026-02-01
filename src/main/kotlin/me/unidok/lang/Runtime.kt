package me.unidok.lang

private fun LongArray.grow(minSize: Int, maxSize: Int): LongArray {
    if (size >= maxSize) throw StackOverflowError("Stack size reached over $maxSize")
    return copyInto(LongArray(newCapacity(size, minSize, maxSize)))
}

private fun Array<Trace?>.grow(minSize: Int, maxSize: Int): Array<Trace?> {
    if (size >= maxSize) throw StackOverflowError("Stack size reached over $maxSize")
    return copyInto(arrayOfNulls(newCapacity(size, minSize, maxSize)))
}

private class Trace(
    @JvmField val function: Int,
    @JvmField var index: Int,
    @JvmField val locals: LongArray
)

class Runtime {
    @JvmField var isActive = true
}

fun executeProgram(
    main: Int,
    constants: Array<String>,
    variables: LongArray,
    functions: Array<LongArray>,
    maxStack: Int,
    maxStackTrace: Int,
    maxHeap: Int
) {
    var stack = LongArray(10)
    var s = -1

    var stackTrace = arrayOfNulls<Trace>(10)
    var t = 0

    var f = main
    var i = 0
    var code = functions.getOrElse(f) { throw NoSuchElementException() }
    var locals = LongArray(code[i++].toInt())

    stackTrace[0] = Trace(f, i, locals)

    while (true) {
        if (i >= code.size) {
            stackTrace[t] = null
            if (t == 0) return
            val trace = stackTrace[--t]!!
            f = trace.function
            i = trace.index
            locals = trace.locals
            code = functions[f]
            continue
        }

        val opcode = code[i]
        when (opcode) {
            Opcodes.PUSH -> {
                if (++s >= stack.size) stack = stack.grow(s, maxStack)
                stack[s] = code[++i]
            }
            Opcodes.LDC -> {
                if (++s >= stack.size) stack = stack.grow(s, maxStack)
                stack[s] = code[++i]
            }
            Opcodes.POP -> --s
            Opcodes.STORE -> {
                locals[code[++i].toInt()] = stack[s--]
            }
            Opcodes.LOAD -> {
                if (++s >= stack.size) stack = stack.grow(s, maxStack)
                stack[s] = locals[code[++i].toInt()]
            }
            Opcodes.L2D -> {
                stack[s] = stack[s].toDouble().toBits()
            }
            Opcodes.D2L -> {
                stack[s] = Double.fromBits(stack[s]).toLong()
            }
            Opcodes.LADD -> {
                val b = stack[s]
                val a = stack[--s]
                stack[s] = a + b
            }
            Opcodes.LSUB -> {
                val b = stack[s]
                val a = stack[--s]
                stack[s] = a - b
            }
            Opcodes.LMUL -> {
                val b = stack[s]
                val a = stack[--s]
                stack[s] = a * b
            }
            Opcodes.LDIV -> {
                val b = stack[s]
                val a = stack[--s]
                stack[s] = a / b
            }
            Opcodes.LREM -> {
                val b = stack[s]
                val a = stack[--s]
                stack[s] = a % b
            }
            Opcodes.LNEG -> stack[s] = -stack[s]
            Opcodes.DADD -> {
                val b = Double.fromBits(stack[s])
                val a = Double.fromBits(stack[--s])
                stack[s] = (a + b).toBits()
            }
            Opcodes.DSUB -> {
                val b = Double.fromBits(stack[s])
                val a = Double.fromBits(stack[--s])
                stack[s] = (a - b).toBits()
            }
            Opcodes.DMUL -> {
                val b = Double.fromBits(stack[s])
                val a = Double.fromBits(stack[--s])
                stack[s] = (a * b).toBits()
            }
            Opcodes.DDIV -> {
                val b = Double.fromBits(stack[s])
                val a = Double.fromBits(stack[--s])
                stack[s] = (a / b).toBits()
            }
            Opcodes.DREM -> {
                val b = Double.fromBits(stack[s])
                val a = Double.fromBits(stack[--s])
                stack[s] = (a % b).toBits()
            }
            Opcodes.INVOKE -> {
                stackTrace[t]!!.index = i + 2
                if (++t >= stackTrace.size) {
                    stackTrace = stackTrace.grow(t, maxStackTrace)
                }
                f = code[i + 1].toInt()
                code = functions[f]
                locals = LongArray(code[0].toInt())
                i = 1
                stackTrace[t] = Trace(f, i, locals)
                continue
            }
            Opcodes.RETURN -> {
                i = code.size
                continue
            }
            Opcodes.GSTORE -> {
                variables[code[++i].toInt()] = stack[s--]
            }
            Opcodes.GLOAD -> {
                if (++s >= stack.size) stack = stack.grow(s, maxStack)
                stack[s] = variables[code[++i].toInt()]
            }
            Opcodes.LPRINT -> print(stack[s--])
            Opcodes.SPRINT -> print(constants[stack[s--].toInt()])
            else -> throw IllegalStateException("Unknown opcode: $opcode")
        }
        ++i
    }
}