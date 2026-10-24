package com.example.kotlovan.data

import kotlinx.serialization.Serializable



@Serializable
enum class CommentIconType { CIRCLE, SQUARE, TRIANGLE, DIAMOND, ROSETTE, PHONE, COMPUTER }

@Serializable
data class CommentPin(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val title: String = "",
    val text: String = "",
    val color: Long = 0xFFFF5722L,
    val iconType: CommentIconType = CommentIconType.CIRCLE
	val link: PinLink? = null
)

@Serializable
data class CellData(
    val title: String = "",
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