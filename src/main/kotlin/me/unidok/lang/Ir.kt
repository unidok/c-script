package me.unidok.lang

interface Symbol {
    val name: String
}

interface Context {
    val parent: Context?
    fun getSymbol(name: String): Symbol?
    fun declareSymbol(symbol: Symbol): Boolean

    fun exception(message: String): Nothing {
        throw CompilationException(message)
    }
}

abstract class LocalContext : Context {
    private val locals = HashMap<String, Symbol>()

    override fun declareSymbol(symbol: Symbol): Boolean {
        return locals.put(symbol.name, symbol) == null
    }

    override fun getSymbol(name: String): Symbol? {
        return locals[name]
    }
}

fun Context.lookupSymbol(name: String): Symbol? {
    var context: Context? = this
    while (context != null) {
        val symbol = context.getSymbol(name)
        if (symbol != null) return symbol
        context = context.parent

    }
    return null
}

inline fun <reified T> Context.lookup(): T {
    return lookup(T::class.java)
}

fun <T> Context.lookup(clazz: Class<T>): T {
    var context: Context? = this
    while (context != null) {
        if (clazz.isInstance(context)) return clazz.cast(context)
        context = context.parent
    }
    throw CompilationException("Context ${clazz.simpleName} not found")
}

class VariableIr(
    override val name: String,
    val type: ValueType,
    val value: ObjectNode?,
    val index: Int
) : Symbol

class LocalVariableIr(
    override val name: String,
    val type: ValueType,
    val value: ObjectNode?,
    val index: Int
) : Symbol

class FunctionIr(
    override val parent: Context,
    override val name: String,
    val returnType: ValueType,
    val body: List<ExpressionNode<*>>,
) : LocalContext(), Symbol {
    val args = LinkedHashMap<String, LocalVariableIr>()
    var index = -1
    var variablesAmount = 0
    val initCode = CodeWriter()

    override fun getSymbol(name: String): Symbol? {
        return super.getSymbol(name) ?: args[name]
    }
}

data class PointerType(
    val to: ValueType,
    val isConst: Boolean = false
) : ValueType {
    override fun toString(): String {
        return "$to*" + if (isConst) " const" else ""
    }
}

data class SimpleType(
    val name: String,
    val isConst: Boolean = false
) : ValueType {
    override fun toString(): String {
        return (if (isConst) "const " else "") + name
    }
}

interface ValueType {
    companion object {
        val INT = SimpleType("int")
        val LONG = SimpleType("long")
        val FLOAT = SimpleType("float")
        val DOUBLE = SimpleType("double")
        val CHAR = SimpleType("char")
        val CONST_CHAR = SimpleType("char", true)
        val STRING = PointerType(CONST_CHAR)
        val VOID = SimpleType("void")
        val VOIDPTR = PointerType(VOID)
    }
}