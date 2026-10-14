package com.scanr.app.data

import kotlin.math.sqrt

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val UP = Vector3(0f, 1f, 0f)
        val DOWN = Vector3(0f, -1f, 0f)
        val FORWARD = Vector3(0f, 0f, -1f)
        val BACK = Vector3(0f, 0f, 1f)
        val LEFT = Vector3(-1f, 0f, 0f)
        val RIGHT = Vector3(1f, 0f, 0f)
    }
    
    val magnitude: Float
        get() = sqrt(x * x + y * y + z * z)
    
    val sqrMagnitude: Float
        get() = x * x + y * y + z * z
    
    val normalized: Vector3
        get() {
            val mag = magnitude
            return if (mag > 0.00001f) {
                Vector3(x / mag, y / mag, z / mag)
            } else {
                ZERO
            }
        }
    
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)
    
    operator fun unaryMinus() = Vector3(-x, -y, -z)
    
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z
    
    fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    
    fun distanceTo(other: Vector3): Float = (this - other).magnitude
    
    fun lerp(other: Vector3, t: Float): Vector3 {
        val clampedT = t.coerceIn(0f, 1f)
        return Vector3(
            x + (other.x - x) * clampedT,
            y + (other.y - y) * clampedT,
            z + (other.z - z) * clampedT
        )
    }
    
    fun toFloatArray() = floatArrayOf(x, y, z)
    
    override fun toString() = "Vector3($x, $y, $z)"
}

fun FloatArray.toVector3(): Vector3 {
    require(size >= 3) { "FloatArray must have at least 3 elements" }
    return Vector3(this[0], this[1], this[2])
}
