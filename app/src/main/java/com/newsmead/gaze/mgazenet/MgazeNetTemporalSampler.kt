package com.newsmead.gaze.mgazenet

/** Selects a fixed number of ordered rows across the whole eligible capture window. */
object MgazeNetTemporalSampler {
    fun indices(candidateCount: Int, selectedCount: Int): IntArray {
        require(selectedCount > 1 && candidateCount >= selectedCount)
        return IntArray(selectedCount) { index ->
            ((index.toLong()*(candidateCount-1))/(selectedCount-1)).toInt()
        }.also { selected ->
            check(selected.first() == 0 && selected.last() == candidateCount-1)
            check(selected.toSet().size == selectedCount)
        }
    }
}
