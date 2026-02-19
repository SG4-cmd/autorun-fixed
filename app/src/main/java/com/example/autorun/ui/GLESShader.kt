package com.example.autorun.ui

import android.opengl.GLES20
import android.util.Log

object GLESShader {
    const val VERTEX_SHADER_CODE = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec4 vColor;
        varying vec4 _color;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            _color = vColor;
        }
    """

    const val FRAGMENT_SHADER_CODE = """
        precision mediump float;
        varying vec4 _color;
        void main() {
            gl_FragColor = _color;
        }
    """

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e("GLESShader", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("GLESShader", "Error linking program: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }
}
