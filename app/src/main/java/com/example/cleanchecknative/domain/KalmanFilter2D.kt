package com.example.cleanchecknative.domain

class KalmanFilter2D(
    initialX: Float,
    initialY: Float,
    processNoise: Float = 0.03f,
    measurementNoise: Float = 0.5f
) {
    // State vector [x, y, vx, vy]' (4x1)
    private var state: FloatArray = floatArrayOf(initialX, initialY, 0f, 0f)

    // State covariance matrix P (4x4)
    private var covariance: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    // State transition matrix A (4x4)
    private val transitionMatrix = floatArrayOf(
        1f, 0f, 1f, 0f,
        0f, 1f, 0f, 1f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    // Measurement matrix H (2x4)
    private val measurementMatrix = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f
    )

    // Process noise covariance Q (4x4)
    private val processNoiseCov = floatArrayOf(
        processNoise, 0f, 0f, 0f,
        0f, processNoise, 0f, 0f,
        0f, 0f, processNoise, 0f,
        0f, 0f, 0f, processNoise
    )

    // Measurement noise covariance R (2x2)
    private val measurementNoiseCov = floatArrayOf(
        measurementNoise, 0f,
        0f, measurementNoise
    )

    fun predict() {
        // Predict state: x_hat = A * x
        state = multiplyMatrixVector(transitionMatrix, 4, 4, state)

        // Predict covariance: P = A * P * A' + Q
        val transitionMatrixT = transpose(transitionMatrix, 4, 4)
        val tempCov = multiply(transitionMatrix, 4, 4, covariance, 4, 4)
        covariance = add(multiply(tempCov, 4, 4, transitionMatrixT, 4, 4), processNoiseCov, 4, 4)
    }

    fun update(measurement: FloatArray) {
        // Innovation (measurement residual): y = z - H * x_hat
        val innovation = subtract(measurement, multiplyMatrixVector(measurementMatrix, 2, 4, state))

        // Innovation covariance: S = H * P * H' + R
        val measurementMatrixT = transpose(measurementMatrix, 2, 4)
        val tempS = multiply(measurementMatrix, 2, 4, covariance, 4, 4)
        val innovationCovariance = add(multiply(tempS, 2, 4, measurementMatrixT, 4, 2), measurementNoiseCov, 2, 2)

        // Kalman Gain: K = P * H' * S^-1
        val innovationCovarianceInv = inverse2x2(innovationCovariance)
        val tempK = multiply(covariance, 4, 4, measurementMatrixT, 4, 2)
        val kalmanGain = multiply(tempK, 4, 2, innovationCovarianceInv, 2, 2)

        // Update state: x = x_hat + K * y
        state = add(state, multiplyMatrixVector(kalmanGain, 4, 2, innovation))

        // Update covariance: P = (I - K * H) * P
        val identity4x4 = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
        val tempP = multiply(kalmanGain, 4, 2, measurementMatrix, 2, 4)
        val tempI = subtract(identity4x4, tempP, 4, 4)
        covariance = multiply(tempI, 4, 4, covariance, 4, 4)
    }

    fun getPosition(): Pair<Float, Float> = Pair(state[0], state[1])

    // --- Matrix Helper Functions ---
    private fun multiply(a: FloatArray, aRows: Int, aCols: Int, b: FloatArray, bRows: Int, bCols: Int): FloatArray {
        val result = FloatArray(aRows * bCols)
        for (r in 0 until aRows) {
            for (c in 0 until bCols) {
                var sum = 0f
                for (k in 0 until aCols) {
                    sum += a[r * aCols + k] * b[k * bCols + c]
                }
                result[r * bCols + c] = sum
            }
        }
        return result
    }

    private fun multiplyMatrixVector(mat: FloatArray, rows: Int, cols: Int, vec: FloatArray): FloatArray {
        val result = FloatArray(rows)
        for (r in 0 until rows) {
            var sum = 0f
            for (c in 0 until cols) {
                sum += mat[r * cols + c] * vec[c]
            }
            result[r] = sum
        }
        return result
    }

    private fun transpose(mat: FloatArray, rows: Int, cols: Int): FloatArray {
        val result = FloatArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                result[c * rows + r] = mat[r * cols + c]
            }
        }
        return result
    }

    private fun add(a: FloatArray, b: FloatArray, rows: Int, cols: Int): FloatArray = a.zip(b, Float::plus).toFloatArray()
    private fun subtract(a: FloatArray, b: FloatArray, rows: Int, cols: Int): FloatArray = a.zip(b, Float::minus).toFloatArray()
    private fun add(a: FloatArray, b: FloatArray): FloatArray = a.zip(b, Float::plus).toFloatArray()
    private fun subtract(a: FloatArray, b: FloatArray): FloatArray = a.zip(b, Float::minus).toFloatArray()

    private fun inverse2x2(mat: FloatArray): FloatArray {
        val det = mat[0] * mat[3] - mat[1] * mat[2]
        if (det == 0f) return mat // Should not happen
        val invDet = 1.0f / det
        return floatArrayOf(mat[3] * invDet, -mat[1] * invDet, -mat[2] * invDet, mat[0] * invDet)
    }
}
