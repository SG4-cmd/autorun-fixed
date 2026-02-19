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
            vec3 lightDir = normalize(vec3(0.5, 1.0, 0.5));
            _diffuse = max(dot(normalize(vNormal), lightDir), 0.4);
        }
    """

    const val FRAGMENT_SHADER_CODE = """
        precision mediump float;
        varying vec4 _color;
        varying float _diffuse;
        void main() {
            // アルファ値が低い（影用平面やガラスなど）パーツを非表示にする
            if (_color.a < 0.5) discard;
            gl_FragColor = vec4(_color.rgb * _diffuse, _color.a);
        }
    """

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }
}
