package tl.umineko.file

import kio.KioInputStream
import kio.util.toUnsignedInt
import java.io.File

class Jis2UcsFile(
  file: File,
) {
  val chars: List<Int>

  init {
    val chars = mutableListOf<Int>()
    with(KioInputStream(file.readBytes())) {
      while (!eof()) {
        chars.add(readShort().toUnsignedInt())
      }
      close()
    }
    this.chars = chars
  }
}
