package io.github.takusan23.zerowebmjs.zerowebm.parse

import io.github.takusan23.zerowebmjs.zerowebm.MatroskaTags

/**
 * EBMLの要素を表すデータクラス
 *
 * @param tag [MatroskaTags]
 * @param elementSize 要素の合計サイズ
 * @param data 実際のデータ
 */
data class MatroskaParseElement(
    val tag: MatroskaTags,
    val data: ByteArray,
    val elementSize: Int,
)