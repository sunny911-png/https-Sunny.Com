package com.example.game.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MeshBuffer(data: FloatArray) {
    val vertexCount: Int = data.size / 6
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(data)
            position(0)
        }
}

object MeshBuilder {

    fun createBoxMesh(w: Float = 1f, h: Float = 1f, d: Float = 1f): MeshBuffer {
        val hw = w * 0.5f
        val hh = h * 0.5f
        val hd = d * 0.5f
        val verts = mutableListOf<Float>()

        fun addQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            fun v(x: Float, y: Float, z: Float) {
                verts.add(x); verts.add(y); verts.add(z)
                verts.add(nx); verts.add(ny); verts.add(nz)
            }
            v(x0, y0, z0); v(x1, y1, z1); v(x2, y2, z2)
            v(x0, y0, z0); v(x2, y2, z2); v(x3, y3, z3)
        }

        // Front (+Z), Back (-Z), Left (-X), Right (+X), Top (+Y), Bottom (-Y)
        addQuad(-hw, -hh, hd, hw, -hh, hd, hw, hh, hd, -hw, hh, hd, 0f, 0f, 1f)
        addQuad(hw, -hh, -hd, -hw, -hh, -hd, -hw, hh, -hd, hw, hh, -hd, 0f, 0f, -1f)
        addQuad(-hw, -hh, -hd, -hw, -hh, hd, -hw, hh, hd, -hw, hh, -hd, -1f, 0f, 0f)
        addQuad(hw, -hh, hd, hw, -hh, -hd, hw, hh, -hd, hw, hh, hd, 1f, 0f, 0f)
        addQuad(-hw, hh, hd, hw, hh, hd, hw, hh, -hd, -hw, hh, -hd, 0f, 1f, 0f)
        addQuad(-hw, -hh, -hd, hw, -hh, -hd, hw, -hh, hd, -hw, -hh, hd, 0f, -1f, 0f)

        return MeshBuffer(verts.toFloatArray())
    }

    fun createGroundQuad(w: Float, d: Float): MeshBuffer {
        val hw = w * 0.5f
        val hd = d * 0.5f
        val data = floatArrayOf(
            -hw, 0f, hd, 0f, 1f, 0f,
            hw, 0f, hd, 0f, 1f, 0f,
            hw, 0f, -hd, 0f, 1f, 0f,
            -hw, 0f, hd, 0f, 1f, 0f,
            hw, 0f, -hd, 0f, 1f, 0f,
            -hw, 0f, -hd, 0f, 1f, 0f
        )
        return MeshBuffer(data)
    }

    fun createDiscMesh(segments: Int = 20): MeshBuffer {
        val verts = mutableListOf<Float>()
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            verts.addAll(listOf(0f, 0f, 0f, 0f, 1f, 0f))
            verts.addAll(listOf(cos(a0), 0f, sin(a0), 0f, 1f, 0f))
            verts.addAll(listOf(cos(a1), 0f, sin(a1), 0f, 1f, 0f))
        }
        return MeshBuffer(verts.toFloatArray())
    }

    fun createRingMesh(innerR: Float, outerR: Float, segments: Int = 36): MeshBuffer {
        val verts = mutableListOf<Float>()
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            verts.addAll(listOf(c0 * innerR, 0f, s0 * innerR, 0f, 1f, 0f))
            verts.addAll(listOf(c0 * outerR, 0f, s0 * outerR, 0f, 1f, 0f))
            verts.addAll(listOf(c1 * outerR, 0f, s1 * outerR, 0f, 1f, 0f))
            verts.addAll(listOf(c0 * innerR, 0f, s0 * innerR, 0f, 1f, 0f))
            verts.addAll(listOf(c1 * outerR, 0f, s1 * outerR, 0f, 1f, 0f))
            verts.addAll(listOf(c1 * innerR, 0f, s1 * innerR, 0f, 1f, 0f))
        }
        return MeshBuffer(verts.toFloatArray())
    }

    fun createSphereMesh(rings: Int = 10, sectors: Int = 14): MeshBuffer {
        val verts = mutableListOf<Float>()
        for (r in 0 until rings) {
            val lat0 = (PI * (-0.5 + r.toDouble() / rings)).toFloat()
            val lat1 = (PI * (-0.5 + (r + 1).toDouble() / rings)).toFloat()
            val y0 = sin(lat0); val zr0 = cos(lat0)
            val y1 = sin(lat1); val zr1 = cos(lat1)

            for (s in 0 until sectors) {
                val lon0 = (2.0 * PI * s / sectors).toFloat()
                val lon1 = (2.0 * PI * (s + 1) / sectors).toFloat()
                val x00 = cos(lon0) * zr0; val z00 = sin(lon0) * zr0
                val x10 = cos(lon1) * zr0; val z10 = sin(lon1) * zr0
                val x01 = cos(lon0) * zr1; val z01 = sin(lon0) * zr1
                val x11 = cos(lon1) * zr1; val z11 = sin(lon1) * zr1

                fun v(x: Float, y: Float, z: Float) {
                    verts.add(x); verts.add(y); verts.add(z)
                    verts.add(x); verts.add(y); verts.add(z)
                }
                v(x00, y0, z00); v(x10, y0, z10); v(x11, y1, z11)
                v(x00, y0, z00); v(x11, y1, z11); v(x01, y1, z01)
            }
        }
        return MeshBuffer(verts.toFloatArray())
    }

    fun createPitchLinesMesh(halfL: Float, halfW: Float): MeshBuffer {
        val verts = mutableListOf<Float>()
        val lw = 0.16f
        val y = 0.015f

        fun addLineRect(xMin: Float, xMax: Float, zMin: Float, zMax: Float) {
            verts.addAll(listOf(xMin, y, zMax, 0f, 1f, 0f))
            verts.addAll(listOf(xMax, y, zMax, 0f, 1f, 0f))
            verts.addAll(listOf(xMax, y, zMin, 0f, 1f, 0f))
            verts.addAll(listOf(xMin, y, zMax, 0f, 1f, 0f))
            verts.addAll(listOf(xMax, y, zMin, 0f, 1f, 0f))
            verts.addAll(listOf(xMin, y, zMin, 0f, 1f, 0f))
        }

        // Touchlines & Goal lines
        addLineRect(-halfL, halfL, halfW - lw, halfW + lw)
        addLineRect(-halfL, halfL, -halfW - lw, -halfW + lw)
        addLineRect(-halfL - lw, -halfL + lw, -halfW, halfW)
        addLineRect(halfL - lw, halfL + lw, -halfW, halfW)

        // Halfway line
        addLineRect(-lw, lw, -halfW, halfW)

        // Penalty boxes (16.5m x 29m) & 6-yard boxes (5.5m x 14m)
        val penL = 16.5f; val penW = 14.5f
        val sixL = 5.5f; val sixW = 7.2f
        for (sign in listOf(-1f, 1f)) {
            val gX = sign * halfL
            val innerX = gX - sign * penL
            addLineRect(minOf(gX, innerX), maxOf(gX, innerX), penW - lw, penW + lw)
            addLineRect(minOf(gX, innerX), maxOf(gX, innerX), -penW - lw, -penW + lw)
            addLineRect(innerX - lw, innerX + lw, -penW, penW)

            val sX = gX - sign * sixL
            addLineRect(minOf(gX, sX), maxOf(gX, sX), sixW - lw, sixW + lw)
            addLineRect(minOf(gX, sX), maxOf(gX, sX), -sixW - lw, -sixW + lw)
            addLineRect(sX - lw, sX + lw, -sixW, sixW)

            // Penalty spot
            val pSpot = gX - sign * 11f
            addLineRect(pSpot - 0.25f, pSpot + 0.25f, -0.25f, 0.25f)
        }

        return MeshBuffer(verts.toFloatArray())
    }
}
