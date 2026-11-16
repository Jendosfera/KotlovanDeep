package com.example.kotlovan.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.example.kotlovan.vm.AppViewModel

@Composable
internal fun ArrayBuilderGrid(
    vm: AppViewModel,
    nav: NavHostController,
    rFrom: Int, rTo: Int, cFrom: Int, cTo: Int,
    step: Float, coords: Set<Pair<Int, Int>>,
    localCurrent: Triple<Int, Int, Int>?,
    onLocalCurrentChange: (Triple<Int, Int, Int>?) -> Unit,
    longPressMode: Boolean,
    selectedForSwap: Triple<Int, Int, Int>?,
    onSelectedForSwapChange: (Triple<Int, Int, Int>?) -> Unit,
    onLongPressModeChange: (Boolean) -> Unit,
    isSelectMode: Boolean, selR: Int, selC: Int, selD: Int,
    isUnnestMode: Boolean, unR: Int, unC: Int, unD: Int,
    expandedStacks: Set<Pair<Int, Int>>,
    onExpandedStacksChange: (Set<Pair<Int, Int>>) -> Unit
) {
    val ctx = LocalContext.current

    for (r in rFrom..rTo) for (c in cFrom..cTo) {
        val stack = vm.cellsAt(r, c)
        val xPx = (c - cFrom) * step
        val yPx = (r - rFrom) * step
        val hasAny = stack.isNotEmpty()
        val isPlus = !hasAny && hasNeighbor(r, c, coords)
        val isEmptyStart = !hasAny && coords.isEmpty() && r == 0 && c == 0
        val isExpanded = (r to c) in expandedStacks
        val hiddenCount = if (isExpanded) 0 else (stack.size - 3).coerceAtLeast(0)

        Box(
            Modifier
                .graphicsLayer { translationX = xPx; translationY = yPx }
                .size(CELL_SIZE.dp)
        ) {
            if (hasAny) {
                val sorted = stack.sortedBy { it.first }
                val hasNested = stack.size > 1

                for ((d, cell) in sorted) {
                    if (!isExpanded && d > 2) continue
                    val isSel = localCurrent?.let {
                        it.first == r && it.second == c && it.third == d
                    } == true
                    val isSwap = longPressMode && selectedForSwap != null &&
                            !(selectedForSwap.first == r &&
                                    selectedForSwap.second == c &&
                                    selectedForSwap.third == d)
                    val isSource = isSelectMode && r == selR && c == selC && d == selD

                    val cellSizeDp: Float = when {
                        isExpanded -> CELL_SIZE
                        hasNested -> CELL_SIZE - 2f * 30f
                        else -> CELL_SIZE
                    }
                    val dOffsetXDp: Float = when {
                        isExpanded -> d * 40f
                        hasNested -> d * 30f
                        else -> 0f
                    }
                    val dOffsetYDp: Float = when {
                        isExpanded -> 0f
                        hasNested -> (2 - d) * 30f
                        else -> 0f
                    }

                    CreatedCell(
                        data = cell, vm = vm,
                        isSelected = isSel,
                        isSwapTarget = isSwap,
                        isSelectMode = isSelectMode,
                        isSource = isSource,
                        depth = d,
                        onClick = {
                            if (isUnnestMode) {
                                vm.moveTail(unR, unC, unD, r, c)
                                nav.popBackStack()
                            } else if (isSelectMode) {
                                if (!(r == selR && c == selC && d == selD)) {
                                    vm.makeNested(selR, selC, selD, r, c)
                                    nav.popBackStack()
                                }
                            } else if (longPressMode) {
                                val s = selectedForSwap
                                if (s == null) {
                                    onSelectedForSwapChange(Triple(r, c, d))
                                } else {
                                    if (s.first == r && s.second == c && s.third == d) {
                                        onSelectedForSwapChange(null)
                                    } else {
                                        vm.moveTail(s.first, s.second, s.third, r, c)
                                        onSelectedForSwapChange(null)
                                        onLongPressModeChange(false)
                                    }
                                }
                            } else {
                                // Правило 4а: ячейка становится текущей при нажатии
                                onLocalCurrentChange(Triple(r, c, d))
                                vm.updateLastCell(r, c, d) 
                            }
                        },
                        onDoubleTap = {
                            if (!isSelectMode && !isUnnestMode && !longPressMode) {
                                vm.updateLastCell(r, c, d)
                                nav.navigate("card/$r/$c?depth=$d")
                            }
                        },
                        onLongPress = {
                            if (!isSelectMode && !isUnnestMode) {
                                onLongPressModeChange(true)
                                onSelectedForSwapChange(Triple(r, c, d))
                                Toast.makeText(ctx, "Выберите ячейку для переноса", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .offset(x = dOffsetXDp.dp, y = dOffsetYDp.dp)
                            .size(cellSizeDp.dp)
                            .zIndex(100f - d.toFloat())
                    )
                }

                if (hiddenCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Yellow)
                            .border(5.dp, Color.Black, CircleShape)
                            .zIndex(200f)
                            .clickable {
                                onExpandedStacksChange(expandedStacks + (r to c))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+${hiddenCount}",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else if (isPlus || isEmptyStart) {
                PlusButton(
                    onClick = {
                        if (isUnnestMode) {
                            vm.moveTail(unR, unC, unD, r, c)
                            nav.popBackStack()
                        } else if (isSelectMode) { /* нельзя */ }
                        else if (longPressMode && selectedForSwap != null) {
                            val s = selectedForSwap
                            vm.moveTail(s.first, s.second, s.third, r, c)
                            onSelectedForSwapChange(null)
                            onLongPressModeChange(false)
                        } else if (!longPressMode) {
                            vm.updateLastCell(r, c, 0)
                            nav.navigate("card/$r/$c?depth=0")
                        }
                    }
                )
            }
        }
    }
}