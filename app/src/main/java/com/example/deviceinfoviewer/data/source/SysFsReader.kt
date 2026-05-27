package com.example.deviceinfoviewer.data.source

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * sysfs / proc 文件系统读取工具
 */
object SysFsReader {

    fun readLine(path: String): String =
        try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim() ?: "" }
        } catch (_: Exception) { "" }

    fun readLong(path: String): Long =
        readLine(path).toLongOrNull() ?: -1L

    fun readFloat(path: String): Float =
        readLine(path).toFloatOrNull() ?: Float.NaN

    fun fileExists(path: String): Boolean = File(path).exists()

    fun listDir(path: String): List<String> {
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) dir.list()?.toList() ?: emptyList()
        else emptyList()
    }

    fun readProp(key: String): String =
        try {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("get", String::class.java)
                .invoke(null, key)?.toString() ?: ""
        } catch (_: Exception) { "" }

    fun readAll(path: String): String =
        try {
            BufferedReader(FileReader(path)).use { it.readText() }
        } catch (_: Exception) { "" }
}
