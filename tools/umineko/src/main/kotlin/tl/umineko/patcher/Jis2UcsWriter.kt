package tl.umineko.patcher

import kio.KioOutputStream
import java.io.File

class Jis2UcsWriter(
  private val chars: List<Int>,
) {
  fun writeTo(outFile: File) {
    with(KioOutputStream(outFile)) {
      chars.forEach { char ->
        writeShort(char.toShort())
      }
      close()
    }
  }
}
