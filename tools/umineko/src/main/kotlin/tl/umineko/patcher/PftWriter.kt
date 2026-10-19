package tl.umineko.patcher

import kio.KioOutputStream
import tl.tex.ColorPalette
import tl.tex.ImageReader
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class PftWriter(
  private val imageFile: File,
) {
  companion object {
    const val MAX_COLORS = 256
  }

  fun writeTo(outFile: File) {
    val image = ImageIO.read(imageFile)
    val paletteBytes = createPaletteBytes(image)
    val palette = ColorPalette(paletteBytes, ColorPalette.Mode.RGBA8888)

    with(KioOutputStream(outFile.outputStream().buffered())) {
      writeShort(image.width.toShort())
      writeShort(image.height.toShort())
      writeShort(8)
      writeShort(32)
      writeInt(16)
      writeInt(1040)
      writeBytes(paletteBytes)
      val reader = ImageReader(image)
      repeat(image.width * image.height) {
        val pixel = reader.nextPixel()
        val colorId = palette.colors.indexOfFirst { it == pixel }
        writeByte(colorId.toByte())
      }
      close()
    }
  }

  fun createPaletteBytes(image: BufferedImage): ByteArray {
    val reader = ImageReader(image)
    val colorsSet = mutableSetOf<Int>()
    while (!reader.eof()) {
      colorsSet.add(reader.nextPixel())
    }
    if (colorsSet.size > MAX_COLORS) {
      error("Too many colors after reading image: ${colorsSet.size}")
    }
    val out = KioOutputStream(ByteArrayOutputStream(), littleEndian = false)
    colorsSet.forEach {
      out.writeInt((it shl 8) or (it ushr 24))
    }
    while (out.pos() < MAX_COLORS * 4) {
      out.writeInt(0)
    }
    return out.getAsByteArrayOutputStream().toByteArray()
  }
}
