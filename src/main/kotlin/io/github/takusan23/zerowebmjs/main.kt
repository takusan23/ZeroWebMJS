package io.github.takusan23.zerowebmjs

import io.github.takusan23.zerowebmjs.zerowebm.MatroskaTags
import io.github.takusan23.zerowebmjs.zerowebm.parse.MatroskaParseElement
import io.github.takusan23.zerowebmjs.zerowebm.parse.ZeroWebMParser.andFF
import io.github.takusan23.zerowebmjs.zerowebm.parse.ZeroWebMParser.parseChildElement
import io.github.takusan23.zerowebmjs.zerowebm.parse.ZeroWebMParser.parseElement
import io.github.takusan23.zerowebmjs.zerowebm.write.MatroskaBuildElement
import kotlinx.browser.document
import kotlinx.dom.createElement
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.Date

fun main() {
    val filePicker = document.getElementById("file_picker") as HTMLInputElement

    filePicker.addEventListener("change", {
        val videoFile = filePicker.files?.get(0) ?: return@addEventListener
        val reader = FileReader()
        reader.addEventListener("load", {
            val bytes = Uint8Array(reader.result as ArrayBuffer)
            val byteArray = bytes.asByteArray()
            // Kotlin製 WebM パーサー
            val matroskaElementList = parseWebM(byteArray)
            // 時間を入れて作り直す
            val reBuildWebM = writeWebM(matroskaElementList)
            execBinaryDownload(reBuildWebM)
        })
        reader.readAsArrayBuffer(videoFile)
    })

}


private fun writeWebM(list: List<MatroskaParseElement>): ByteArray {

    fun writeEbml(): MatroskaBuildElement {
        val ebmlVersion = list.first { it.tag == MatroskaTags.EBMLVersion }.toBuildElement()
        val readVersion = list.first { it.tag == MatroskaTags.EBMLReadVersion }.toBuildElement()
        val maxIdLength = list.first { it.tag == MatroskaTags.EBMLMaxIDLength }.toBuildElement()
        val maxSizeLength = list.first { it.tag == MatroskaTags.EBMLMaxSizeLength }.toBuildElement()
        val docType = list.first { it.tag == MatroskaTags.DocType }.toBuildElement()
        val docTypeVersion = list.first { it.tag == MatroskaTags.DocTypeVersion }.toBuildElement()
        val docTypeReadVersion = list.first { it.tag == MatroskaTags.DocTypeReadVersion }.toBuildElement()
        val children = ebmlVersion.concat() + readVersion.concat() + maxIdLength.concat() + maxSizeLength.concat() + docType.concat() + docTypeVersion.concat() + docTypeReadVersion.concat()
        return MatroskaBuildElement(MatroskaTags.EBML, children)
    }

    fun writeSegment(): MatroskaBuildElement {
        val elementList = arrayListOf<MatroskaBuildElement>()

        val timestampScale = list.first { it.tag == MatroskaTags.TimestampScale }.toBuildElement()
        // 時間！！！まさかのFloat
        val duration = (list.last { it.tag == MatroskaTags.Timestamp }.data.toInt() + list.last { it.tag == MatroskaTags.SimpleBlock }.data.copyOfRange(1, 3).toInt()).let {
            MatroskaBuildElement(MatroskaTags.Duration, it.toFloat().toBits().to4ByteArray())
        }
        val multiplexingAppName = list.first { it.tag == MatroskaTags.MuxingApp }.toBuildElement()
        val writingAppName = list.first { it.tag == MatroskaTags.WritingApp }.toBuildElement()
        val children = timestampScale.concat() + duration.concat() + multiplexingAppName.concat() + writingAppName.concat()

        elementList += MatroskaBuildElement(MatroskaTags.Info, children)

        val videoTrackNumber = list.first { it.tag == MatroskaTags.TrackNumber }.toBuildElement()
        val videoTrackUid = list.first { it.tag == MatroskaTags.TrackUID }.toBuildElement()
        val videoCodecId = list.first { it.tag == MatroskaTags.CodecID }.toBuildElement()
        val videoTrackType = list.first { it.tag == MatroskaTags.TrackType }.toBuildElement()
        val pixelWidth = list.first { it.tag == MatroskaTags.PixelWidth }.toBuildElement()
        val pixelHeight = list.first { it.tag == MatroskaTags.PixelHeight }.toBuildElement()
        val videoTrack = MatroskaBuildElement(MatroskaTags.VideoTrack, pixelWidth.concat() + pixelHeight.concat())
        val videoTrackEntryChildren = videoTrackNumber.concat() + videoTrackUid.concat() + videoCodecId.concat() + videoTrackType.concat() + videoTrack.concat()
        val videoTrackEntry = MatroskaBuildElement(MatroskaTags.Track, videoTrackEntryChildren)

        val audioTrackNumber = list.first { it.tag == MatroskaTags.TrackNumber }.toBuildElement()
        val audioTrackUid = list.first { it.tag == MatroskaTags.TrackUID }.toBuildElement()
        val audioCodecId = list.first { it.tag == MatroskaTags.CodecID }.toBuildElement()
        val audioTrackType = list.first { it.tag == MatroskaTags.TrackType }.toBuildElement()
        val codecPrivate = list.first { it.tag == MatroskaTags.CodecPrivate }.toBuildElement()
        val sampleFrequency = list.first { it.tag == MatroskaTags.SamplingFrequency }.toBuildElement()
        val channels = list.first { it.tag == MatroskaTags.Channels }.toBuildElement()
        val audioTrack = MatroskaBuildElement(MatroskaTags.AudioTrack, channels.concat() + sampleFrequency.concat())
        val audioTrackEntryValue = audioTrackNumber.concat() + audioTrackUid.concat() + audioCodecId.concat() + audioTrackType.concat() + codecPrivate.concat() + audioTrack.concat()
        val audioTrackEntry = MatroskaBuildElement(MatroskaTags.Track, audioTrackEntryValue)

        elementList += MatroskaBuildElement(MatroskaTags.Tracks, videoTrackEntry.concat() + audioTrackEntry.concat())

        val simpleBlockList = arrayListOf<MatroskaBuildElement>()
        var prevTimestamp: MatroskaBuildElement? = null
        val filteredList = list.filter { it.tag == MatroskaTags.Timestamp || it.tag == MatroskaTags.SimpleBlock }
        filteredList.forEachIndexed { index, matroskaParseElement ->
            if (matroskaParseElement.tag == MatroskaTags.Timestamp) {
                prevTimestamp = matroskaParseElement.toBuildElement()
            }
            if (matroskaParseElement.tag == MatroskaTags.SimpleBlock) {
                simpleBlockList += matroskaParseElement.toBuildElement()
                // 次Timeのやつならおわる
                val nextElement = filteredList.getOrNull(index + 1)
                if (nextElement?.tag == MatroskaTags.Timestamp) {
                    elementList += MatroskaBuildElement(MatroskaTags.Cluster, prevTimestamp!!.concat() + simpleBlockList.flatMap { it.concat().toList() })
                    simpleBlockList.clear()
                }
            }
        }

        return MatroskaBuildElement(MatroskaTags.Segment, elementList.flatMap { it.concat().toList() }.toByteArray())
    }

    val ebmlHeader = writeEbml()
    val segment = writeSegment()

    return ebmlHeader.concat() + segment.concat()
}

/** WebMをパースする */
private fun parseWebM(bytes: ByteArray): List<MatroskaParseElement> {
    // EBMLを読み出す
    val elementList = arrayListOf<MatroskaParseElement>()
    // トップレベルのパース位置
    // EBML Segment Cluster など
    var topLevelReadPos = 0
    val ebmlElement = parseElement(bytes, 0)
    topLevelReadPos += ebmlElement.elementSize
    elementList.addAll(parseChildElement(ebmlElement.data))

    // Segmentを読み出す
    val segmentElement = parseElement(bytes, topLevelReadPos)
    topLevelReadPos += segmentElement.elementSize
    elementList.addAll(parseChildElement(segmentElement.data))

    return elementList
}

/** JSのバイト配列をKotlinにする */
private fun Uint8Array.asByteArray() = ByteArray(this.length) { this[it] }

/** Kotlinのバイト配列をJSにする */
private fun ByteArray.asUint8Array() = Uint8Array(this.unsafeCast<ArrayBuffer>())

/** [Int]を[ByteArray]に変換する。4バイト */
private fun Int.to4ByteArray() = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)

/** ByteArray から Int へ変換する。ByteArray 内にある Byte は符号なしに変換される。 */
private fun ByteArray.toInt(): Int {
    // 先頭に 0x00 があれば消す
    val validValuePos = kotlin.math.max(0, this.indexOfFirst { it != 0x00.toByte() })
    var result = 0
    // 逆にする
    // これしないと左側にバイトが移動するようなシフト演算？になってしまう
    // for を 多い順 にすればいいけどこっちの方でいいんじゃない
    drop(validValuePos).reversed().also { bytes ->
        for (i in 0 until bytes.count()) {
            result = result or (bytes.get(i).toInt().andFF() shl (8 * i))
        }
    }
    return result
}

/** [MatroskaParseElement] を [MatroskaBuildElement] にする */
private fun MatroskaParseElement.toBuildElement() = MatroskaBuildElement(this.tag, this.data)

/** バイナリをダウンロードする */
private fun execBinaryDownload(byteArray: ByteArray) {
    // ダウンロード可能リンクを作る
    val blob = Blob(arrayOf(byteArray.asUint8Array()), BlobPropertyBag(type = "video/webm"))
    val blobUrl = URL.createObjectURL(blob)
    // aタグを作って押す
    val aElement = document.createElement("a") {
        setAttribute("href", blobUrl)
        setAttribute("download", "${Date.now()}.webm")
    }
    document.body?.append(aElement)
    (aElement as HTMLAnchorElement).click()
}