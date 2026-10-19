package tl.umineko.patcher

import com.google.common.hash.Hashing
import kio.LERandomAccessFile
import kio.util.WINDOWS_932
import kio.util.child
import kio.util.readJsonOrElse
import kio.util.toUnsignedInt
import kio.util.writeJson
import tl.umineko.file.FontBinFile
import java.io.File

class FontConverter(
  private val srcFontDir: File,
  private val imFontDir: File,
  private val imFontVerifyDir: File,
  private val hashFile: File,
  private val writeVerifyPng: Boolean = false,
) {
  private val hashes: MutableMap<String, ByteArray> = hashFile.readJsonOrElse { mutableMapOf() }

  init {
    imFontDir.mkdir()
    if (writeVerifyPng) {
      imFontVerifyDir.mkdir()
    }
  }

  fun patch(fonts: List<InputFont>) {
    fonts.forEach { font ->
      val srcFile = srcFontDir.child("${font.name}.png")
      val outFile = imFontDir.child("${font.name}.bin")
      val oldHash = hashes.getOrDefault(font.name, ByteArray(0))
      val newHash = Hashing.murmur3_128().hashBytes(srcFile.readBytes()).asBytes()

      if (oldHash.contentEquals(newHash)) {
        println("Font ${font.name} image is up to date")
      } else {
        println("Patching font ${font.name}...")
        FontBinWriter(srcFile)
          .patchTo(outFile)
        hashes[font.name] = newHash
      }

      println("Inserting font ${font.name} tables...")
      with(LERandomAccessFile(outFile)) {
        seek(0x1DBE0)
        // ASCII X advance table (+0)
        val xAdvance = font.xAdvance
          .plus(scriptZeroWidthSpace to 0)
          .mapKeys { it.key.code }
        repeat(128) { code ->
          writeByte(xAdvance.getOrDefault(code, 18))
        }
        // ASCII Y offset table, not implemented (+128)
        repeat(128) {
          writeByte(0)
        }
        // ASCII kerning tables (+256)
        val kerning = font.kerning.mapKeys { Pair(it.key.first.code, it.key.second.code) }
        repeat(128) { firstChar ->
          repeat(128) { secondChar ->
            writeByte(kerning.getOrDefault(Pair(firstChar, secondChar), 0))
          }
        }

        // Extended X advance and kerning tables (+16640)
        val xAdvanceEx = font.xAdvance.filter { it.key.code > 128 }
        val kerningEx = font.kerning.filter { it.key.first.code > 128 || it.key.second.code > 128 }

        writeInt(xAdvanceEx.size)
        writeInt(kerningEx.size)

        xAdvanceEx.forEach { (char, xAdvance) ->
          val bytes = char.toString().toByteArray(Charsets.WINDOWS_932)
          writeByte(bytes[1].toUnsignedInt())
          writeByte(bytes[0].toUnsignedInt())
          writeByte(xAdvance)
          writeByte(0)
        }

        kerningEx.forEach { (pair, amount) ->
          val bytes1 = pair.first.toString().toByteArray(Charsets.WINDOWS_932)
          val bytes2 = pair.second.toString().toByteArray(Charsets.WINDOWS_932)
          if (bytes1.size == 1) {
            writeByte(bytes1[0].toUnsignedInt())
            writeByte(0)
          } else {
            writeByte(bytes1[1].toUnsignedInt())
            writeByte(bytes1[0].toUnsignedInt())
          }
          if (bytes2.size == 1) {
            writeByte(bytes2[0].toUnsignedInt())
            writeByte(0)
          } else {
            writeByte(bytes2[1].toUnsignedInt())
            writeByte(bytes2[0].toUnsignedInt())
          }
          writeByte(amount)
          writeByte(0)
          writeByte(0)
          writeByte(0)
        }

        close()
      }

      if (writeVerifyPng) {
        FontBinFile(outFile)
          .writeToPng(imFontVerifyDir.child("${font.name}.png"))
      }
    }

    hashFile.writeJson(hashes)
  }

  data class InputFont(
    val name: String,
    val xAdvance: Map<Char, Int>,
    val kerning: Map<Pair<Char, Char>, Int> = emptyMap(),
  )
}
