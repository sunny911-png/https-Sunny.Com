package com.example.game.render

import android.opengl.GLES20
import java.nio.FloatBuffer

class ShaderProgram {

    val programId: Int
    val aPositionHandle: Int
    val aNormalHandle: Int
    val uMvpMatrixHandle: Int
    val uModelMatrixHandle: Int
    val uColorHandle: Int
    val uLightDirHandle: Int
    val uLightColorHandle: Int
    val uAmbientColorHandle: Int

    init {
        val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertShader)
            GLES20.glAttachShader(prog, fragShader)
            GLES20.glLinkProgram(prog)
        }
        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(programId, "aNormal")
        uMvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(programId, "uColor")
        uLightDirHandle = GLES20.glGetUniformLocation(programId, "uLightDir")
        uLightColorHandle = GLES20.glGetUniformLocation(programId, "uLightColor")
        uAmbientColorHandle = GLES20.glGetUniformLocation(programId, "uAmbientColor")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun drawMesh(
        mesh: MeshBuffer,
        mvpMatrix: FloatArray,
        modelMatrix: FloatArray,
        r: Float,
        g: Float,
        b: Float,
        a: Float = 1f,
        drawMode: Int = GLES20.GL_TRIANGLES
    ) {
        GLES20.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(uColorHandle, r, g, b, a)

        val buf: FloatBuffer = mesh.buffer
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 24, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 24, buf)

        GLES20.glDrawArrays(drawMode, 0, mesh.vertexCount)
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vWorldNormal;
            void main() {
                vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uLightColor;
            uniform vec3 uAmbientColor;
            varying vec3 vWorldNormal;
            void main() {
                float diff = max(dot(normalize(vWorldNormal), normalize(uLightDir)), 0.0);
                vec3 lit = uColor.rgb * (uAmbientColor + uLightColor * diff);
                gl_FragColor = vec4(lit, uColor.a);
            }
        """
    }
}
