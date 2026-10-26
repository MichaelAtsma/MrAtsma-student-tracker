package com.example.mratsmastudenttracker.ui.utils

import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxParser {

    private fun columnLetterToIndex(colStr: String): Int {
        var result = 0
        for (char in colStr.uppercase()) {
            if (char in 'A'..'Z') {
                result = result * 26 + (char - 'A' + 1)
            }
        }
        return result - 1
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colLetters = ref.takeWhile { it.isLetter() }
        val rowDigits = ref.dropWhile { it.isLetter() }
        val col = if (colLetters.isNotEmpty()) columnLetterToIndex(colLetters) else 0
        val row = (rowDigits.toIntOrNull() ?: 1) - 1
        return Pair(row, col)
    }

    fun parseXlsx(inputStream: InputStream, maxRows: Int? = null): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val bytes = zip.readBytes()
                        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.inputStream())
                        val nodes = doc.getElementsByTagName("si")
                        for (i in 0 until nodes.length) {
                            val item = nodes.item(i)
                            sharedStrings.add(item.textContent ?: "")
                        }
                    }
                    entry.name == "xl/worksheets/sheet1.xml" -> {
                        sheetBytes = zip.readBytes()
                    }
                    sheetBytes == null && entry.name.startsWith("xl/worksheets/sheet") -> {
                        sheetBytes = zip.readBytes()
                    }
                }
                entry = zip.nextEntry
            }
        }

        val sheetStream = sheetBytes ?: return emptyList()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sheetStream.inputStream())
        val rowNodes = doc.getElementsByTagName("row")
        val resultRows = mutableListOf<List<String>>()

        val limit = if (maxRows != null) minOf(rowNodes.length, maxRows) else rowNodes.length

        for (i in 0 until limit) {
            val rowElem = rowNodes.item(i) as? Element ?: continue
            val cellNodes = rowElem.getElementsByTagName("c")
            val rowCells = mutableMapOf<Int, String>()

            for (j in 0 until cellNodes.length) {
                val cellElem = cellNodes.item(j) as? Element ?: continue
                val ref = cellElem.getAttribute("r")
                val type = cellElem.getAttribute("t")
                val (_, col) = parseCellRef(ref)

                val vNodes = cellElem.getElementsByTagName("v")
                var rawVal = if (vNodes.length > 0) vNodes.item(0).textContent ?: "" else ""

                if (rawVal.isBlank() && (type == "inlineStr" || type == "str")) {
                    val tNodes = cellElem.getElementsByTagName("t")
                    if (tNodes.length > 0) rawVal = tNodes.item(0).textContent ?: ""
                }

                val cellVal = when (type) {
                    "s" -> {
                        val sIdx = rawVal.toIntOrNull() ?: -1
                        if (sIdx in sharedStrings.indices) sharedStrings[sIdx] else rawVal
                    }
                    "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                    else -> {
                        if (rawVal.endsWith(".0")) rawVal.removeSuffix(".0") else rawVal
                    }
                }
                rowCells[col] = cellVal.trim()
            }

            val maxCol = if (rowCells.isNotEmpty()) rowCells.keys.maxOrNull()!! else -1
            if (maxCol >= 0) {
                val rowList = (0..maxCol).map { rowCells[it] ?: "" }
                resultRows.add(rowList)
            } else {
                resultRows.add(emptyList())
            }
        }

        return resultRows
    }
}
