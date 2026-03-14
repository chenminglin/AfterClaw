package com.bethena.andclawapp.host

import java.io.File
import java.io.IOException

/**
 * 检查该文件是否为兼容的符号链接。
 *
 * @return 如果是符号链接，则返回 true；否则返回 false。
 */
internal fun File.isSymbolicLinkCompat(): Boolean {
    return try {
        val canonical = canonicalFile
        val absoluteParent = absoluteFile.parentFile ?: return false
        canonical != File(absoluteParent, name).canonicalFile
    } catch (_: IOException) {
        false
    }
}

/**
 * 将字符串值转义为可以安全在 shell 中使用的参数。
 *
 * @param value 需要转义的字符串。
 * @return 转义后的字符串。
 */
internal fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
