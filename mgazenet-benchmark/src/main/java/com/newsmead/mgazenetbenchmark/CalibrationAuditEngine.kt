package com.newsmead.mgazenetbenchmark

/** Model-independent target-group audit. Features remain in memory and are never encoded in reports. */
interface CalibrationRegressor : AutoCloseable {
    fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>)
    fun predict(features: FloatArray): FloatArray
}

class CalibrationAuditEngine(private val factory: () -> CalibrationRegressor) {
    data class Row(val features: FloatArray, val normalizedLabel: FloatArray,
                   val captureMs: Double, val sourceOutputMs: Double)
    data class Group(val id: String, val rows: List<Row>)
    data class Training(val groups: List<Group>)
    data class Prediction(val captureMs: Double, val sourceOutputMs: Double,
                          val normalizedPoint: FloatArray)
    data class Fold(val heldOutId: String, val normalizedTarget: FloatArray,
                    val predictions: List<Prediction>)
    data class Result(val method: String = METHOD, val folds: List<Fold>)

    fun audit(training: Training): Result {
        validate(training)
        val folds = training.groups.mapIndexed { heldOutIndex, heldOut ->
            val trainRows = training.groups.filterIndexed { index, _ -> index != heldOutIndex }
                .flatMap { it.rows }
            val features = trainRows.map { it.features }.toTypedArray()
            val labels = trainRows.map { it.normalizedLabel }.toTypedArray()
            val regressor = factory()
            try {
                regressor.fit(features, labels)
                Fold(heldOut.id, heldOut.rows.first().normalizedLabel.copyOf(), heldOut.rows.map { row ->
                    val prediction = regressor.predict(row.features)
                    require(prediction.size == 2 && prediction.all(Float::isFinite))
                    Prediction(row.captureMs,row.sourceOutputMs,prediction.copyOf())
                })
            } finally {
                regressor.close()
            }
        }
        return Result(folds=folds)
    }

    companion object {
        const val METHOD = "leave_one_complete_target_group_out"
        const val FEATURE_COUNT = 258
        const val FIT_TARGETS = 16
        const val ROWS_PER_TARGET = 45

        fun validate(training: Training) {
            require(training.groups.size == FIT_TARGETS)
            require(training.groups.map { it.id }.distinct().size == FIT_TARGETS)
            require(training.groups.map { it.id } == (1..FIT_TARGETS).map { "fit_$it" })
            var previousCapture = -1.0
            var previousOutput = -1.0
            training.groups.forEach { group ->
                require(group.rows.size == ROWS_PER_TARGET)
                val target = group.rows.first().normalizedLabel
                require(target.size == 2 && target.all { it.isFinite() && it in 0f..1f })
                group.rows.forEach { row ->
                    require(row.features.size == FEATURE_COUNT && row.features.all(Float::isFinite))
                    require(row.normalizedLabel.contentEquals(target))
                    require(row.captureMs.isFinite() && row.sourceOutputMs.isFinite() &&
                        row.captureMs > previousCapture && row.sourceOutputMs > previousOutput &&
                        row.sourceOutputMs >= row.captureMs)
                    previousCapture = row.captureMs; previousOutput = row.sourceOutputMs
                }
            }
        }

        fun flattened(training: Training): Pair<Array<FloatArray>,Array<FloatArray>> {
            validate(training)
            val rows = training.groups.flatMap { it.rows }
            return rows.map { it.features }.toTypedArray() to rows.map { it.normalizedLabel }.toTypedArray()
        }

        fun clear(training: Training) {
            training.groups.flatMap { it.rows }.forEach { row ->
                row.features.fill(0f); row.normalizedLabel.fill(0f)
            }
        }
    }
}
