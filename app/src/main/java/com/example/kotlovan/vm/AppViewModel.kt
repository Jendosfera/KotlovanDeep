    // ---- в классе AppViewModel ----

    fun setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int, d: Int) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy,
                lastRow = r, lastCol = c, lastDepth = d))
            else -> config.copy(main = config.main.copy(
                globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy,
                lastRow = r, lastCol = c, lastDepth = d))
        }
        markChanged() // Исправлено: теперь корректно обновляет таймеры автосохранения
    }

    fun updateLastCell(r: Int, c: Int, d: Int = 0) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(lastRow = r, lastCol = c, lastDepth = d))
            else -> config.copy(main = config.main.copy(lastRow = r, lastCol = c, lastDepth = d))
        }
        markChanged() // Исправлено
    }

    fun swapCells(r1: Int, c1: Int, d1: Int, r2: Int, c2: Int, d2: Int) {
        // ... (существующая логика перемещения tail1 и tail2) ...
        
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        updateLastCell(r2, c2, d2) // Перемещенная ячейка становится текущей
        markChanged()
    }

    fun moveTail(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
        // ... (существующая логика перемещения chain) ...
        
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        updateLastCell(tr, tc, newBase) // Вкладываемая ячейка становится текущей
        markChanged()
    }