package com.example.autorun.data.road

/**
 * 【RoadObjectData】
 * ガードレールやカーブミラー等の付帯オブジェクトの計算済み座標を保持するデータバッファ。
 * 描画パフォーマンス向上のため、プリミティブ配列でえ保持する。
 */
object RoadObjectData {
    private const val MAX_SAMPLES = 4000

    // ガードレールの高さ計算結果
    val leftGuardrailTopY = FloatArray(MAX_SAMPLES + 1)
    val leftGuardrailPlateBottomY = FloatArray(MAX_SAMPLES + 1)

    // 右ミラー用座標バッファ
    val rightMirrorUpperX = FloatArray(MAX_SAMPLES + 1)
    val rightMirrorUpperY = FloatArray(MAX_SAMPLES + 1)
    val rightMirrorLowerX = FloatArray(MAX_SAMPLES + 1)
    val rightMirrorLowerY = FloatArray(MAX_SAMPLES + 1)

    // 左ミラー用座標バッファ
    val leftMirrorUpperX = FloatArray(MAX_SAMPLES + 1)
    val leftMirrorUpperY = FloatArray(MAX_SAMPLES + 1)
    val leftMirrorLowerX = FloatArray(MAX_SAMPLES + 1)
    val leftMirrorLowerY = FloatArray(MAX_SAMPLES + 1)

    /**
     * バッファのクリア。毎フレーム描画前に呼び出すこと。
     */
    fun clear() {
        leftGuardrailTopY.fill(0f)
        leftGuardrailPlateBottomY.fill(0f)
        
        rightMirrorUpperX.fill(0f)
        rightMirrorUpperY.fill(0f)
        rightMirrorLowerX.fill(0f)
        rightMirrorLowerY.fill(0f)

        leftMirrorUpperX.fill(0f)
        leftMirrorUpperY.fill(0f)
        leftMirrorLowerX.fill(0f)
        leftMirrorLowerY.fill(0f)
    }
}
