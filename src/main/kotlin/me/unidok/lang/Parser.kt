package me.unidok.lang

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ParserException(message: String) : Exception(message)

class Parser private constructor(
    private val lexer: Lexer
) {
    private val tokens = ArrayList<Token>()
    private var index = 0

    private fun read(): Boolean {
        val token = lexer.eat() ?: return false
        return tokens.add(token)
    }

    private fun peek(): Token {
        if (hasNext()) return tokens[index]
        exception("Expecting a token")
    }

    private fun eat(): Token {
        if (hasNext()) return tokens[index++]
        exception("Expecting a token")
    }

    private fun eatIdentifier(): String {
        val token = eat() as? Token.Identifier ?: exception("Expecting identifier")
        return token.value
    }

//    private fun eatArray(): List<ObjectNode> {
//
//    }

    private fun exception(reason: String): Nothing {
        throw ParserException("$reason at ${lexer.column}, line ${lexer.line}")
    }

    private fun eatBody(): List<ExpressionNode<*>> {
        if (!hasNext(TokenType.OPEN_CURLY)) exception("Expecting '{'")
        advance()
        val body = ArrayList<ExpressionNode<*>>()
        while (!hasNext(TokenType.CLOSE_CURLY)) {
            body.add(eatExpression())
        }
        if (!hasNext(TokenType.CLOSE_CURLY)) exception("Unclosed '}'")
        advance()
        return body
    }

    private fun eatExpression(): ExpressionNode<*> {
        val expr = when (peek().type) {
            TokenType.ASM -> {
                advance()
                val opcode = (eat() as Token.Int).value
                AsmNode(opcode)
            }
            TokenType.RETURN -> {
                advance()
                val value = if (hasNext(TokenType.SEMICOLON)) {
                    null
                } else {
                    eatObject()
                }
                ReturnNode(value)
            }
            else -> {
                safe {
                    val type = eatType()
                    val name = eatIdentifier()
                    var value: ObjectNode? = null
                    if (hasNext(TokenType.EQUAL)) {
                        advance()
                        value = eatObject()
                    }
                    LocalVariableNode(name, type, value)
                }.getOrElse {
                    ObjectExpressionNode(eatObject())
                }
            }
        }
        if (!hasNext(TokenType.SEMICOLON)) exception("Expecting ';'")
        advance()
        return expr
    }

    @Suppress("WRONG_INVOCATION_KIND")
    @OptIn(ExperimentalContracts::class)
    private inline fun <R> safe(block: () -> R): Result<R> {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        val index = index
        return try {
            Result.success(block())
        } catch (e: LexerException) {
            this.index = index
            Result.failure(e)
        } catch (e: ParserException) {
            this.index = index
            Result.failure(e)
        }
    }

    private fun eatType(): ValueType {
        val const = hasNext(TokenType.CONST)
        if (const) advance()
        var type: ValueType = SimpleType(eatIdentifier(), const)
        while (hasNext(TokenType.ASTERISK)) {
            advance()
            type = PointerType(type)
        }
        return type
    }

    fun parse(): Program {
        val variables = ArrayList<VariableNode>()
        val functions = ArrayList<FunctionNode>()
        while (hasNext()) {
            val token = peek()
            when (token.type) {
                TokenType.STRUCT -> {
                    advance()
                    TODO()
                }
                else -> {
                    safe {
                        val type = eatType()
                        val name = eatIdentifier()
                        if (!hasNext(TokenType.OPEN_PAREN)) {
                            var value: ObjectNode? = null
                            if (hasNext(TokenType.EQUAL)) {
                                advance()
                                value = eatObject()
                            }
                            variables.add(VariableNode(name, type, value))
                            continue
                        }
                        advance()
                        val args = ArrayList<ArgumentNode>()
                        while (!hasNext(TokenType.CLOSE_PAREN)) {
                            val type = eatType()
                            val name = eatIdentifier()
                            args.add(ArgumentNode(name, type))
                            if (hasNext(TokenType.CLOSE_PAREN)) break
                            if (!hasNext(TokenType.COMMA)) exception("Expecting ','")
                            advance()
                        }
                        if (!hasNext(TokenType.CLOSE_PAREN)) exception("Unclosed ')'")
                        advance()
                        val body = eatBody()
                        functions.add(FunctionNode(name, args, type, body))
                    }.getOrElse {
                        exception("Unexpected token ${token.type}")
                    }
                }
            }
        }
        return Program(variables, functions)
    }

    private fun hasNext(): Boolean {
        return index < tokens.size || read()
    }

    private fun hasNext(type: TokenType): Boolean {
        return hasNext() && peek().type == type
    }

    private fun advance() {
        index++
    }

    // Константы, функции
    private fun op0(): ObjectNode {
        val token = peek()
        return when (token.type) {
            TokenType.INT -> {
                advance()
                IntNode((token as Token.Int).value)
            }
            TokenType.LONG -> {
                advance()
                LongNode((token as Token.Long).value)
            }
            TokenType.FLOAT -> {
                advance()
                FloatNode((token as Token.Float).value)
            }
            TokenType.DOUBLE -> {
                advance()
                DoubleNode((token as Token.Double).value)
            }
            TokenType.STRING -> {
                advance()
                StringNode((token as Token.String).value)
            }
            TokenType.IDENTIFIER -> {
                IdentifierNode(eatIdentifier())
            }
            TokenType.OPEN_PAREN -> {
                advance()
                val res = eatObject()
                if (!hasNext(TokenType.CLOSE_PAREN)) exception("Unclosed ')'")
                advance()
                res
            }
            else -> exception("Unexpected token '${token.type}'")
        }
    }


    private fun op1(): ObjectNode {
        val obj = op0()
        if (hasNext(TokenType.OPEN_PAREN)) {
            advance()
            val args = ArrayList<ObjectNode>()
            while (!hasNext(TokenType.CLOSE_PAREN)) {
                args.add(eatObject())
                if (hasNext(TokenType.CLOSE_PAREN)) break
                if (!hasNext(TokenType.COMMA)) exception("Expecting ','")
                advance()
            }
            if (!hasNext(TokenType.CLOSE_PAREN)) exception("Unclosed ')'")
            advance()
            return InvokeNode((obj as IdentifierNode).name, args)
        }
        return obj
    }

    // unary + - ! ~
    private fun op2(): ObjectNode = when (peek().type) {
        // recursive (right-to-left): ---a = -(-(-a))
        TokenType.PLUS -> {
            advance()
            UnaryNode(op2(), UnaryNode.Type.PLUS)
        }
        TokenType.MINUS -> {
            advance()
            UnaryNode(op2(), UnaryNode.Type.MINUS)
        }
//        TokenType.LOGICAL_NOT -> {
//            advance()
//            if (op2().toBoolean()) BigDecimal.ZERO else BigDecimal.ONE
//        }
//        TokenType.BITWISE_NOT -> {
//            advance()
//            op2().toLong().inv().toBigDecimal()
//        }
        else -> op1()
    }

    // * / %
    private fun op3(): ObjectNode {
        var res = op2()
        while (hasNext()) {
            when (peek().type) {
                TokenType.ASTERISK -> {
                    advance()
                    res = BinaryNode(res, op2(), BinaryNode.Type.MULTIPLY)
                }
                TokenType.DIVIDE -> {
                    advance()
                    res = BinaryNode(res, op2(), BinaryNode.Type.DIVIDE)
                }
                TokenType.REMAINDER -> {
                    advance()
                    res = BinaryNode(res, op2(), BinaryNode.Type.REMAINDER)
                }
                else -> break
            }
        }
        return res
    }

    // + -
    private fun op4(): ObjectNode {
        var res = op3()
        while (hasNext()) {
            when (peek().type) {
                TokenType.PLUS -> {
                    advance()
                    res = BinaryNode(res, op3(), BinaryNode.Type.PLUS)
                }
                TokenType.MINUS -> {
                    advance()
                    res = BinaryNode(res, op3(), BinaryNode.Type.MINUS)
                }
                else -> break
            }
        }
        return res
    }

//    // << >> >>>
//    private fun op5(): ASTNode {
//        var res = op4()
//        while (true) {
//            when (current?.type) {
//                TokenType.LEFT_SHIFT -> {
//                    advance()
//                    res = (res.toLong() shl op4().toInt()).toBigDecimal()
//                }
//                TokenType.RIGHT_SHIFT -> {
//                    advance()
//                    res = (res.toLong() shr op4().toInt()).toBigDecimal()
//                }
//                TokenType.UNSIGNED_RIGHT_SHIFT -> {
//                    advance()
//                    res = (res.toLong() ushr op4().toInt()).toBigDecimal()
//                }
//                else -> return res
//            }
//        }
//    }
//
//    // > >= < <=
//    private fun op6(): ASTNode {
//        val op = op5()
//        return when (current?.type) {
//            TokenType.LESS -> {
//                advance()
//                (op < op5()).toBigDecimal()
//            }
//            TokenType.LESS_OR_EQUALS -> {
//                advance()
//                (op <= op5()).toBigDecimal()
//            }
//            TokenType.GREATER -> {
//                advance()
//                (op > op5()).toBigDecimal()
//            }
//            TokenType.GREATER_OR_EQUALS -> {
//                advance()
//                (op >= op5()).toBigDecimal()
//            }
//            else -> op
//        }
//    }
//
//    // == !=
//    private fun op7(): ASTNode {
//        val op = op6()
//        return when (current?.type) {
//            TokenType.EQUALS -> {
//                advance()
//                (op == op6()).toBigDecimal()
//            }
//            TokenType.NOT_EQUALS -> {
//                advance()
//                (op != op6()).toBigDecimal()
//            }
//            else -> op
//        }
//    }
//
//    // &
//    private fun op8(): ASTNode {
//        var res = op7()
//        while (hasNext(TokenType.BITWISE_AND)) {
//            advance()
//            res = (res.toLong() and op7().toLong()).toBigDecimal()
//        }
//        return res
//    }
//
//    // ^
//    private fun op9(): ASTNode {
//        var res = op8()
//        while (hasNext(TokenType.BITWISE_XOR)) {
//            advance()
//            res = (res.toLong() xor op8().toLong()).toBigDecimal()
//        }
//        return res
//    }
//
//    // |
//    private fun op10(): ASTNode {
//        var res = op9()
//        while (hasNext(TokenType.BITWISE_OR)) {
//            advance()
//            res = (res.toLong() or op9().toLong()).toBigDecimal()
//        }
//        return res
//    }
//
//    // &&
//    private fun op11(): ASTNode {
//        val op = op10()
//        if (hasNext(TokenType.LOGICAL_AND)) {
//            var res = op.toBoolean()
//            do {
//                advance()
//                val op = op10()
//                res = res && op.toBoolean()
//            } while (hasNext(TokenType.LOGICAL_AND))
//            return res.toBigDecimal()
//        }
//        return op
//    }
//
//    // ||
//    private fun op12(): ASTNode {
//        val op = op11()
//        if (hasNext(TokenType.LOGICAL_OR)) {
//            var res = op.toBoolean()
//            do {
//                advance()
//                val op = op11()
//                res = res || op.toBoolean()
//            } while (hasNext(TokenType.LOGICAL_OR))
//            return res.toBigDecimal()
//        }
//        return op
//    }
//
//    // a ? b : c
//    private fun op13(): ASTNode {
//        val a = op12()
//        if (hasNext(TokenType.QUESTION)) {
//            advance()
//            val b = op12()
//            if (!hasNext(TokenType.COLON)) exception("Expecting ':'")
//            advance()
//            val c = op12()
//            return if (a.toBoolean()) b else c
//        }
//        return a
//    }


    private fun eatObject(): ObjectNode {
        val obj = op4()
        if (hasNext(TokenType.EQUAL)) {
            advance()
            val value = eatObject()
            return AssignmentNode(obj, value)
        }
        return obj
    }

    companion object {
        fun parse(expression: CharSequence): Program {
            return Parser(Lexer(expression)).parse()
        }
    }
}