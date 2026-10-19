package tl.umineko.file

import kio.KioInputStream
import kio.util.toUnsignedInt
import tl.tex.ImageWriter
import tl.umineko.util.commonFontPalette
import java.io.File

class FontBinFile(
  file: File,
  width: Int = 1692,
  height: Int = 1656,
) {
  val image: ImageWriter

  init {
    with(KioInputStream(file.readBytes())) {
      image = ImageWriter(width, height)
      repeat(width * height / 2) {
        val data = readByte().toUnsignedInt()
        val pixel1 = data and 0b11110000 shr 4
        val pixel2 = data and 0b00001111
        image.writePixel(commonFontPalette[pixel2])
        image.writePixel(commonFontPalette[pixel1])
      }
      close()
    }
  }

  fun writeToPng(file: File) {
    image.writeToPng(file)
  }
}
