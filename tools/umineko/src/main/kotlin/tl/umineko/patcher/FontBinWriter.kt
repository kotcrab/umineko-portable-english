package tl.umineko.patcher

import tl.tex.ImageReader
import tl.umineko.util.commonFontPalette
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class FontBinWriter(
  private val imageFile: File,
) {
  fun patchTo(outBinFile: File) {
    val imgReader = ImageReader(ImageIO.read(imageFile))
    val out = ByteArrayOutputStream()

    while (!imgReader.eof()) {
      val pixel1 = imgReader.nextPixel().let { if (it == 0) commonFontPalette.colors.first() else it }
      val pixel2 = imgReader.nextPixel().let { if (it == 0) commonFontPalette.colors.first() else it }
      val color1 = commonFontPalette.getClosestColor(pixel1)
      val color2 = commonFontPalette.getClosestColor(pixel2)
      val encoded = (color2 shl 4) or color1
      out.write(encoded)
    }

    outBinFile.writeBytes(out.toByteArray())
  }
}
