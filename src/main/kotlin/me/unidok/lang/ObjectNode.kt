package me.unidok.lang

interface ASTNode

interface TopLevelNode<R> : ASTNode {
    fun getIr(context: Context): R
}

interface ExpressionNode<R> : ASTNode {
    fun compile(context: Context, code: CodeWriter): R
}

class ObjectExpressionNode(val node: ObjectNode) : ExpressionNode<ValueType> {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val type = node.compile(context, code)
        if (type != ValueType.VOID) code.write(Opcodes.POP)
        return type
    }
}

interface ObjectNode : ASTNode {
    fun compile(context: Context, code: CodeWriter): ValueType
}

class ReturnNode(
    val value: ObjectNode?
) : ExpressionNode<ValueType> {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val type = value?.compile(context, code)
        code.write(Opcodes.RETURN)
        return type ?: ValueType.VOID
    }
}

class AsmNode(
    val opcode: Int
) : ExpressionNode<Unit> {
    override fun compile(context: Context, code: CodeWriter) {
        code.write(opcode)
    }
}

class ArgumentNode(val name: String, val type: ValueType) : TopLevelNode<LocalVariableIr> {
    override fun getIr(context: Context): LocalVariableIr {
        val function = context.lookup<FunctionIr>()
        val index = function.variablesAmount++
        if (type == ValueType.VOID) context.exception("Variable type cannot be void")
        val symbol = LocalVariableIr(name, this.type, null, index)
        if (function.args.put(name, symbol) != null) {
            context.exception("Variable '$name' already declared")
        }
        return symbol
    }
}

class FunctionNode(
    val name: String,
    val args: List<ArgumentNode>,
    val returnType: ValueType,
    val body: List<ExpressionNode<*>>,
) : TopLevelNode<FunctionIr> {
    override fun getIr(context: Context): FunctionIr {
        return FunctionIr(context, name, returnType, body).also { function ->
            args.asReversed().associateTo(function.args) { it.name to it.getIr(function) }
        }
    }
}

class VariableNode(
    val name: String,
    val type: ValueType,
    val value: ObjectNode? = null
) : TopLevelNode<VariableIr> {
    override fun getIr(context: Context): VariableIr {
        val program = context.lookup<Program>()
        val index = program.variablesAmount++
        val type = value?.compile(context, program.main)
        if (type != null) {
            if (type == ValueType.VOID) context.exception("Variable type cannot be void")
            if (type != this.type) context.exception("Type mismatch: $type != ${this.type}")
            program.main.write(Opcodes.GSTORE)
            program.main.write(index)
        }
        val symbol = VariableIr(name, this.type, value, index)
        if (!context.declareSymbol(symbol)) {
            context.exception("Global variable '$name' already declared")
        }
        return symbol
    }
}


class LocalVariableNode(
    val name: String,
    val type: ValueType,
    val value: ObjectNode? = null
) : ExpressionNode<LocalVariableIr> {
    override fun compile(context: Context, code: CodeWriter): LocalVariableIr {
        val function = context.lookup<FunctionIr>()
        val index = function.variablesAmount++
        val type = value?.compile(context, code)
        if (type != null) {
            if (type == ValueType.VOID) context.exception("Variable type cannot be void")
            if (type != this.type) context.exception("Type mismatch: $type != ${this.type}")
            code.write(Opcodes.STORE)
            code.write(index)
        }
        val symbol = LocalVariableIr(name, this.type, value, index)
        if (!context.declareSymbol(symbol)) {
            context.exception("Variable '$name' already declared")
        }
        return symbol
    }
}

class AssignmentNode(
    val obj: ObjectNode,
    val value: ObjectNode
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val obj = (obj as IdentifierNode).name
        val type = value.compile(context, code)
        val symbol = context.lookupSymbol(obj) ?: context.exception("Unknown variable '$obj'")
        when (symbol) {
            is VariableIr -> {
                if (type != symbol.type) context.exception("Type mismatch: $type != ${symbol.type}")
                code.write(Opcodes.GSTORE)
                code.write(symbol.index)
                return type
            }
            is LocalVariableIr -> {
                if (type != symbol.type) context.exception("Type mismatch: $type != ${symbol.type}")
                code.write(Opcodes.STORE)
                code.write(symbol.index)
                return type
            }
        }
        return type
    }
}


class InvokeNode(
    val name: String,
    val args: List<ObjectNode>
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val args = args.map { it.compile(context, code) }
        var function: FunctionIr? = null
        val choices = context.lookup<Program>().functionsMap[name] ?: emptyList()
        for (f in choices) {
            if (f.args.size != args.size) continue
            var index = 0
            if (f.args.values.any { it.type != args[index++] }) continue
            function = f
        }
        if (function == null) {
            context.exception("Unknown function '$name(${args.joinToString()})'")
        }
        code.write(Opcodes.INVOKE)
        code.write(function.index)
        return function.returnType
    }
}

class IdentifierNode(
    val name: String
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val symbol = context.lookupSymbol(name) ?: context.exception("Unknown symbol '$name'")
        return when (symbol) {
            is VariableIr -> {
                code.write(Opcodes.GLOAD)
                code.write(symbol.index)
                symbol.type
            }
            is LocalVariableIr -> {
                code.write(Opcodes.LOAD)
                code.write(symbol.index)
                symbol.type
            }
            is FunctionIr -> {
                code.write(Opcodes.PUSH)
                code.write(symbol.index)
                ValueType.VOIDPTR
            }
            else -> context.exception("Unknown symbol type '${symbol.javaClass}'")
        }
    }
}

class IntNode(
    val value: Int
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        code.write(Opcodes.PUSH)
        code.write(value)
        return ValueType.INT
    }
}

class LongNode(
    val value: Long
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        code.write(Opcodes.PUSH)
        code.write(value)
        return ValueType.LONG
    }
}

class FloatNode(
    val value: Float
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        code.write(Opcodes.PUSH)
        code.write(value)
        return ValueType.FLOAT
    }
}

class DoubleNode(
    val value: Double
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        code.write(Opcodes.PUSH)
        code.write(value)
        return ValueType.DOUBLE
    }
}

class StringNode(
    val value: String
) : ObjectNode {
    override fun compile(context: Context, code: CodeWriter): ValueType {
        val index = context.lookup<Program>().constant(value)
        code.write(Opcodes.LDC)
        code.write(index)
        return ValueType.STRING
    }
}

class BinaryNode(
    val operand1: ObjectNode,
    val operand2: ObjectNode,
    val type: Type
) : ObjectNode {
    enum class Type {
        PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER
    }

    override fun compile(context: Context, code: CodeWriter): ValueType {
        val type1 = operand1.compile(context, code)
        val code2 = CodeWriter()
        val type2 = operand2.compile(context, code2)
        val valueType = if (type1 == ValueType.DOUBLE || type2 == ValueType.DOUBLE) {
            ValueType.DOUBLE
        } else {
            ValueType.LONG
        }
        if (type1 != valueType && type1 == ValueType.LONG) {
            code.write(Opcodes.L2D)
        }
        code.write(code2)
        if (type2 != valueType && type2 == ValueType.LONG) {
            code.write(Opcodes.L2D)
        }
        when (type) {
            Type.PLUS -> when (valueType) {
                ValueType.LONG -> code.write(Opcodes.LADD)
                ValueType.DOUBLE -> code.write(Opcodes.DADD)
            }
            Type.MINUS -> when (valueType) {
                ValueType.LONG -> code.write(Opcodes.LSUB)
                ValueType.DOUBLE -> code.write(Opcodes.DSUB)
            }
            Type.MULTIPLY -> when (valueType) {
                ValueType.LONG -> code.write(Opcodes.LMUL)
                ValueType.DOUBLE -> code.write(Opcodes.DMUL)
            }
            Type.DIVIDE -> when (valueType) {
                ValueType.LONG -> code.write(Opcodes.LDIV)
                ValueType.DOUBLE -> code.write(Opcodes.DDIV)
            }
            Type.REMAINDER -> when (valueType) {
                ValueType.LONG -> code.write(Opcodes.LREM)
                ValueType.DOUBLE -> code.write(Opcodes.DREM)
            }
        }
        return valueType
    }
}

class UnaryNode(
    val operand: ObjectNode,
    val type: Type
) : ObjectNode {
    enum class Type {
        PLUS, MINUS
    }

    override fun compile(context: Context, code: CodeWriter): ValueType {
        val valueType = operand.compile(context, code)
        when (type) {
            Type.PLUS -> {}
            Type.MINUS -> code.write(Opcodes.LNEG)
        }
        return valueType
    }
}