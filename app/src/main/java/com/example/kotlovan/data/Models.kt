package com.example.kotlovan.data

import kotlinx.serialization.Serializable

@Serializable
enum class CommentIconType { CIRCLE, SQUARE, TRIANGLE, DIAMOND, ROSETTE, PHONE, COMPUTER }

@Serializable
data class PinLink(
    val isAux: Boolean,
    val row: Int,
    val col: Int,
    val x: Float,
    val y: Float,
    val scale: Float
)

@Serializable
data class CommentPhoto(
    val id: String,
    val file: String,
    val title: String = "",
    val comment: String = ""
)

@Serializable
data class CommentPin(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val title: String = "",
    val text: String = "",
    val color: Long = 0xFFFFEB3B,
    val iconType: CommentIconType = CommentIconType.CIRCLE,
    val link: PinLink? = null,
    val photos: List<CommentPhoto> = emptyList(),
    val rating: Int = 0,
    val shortLabel: String = "",
    val strokeColor: Long = 0xFF1976D2,
    val strokeRatio: Float = 0.25f,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CellData(
    val title: String = "",
    val imageFile: String? = null,
    val imageUri: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val comments: List<CommentPin> = emptyList(),
    val ownPosition: Boolean = false,
    val commonX: Float = 0.5f,
    val commonY: Float = 0.5f,
    val ownScale: Float = 1f,
    val ownOffsetX: Float = 0f,
    val ownOffsetY: Float = 0f
)

@Serializable
data class SubArray(
    val cells: Map<String, CellData> = emptyMap(),
    val globalScale: Float = 1f,
    val globalOffsetX: Float = 0f,
    val globalOffsetY: Float = 0f,
    val lastRow: Int = 0,
    val lastCol: Int = 0,
    val lastDepth: Int = 0,
    val rowLabels: Map<Int, String> = emptyMap(),
    val colLabels: Map<Int, String> = emptyMap()
) {
    fun key(r: Int, c: Int, d: Int = 0) = "$r,$c,$d"
    fun cell(r: Int, c: Int, d: Int = 0): CellData? = cells[key(r, c, d)]

    fun depthAt(r: Int, c: Int): Int {
        var maxD = -1
        for (k in cells.keys) {
            val p = k.split(",")
            if (p.size == 3 && p[0].toIntOrNull() == r && p[1].toIntOrNull() == c) {
                val d = p[2].toIntOrNull() ?: 0
                if (d > maxD) maxD = d
            }
        }
        return maxD
    }

    fun hasCell(r: Int, c: Int): Boolean = depthAt(r, c) >= 0

    fun cellsAt(r: Int, c: Int): List<Pair<Int, CellData>> {
        val result = mutableListOf<Pair<Int, CellData>>()
        for ((k, v) in cells) {
            val p = k.split(",")
            if (p.size == 3 && p[0].toIntOrNull() == r && p[1].toIntOrNull() == c) {
                result += (p[2].toIntOrNull() ?: 0) to v
            }
        }
        return result.sortedBy { it.first }
    }

    fun allCoords(): Set<Pair<Int, Int>> {
        val s = mutableSetOf<Pair<Int, Int>>()
        for (k in cells.keys) {
            val p = k.split(",")
            if (p.size == 3) {
                val r = p[0].toIntOrNull() ?: continue
                val c = p[1].toIntOrNull() ?: continue
                s += r to c
            }
        }
        return s
    }
}

@Serializable
data class ArrayConfig(
    val name: String = "Массив",
    val main: SubArray = SubArray(),
    val aux: SubArray = SubArray(),
    val photoMain: SubArray = SubArray(),   // ← заменяет photoArchive
    val photoAux: SubArray = SubArray(),    // ← новое
    val iconSize: Float = 1f,
    val iconOpacity: Float = 1f
)

@Serializable
data class LinkReturnState(
    val isAux: Boolean,
    val row: Int,
    val col: Int,
    val scale: Float,
    val offX: Float,
    val offY: Float,
    val commentRow: Int,
    val commentCol: Int,
    val commentId: String
)

// ==== Theme ====

@Serializable
enum class BgType { SOLID, GRADIENT, PATTERN }

@Serializable
enum class PatternPreset { NONE, GRID, DOTS, PAPER, BLUEPRINT, CARBON }

@Serializable
data class BackgroundSpec(
    val type: BgType = BgType.SOLID,
    val color: Long = 0xFFECEFF1,
    val gradientStart: Long = 0xFFECEFF1,
    val gradientEnd: Long = 0xFF90A4AE,
    val patternPreset: PatternPreset = PatternPreset.NONE,
    val patternIntensity: Float = 0.3f
)

@Serializable
enum class ButtonShape { ROUNDED, PILL, SQUARE, DIAMOND, HEXAGON }

@Serializable
data class ButtonStyle(
    val shape: ButtonShape = ButtonShape.ROUNDED,
    val cornerRadius: Float = 12f,
    val elevation: Float = 6f,
    val pressedElevation: Float = 2f,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0xFF000000
)

@Serializable
data class ThemeColors(
    val primary: Long = 0xFF1565C0,
    val onPrimary: Long = 0xFFFFFFFF,
    val surface: Long = 0xFFFFFFFF,
    val onSurface: Long = 0xFF000000,
    val accent: Long = 0xFF4CAF50
)

@Serializable
data class ThemeProfile(
    val name: String = "Стандарт",
    val colors: ThemeColors = ThemeColors(),
    val background: BackgroundSpec = BackgroundSpec(),
    val buttonStyle: ButtonStyle = ButtonStyle()
)