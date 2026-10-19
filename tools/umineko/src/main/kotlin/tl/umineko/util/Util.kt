package tl.umineko.util

import tl.tex.ColorPalette

fun <T> Iterator<T>.next(n: Int): List<T> {
  return buildList {
    repeat(n) {
      add(next())
    }
  }
}

val commonFontPalette by lazy {
  ColorPalette(
    byteArrayOf(
      0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0x00.toByte(),
      0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0x11.toByte(),
      0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0x22.toByte(),
      0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0x33.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x44.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x55.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x66.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x77.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x88.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0x99.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xaa.toByte(),
      0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xbb.toByte(),
      0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xcc.toByte(),
      0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xdd.toByte(),
      0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xee.toByte(),
      0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()
    ), ColorPalette.Mode.RGBA8888
  )
}
