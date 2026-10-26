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
    val color: Long = 0xFFFFEB3B,             // жёлтый по умолчанию
    val iconType: CommentIconType = CommentIconType.CIRCLE,
    val link: PinLink? = null,
    val photos: List<CommentPhoto> = emptyList(),
    val rating: Int = 0,
    val shortLabel: String = "",
    val strokeColor: Long = 0xFF1976D2,       // синяя кайма
    val strokeRatio: Float = 0.25f            // толщина = 1/4 диаметра
)

@Serializable
data class CellData(
    val title: String = "",
    /** Имя файла в filesDir/cell_images/ — используется в ZIP-формате. */
    val imageFile: String? = null,
    /** Устаревшее поле — только для чтения старых JSON; при normalize мигрируется в imageFile. */
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
    val rowLabels: Map<Int, String> = emptyMap(),
    val colLabels: Map<Int, String> = emptyMap()
) {
    fun key(r: Int, c: Int) = "$r,$c"
    fun cell(r: Int, c: Int): CellData? = cells[key(r, c)]
}

@Serializable
data class ArrayConfig(
    val name: String = "Массив",
    val main: SubArray = SubArray(),
    val aux: SubArray = SubArray(),
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