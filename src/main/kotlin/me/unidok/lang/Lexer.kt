package me.unidok.lang

class LexerException(message: String) : Exception(message)

class Lexer(private val input: CharSequence) {
    private val length = input.length
    private var currentChar = -1
    private var cursor = -1
    var column = 0
        private set
    var line = 1
        private set

    init {
        advance()
    }

    private fun exception(reason: String): Nothing {
        throw LexerException("$reason at $column, line $line")
    }

    fun eat(): Token? {
        skipWhitespace()
        return when {
            currentChar == -1 -> null
            currentChar == '0'.code -> {
                advance()
                when (currentChar) {
                    'x'.code -> readHexNumber()
                    'o'.code -> readOctNumber()
                    'b'.code -> readBinNumber()
                    else -> {
                        back()
                        readNumber()
                    }
                }
            }
            Character.isDigit(currentChar) -> readNumber()
            Character.isJavaIdentifierStart(currentChar) -> readIdentifier()
            currentChar == '"'.code -> readString()
            else -> readOperator()
        }
    }

    private fun readString(): Token {
        val content = StringBuilder()
        var escaped = false

        advance()

        while (currentChar != -1 && currentChar != '\n'.code) {
            if (escaped) {
                if (currentChar == 'u'.code) {
                    content.append('u')
                    advance()
                    var l = 0
                    var code = 0
                    while (++l <= 4 && isHexDigit(currentChar)) {
                        code = code * 16 + Character.digit(currentChar, 16)
                        advance()
                    }
                    if (l != 4) {
                        exception("Illegal Unicode escape sequence")
                    }
                    content.append(code.toChar())
                    escaped = false
                    continue
                }
                val escapedChar = when (currentChar) {
                    'n'.code -> '\n'
                    't'.code -> '\t'
                    '\\'.code -> '\\'
                    '"'.code -> '"'
                    '$'.code -> '$'
                    else -> exception("Illegal escape character")
                }
                content.append(escapedChar)
                escaped = false
                advance()
            } else if (currentChar == '\\'.code) {
                escaped = true
                advance()
            } else if (currentChar == '"'.code) {
                advance()
                return Token.String(content.toString())
            } else {
                content.append(currentChar.toChar())
                advance()
            }
        }

        exception("Unclosed string literal")
    }

    private fun readOperator(): Token {
        val type = when (currentChar) {
            '+'.code -> TokenType.PLUS
            '-'.code -> TokenType.MINUS
            '*'.code -> TokenType.ASTERISK
            '/'.code -> TokenType.DIVIDE
            '%'.code -> TokenType.REMAINDER
            '^'.code -> TokenType.BITWISE_XOR
            '('.code -> TokenType.OPEN_PAREN
            ')'.code -> TokenType.CLOSE_PAREN
            '{'.code -> TokenType.OPEN_CURLY
            '}'.code -> TokenType.CLOSE_CURLY
            ','.code -> TokenType.COMMA
            '<'.code -> {
                advance()
                when (currentChar) {
                    '<'.code -> TokenType.LEFT_SHIFT
                    '='.code -> TokenType.LESS_OR_EQUALS
                    else -> {
                        back()
                        TokenType.LESS
                    }
                }
            }
            '>'.code -> {
                advance()
                when (currentChar) {
                    '>'.code -> {
                        advance()
                        if (currentChar == '>'.code) {
                            TokenType.UNSIGNED_RIGHT_SHIFT
                        } else {
                            back()
                            TokenType.RIGHT_SHIFT
                        }
                    }
                    '='.code -> TokenType.GREATER_OR_EQUALS
                    else -> {
                        back()
                        TokenType.GREATER
                    }
                }
            }
            '='.code -> {
                advance()
                if (currentChar == '='.code) {
                    TokenType.DOUBLE_EQUAL
                } else {
                    back()
                    TokenType.EQUAL
                }
            }
            '!'.code -> {
                advance()
                if (currentChar == '='.code) {
                    TokenType.NOT_EQUALS
                } else {
                    back()
                    TokenType.LOGICAL_NOT
                }
            }
            '&'.code -> {
                advance()
                if (currentChar == '&'.code) {
                    TokenType.AMPERSAND
                } else {
                    back()
                    TokenType.BITWISE_AND
                }
            }
            '|'.code -> {
                advance()
                if (currentChar == '|'.code) {
                    TokenType.LOGICAL_OR
                } else {
                    back()
                    TokenType.BITWISE_OR
                }
            }
            '~'.code -> TokenType.BITWISE_NOT
            '?'.code -> TokenType.QUESTION
            ':'.code -> TokenType.COLON
            ';'.code -> TokenType.SEMICOLON
            else -> exception("Unexpected character '${currentChar.toChar()}'")
        }
        advance()
        return Token(type)
    }

    private fun readNumber(): Token {
        val startCursor = cursor
        var isFloat = false

        while (Character.isDigit(currentChar)) {
            advance()
        }

        if (currentChar == '.'.code) {
            isFloat = true
            advance()
            while (Character.isDigit(currentChar)) {
                advance()
            }
        }

        if (currentChar == 'e'.code || currentChar == 'E'.code) {
            isFloat = true
            advance()
            if (currentChar == '+'.code || currentChar == '-'.code) {
                advance()
            }

            if (!Character.isDigit(currentChar)) {
                exception("Invalid exponent literal")
            }

            var exp = 0
            do {
                exp = exp * 10 + Character.digit(currentChar, 10)
                if (exp > 300) exception("Exponent literal overflow")

                advance()
            } while (Character.isDigit(currentChar))
        }

        if (currentChar == 'f'.code || currentChar == 'F'.code) {
            val token = Token.Float(input.substring(startCursor, cursor).toFloat())
            advance()
            return token
        }

        if (currentChar == 'l'.code || currentChar == 'L'.code) {
            val token = Token.Long(input.substring(startCursor, cursor).toLong())
            advance()
            return token
        }

        return if (isFloat) {
            Token.floating(input.substring(startCursor, cursor).toDouble())
        } else {
            Token.integer(input.substring(startCursor, cursor).toLong())
        }
    }

    private fun isHexDigit(ch: Int): Boolean {
        return Character.isDigit(ch) || ch >= 'a'.code && ch <= 'f'.code || ch >= 'A'.code && ch <= 'F'.code
    }

    private fun readHexNumber(): Token {
        advance() // consume 'x'
        var value = 0L
        while (isHexDigit(currentChar)) {
            value = value * 16 + Character.digit(currentChar, 16)
            advance()
        }
        if (currentChar == 'l'.code || currentChar == 'L'.code) {
            advance()
            return Token.Long(value)
        }
        return Token.integer(value)
    }

    private fun readOctNumber(): Token {
        advance() // consume 'o'
        var value = 0L
        while (currentChar >= '0'.code && currentChar <= '8'.code) {
            value = value * 8 + Character.digit(currentChar, 8)
            advance()
        }
        if (currentChar == 'l'.code || currentChar == 'L'.code) {
            advance()
            return Token.Long(value)
        }
        return Token.integer(value)
    }

    private fun readBinNumber(): Token {
        advance() // consume 'b'
        var value = 0L
        while (currentChar == '0'.code || currentChar == '1'.code) {
            value = value * 2 + Character.digit(currentChar, 2)
            advance()
        }
        if (currentChar == 'l'.code || currentChar == 'L'.code) {
            advance()
            return Token.Long(value)
        }
        return Token.integer(value)
    }

    private fun readIdentifier(): Token {
        val startCursor = cursor
        do {
            advance()
        } while (Character.isJavaIdentifierPart(currentChar))
        val string = input.substring(startCursor, cursor)
        return when (string) {
            "if" -> Token(TokenType.IF)
            "else" -> Token(TokenType.ELSE)
            "while" -> Token(TokenType.WHILE)
            "continue" -> Token(TokenType.CONTINUE)
            "break" -> Token(TokenType.BREAK)
            "return" -> Token(TokenType.RETURN)
            "asm" -> Token(TokenType.ASM)
            "const" -> Token(TokenType.CONST)
            else -> Token.Identifier(string)
        }
    }

    private fun skipWhitespace() {
        while (Character.isWhitespace(currentChar)) advance()
    }

    private fun advance() {
        currentChar = if (++cursor < length) {
            input[cursor].code
        } else {
            -1
        }
        if (currentChar == '\n'.code) {
            line++
            column = 0
        }
        column++
    }

    private fun back() {
        if (currentChar == '\n'.code) {
            line--
        }
        currentChar = if (--cursor >= 0) {
            input[cursor].code
        } else {
            -1
        }
    }
}