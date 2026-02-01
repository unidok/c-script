package me.unidok.lang

object Opcodes {
    const val PUSH = 1L
    const val POP = 2L
    const val LDC = 3L

    const val STORE_0 = 10L
    const val STORE_1 = 11L
    const val STORE_2 = 12L
    const val STORE_3 = 13L
    const val STORE_4 = 14L
    const val STORE = 15L
    const val LOAD_0 = 20L
    const val LOAD_1 = 21L
    const val LOAD_2 = 22L
    const val LOAD_3 = 23L
    const val LOAD_4 = 24L
    const val LOAD = 25L

    const val L2D = 30L
    const val D2L = 31L

    const val LADD = 50L
    const val LSUB = 51L
    const val LMUL = 52L
    const val LDIV = 53L
    const val LREM = 54L
    const val LNEG = 55L


    const val DADD = 60L
    const val DSUB = 61L
    const val DMUL = 62L
    const val DDIV = 63L
    const val DREM = 64L

    const val GOTO = 100L
    const val INVOKE = 101L
    const val RETURN = 102L

    const val GSTORE = 120L
    const val GLOAD = 121L

    const val SPRINT = 200L
    const val LPRINT = 201L
}