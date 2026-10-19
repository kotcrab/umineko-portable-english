package tl.umineko.file

import kio.KioInputStream
import kio.util.toUnsignedInt
import tl.tex.ColorPalette
import tl.tex.ImageWriter
import java.io.File

class PftFile(file: File) {
  val image: ImageWriter

  init {
    with(KioInputStream(file.readBytes())) {
      val width = readShort()
      val height = readShort()
      readShort().also { if (it.toInt() != 8) error("Expected 8 in header") }
      readShort().also { if (it.toInt() != 32) error("Expected 32 in header") }
      readInt().also { if (it != 16) error("Expected 16 in header") }
      readInt().also { if (it != 1040) error("Expected 1040 in header") }

      val palette = ColorPalette(readBytes(4 * 256), ColorPalette.Mode.RGBA8888)

      image = ImageWriter(width.toUnsignedInt(), height.toUnsignedInt())
      repeat(width * height) {
        image.writePixel(palette[readByte().toUnsignedInt()])
      }

      close()
    }
  }

  fun writeToPng(file: File) {
    image.writeToPng(file)
  }
}
