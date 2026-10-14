package com.scanr.app.data

data class Vertex(
    val position: Vector3,
    val normal: Vector3 = Vector3.UP,
    val color: Color3? = null,
    val confidence: Float = 1.0f
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            position.x, position.y, position.z,
            normal.x, normal.y, normal.z
        )
    }
    
    fun toFloatArrayWithColor(): FloatArray {
        val c = color ?: Color3.WHITE
        return floatArrayOf(
            position.x, position.y, position.z,
            normal.x, normal.y, normal.z,
            c.r, c.g, c.b
        )
    }
}

data class Color3(
    val r: Float,
    val g: Float,
    val b: Float
) {
    companion object {
        val WHITE = Color3(1f, 1f, 1f)
        val BLACK = Color3(0f, 0f, 0f)
        val RED = Color3(1f, 0f, 0f)
        val GREEN = Color3(0f, 1f, 0f)
        val BLUE = Color3(0f, 0f, 1f)
    }
    
    fun toFloatArray() = floatArrayOf(r, g, b)
}

data class Triangle(
    val v0: Int,
    val v1: Int,
    val v2: Int
) {
    fun toIntArray() = intArrayOf(v0, v1, v2)
}
