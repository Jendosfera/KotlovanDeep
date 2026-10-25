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
data class CommentPin(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val title: String = "",
    val text: String = "",
    val color: Long = 0xFFFF5722L,
    val iconType: CommentIconType = CommentIconType.CIRCLE,
    val link: PinLink? = null
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