package me.unidok.lang

open class Token(@JvmField val type: TokenType) {
    companion object {
        fun integer(integer: kotlin.Long): Token {
            if (integer >= kotlin.Int.MIN_VALUE && integer <= kotlin.Int.MAX_VALUE) {
                return Int(integer.toInt())
            }
            return Long(integer)
        }

        fun floating(floating: kotlin.Double): Token {
            if (floating >= kotlin.Float.MIN_VALUE && floating <= kotlin.Float.MAX_VALUE) {
                return Float(floating.toFloat())
            }
            return Double(floating)
        }
    }

    class Int(@JvmField val value: kotlin.Int) : Token(TokenType.INT)
    class Long(@JvmField val value: kotlin.Long) : Token(TokenType.LONG)
    class Float(@JvmField val value: kotlin.Float) : Token(TokenType.FLOAT)
    class Double(@JvmField val value: kotlin.Double) : Token(TokenType.DOUBLE)
    class Identifier(@JvmField val value: kotlin.String) : Token(TokenType.IDENTIFIER)
    class String(@JvmField val value: kotlin.String) : Token(TokenType.STRING)
}