package com.example.autorun.ui

import android.opengl.GLES20
import android.util.Log

object GLESShader {
    const val VERTEX_SHADER_CODE = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec4 vColor;
        attribute vec3 vNormal;
        varying vec4 _color;
        varying float _diffuse;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            _color = vColor;
            // 簡易的な平行光源 (右上から光を当てる)
            vec3 lightDir = normalize(vec3(0.5, 1.0, 0.5));
            // 法線と光の方向から明るさを計算 (最低 0.3 の明るさを確保)
            _diffuse = max(dot(normalize(vNormal), lightDir), 0.3);
        }
    """

    const val FRAGMENT_SHADER_CODE = """
        precision mediump float;
        varying vec4 _color;
        varying float _diffuse;
        void main() {
            // 色に明るさを掛けて出力
            gl_FragColor = vec4(_color.rgb * _diffuse, _color.a);
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
