package com.example.kotlovan.data

import kotlinx.serialization.Serializable

@Serializable
data class CommentPin(
    val id: String,
    val x: Float,          // 0..1 нормализованные координаты на изображении
    val y: Float,
    val radius: Float,     // 0..1 нормализованный радиус
    val title: String = "",
    val text: String = ""
)

@Serializable
data class CellData(
    val title: String = "",
    val imageFile: String? = null,     // имя файла внутри filesDir/cell_images
    val comments: List<CommentPin> = emptyList(),
    val ownPosition: Boolean = false,  // "Свое положение"
    val commonX: Float = 0.5f,         // "Общая точка"
    val commonY: Float = 0.5f,
    val ownScale: Float = 1f,
    val ownOffsetX: Float = 0f,
    val ownOffsetY: Float = 0f
)

@Serializable
data class ArrayConfig(
    val name: String = "Массив",
    // key = "row,col"
    val cells: Map<String, CellData> = emptyMap(),
    val globalScale: Float = 1f,
    val globalOffsetX: Float = 0f,
    val globalOffsetY: Float = 0f,
    val lastRow: Int = 0,
    val lastCol: Int = 0,
    val iconSize: Float = 1f,      // 0.1f .. 3f
    val iconOpacity: Float = 1f    // 0.1f .. 1f
) {
    fun key(r: Int, c: Int) = "$r,$c"
    fun cell(r: Int, c: Int): CellData? = cells[key(r, c)]
}