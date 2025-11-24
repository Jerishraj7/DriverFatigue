package com.example.driverfatiguedetection

import kotlin.math.hypot

object DrowsinessMetrics {
    data class P(val x: Float, val y: Float)
    data class Ratios(val ear: Double, val mar: Double)

    private fun dist(a: P, b: P) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    // 6 points per eye (MediaPipe indices)
    private val L = intArrayOf(33, 160, 158, 133, 153, 144)
    private val R = intArrayOf(362, 385, 387, 263, 373, 380)

    private const val M_LEFT = 78
    private const val M_RIGHT = 308
    private const val M_TOP = 13
    private const val M_BOTTOM = 14

    private fun ear6(pts: List<P>): Double {
        val p1 = pts[0]; val p2 = pts[1]; val p3 = pts[2]
        val p4 = pts[3]; val p5 = pts[4]; val p6 = pts[5]
        val num = dist(p2, p6) + dist(p3, p5)
        val den = 2.0 * dist(p1, p4)
        return if (den > 0) num / den else 0.0
    }

    private fun mar(mL: P, mR: P, mT: P, mB: P): Double {
        val v = dist(mT, mB)
        val h = dist(mL, mR)
        return if (h > 0) v / h else 0.0
    }

    fun compute(all: List<P>): Ratios {
        val left  = listOf(all[L[0]], all[L[1]], all[L[2]], all[L[3]], all[L[4]], all[L[5]])
        val right = listOf(all[R[0]], all[R[1]], all[R[2]], all[R[3]], all[R[4]], all[R[5]])
        val ear = (ear6(left) + ear6(right)) / 2.0
        val mar = mar(all[M_LEFT], all[M_RIGHT], all[M_TOP], all[M_BOTTOM])
        return Ratios(ear, mar)
    }
}
