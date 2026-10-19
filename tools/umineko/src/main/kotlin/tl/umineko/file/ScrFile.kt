package tl.umineko.file

import kio.KioInputStream
import kio.util.WINDOWS_932
import kio.util.child
import kio.util.toUnsignedInt
import kio.util.toWHex
import java.io.File

class ScrFile(file: File) {
  private val sections: List<ByteArray>

  init {
    val sections = mutableListOf<ByteArray>()

    with(KioInputStream(file.readBytes())) {
      if (readStringAndTrim(4) != "scr") {
        error("Not a SCR file")
      }
      val count = readInt()

      val pointers = mutableListOf<Int>()
      repeat(count) {
        pointers.add(readInt())
      }

      pointers.plus(file.length().toInt())
        .windowed(2)
        .forEach { (start, end) ->
          val sectionSize = end - start
          setPos(start)
          sections.add(readBytes(sectionSize))
        }
      close()
    }

    this.sections = sections
  }

  fun parseSections(indexes: Set<Int> = emptySet()): Map<Int, List<Command>> {
    return sections.mapIndexed nextSection@{ index, section ->
      if (indexes.isNotEmpty() && index !in indexes) {
        return@nextSection index to emptyList<Command>()
      }
      val commands = mutableListOf<Command>()
      with(KioInputStream(section)) {
        while (!eof()) {
          val pos = pos()
          val op = readShort().toUnsignedInt()
          val (name, data) = when (op) {
            //0x0x
            0x00 -> null to CommandData.None
            0x01 -> null to readOtherData(2)
            0x02, 0x04 -> "addText" to readTextData()
            0x03, 0x05 -> "addText2" to readTextData()
            0x06 -> null to readOtherData(1)
            0x07 -> "addText3" to readTextData()
            0x08 -> null to CommandData.None
            0x09 -> null to CommandData.None
            0x0a, 0x70 -> null to readOtherData(2)
            0x0b -> null to readOtherData(1)
            0x0c -> null to readOtherData(3)
            0x0d -> null to readOtherData(1)
            0x0F, 0x10, 0x11, 0x12, 0x13 -> null to readOtherData(2)
            0x0E -> null to readOtherData(3)

            //0x1x
            0x14, 0x15, 0x16, 0x17, 0x18 -> null to readOtherData(2)
            0x19, 0x1A -> null to readOtherData(2)
            0x1B, 0x1C -> null to readOtherData(2)
            0x1D -> null to readOtherData(4)
            0x1E, 0x1F -> null to readOtherData(4)

            //0x2x
            0x20, 0x3E -> null to CommandData.None
            0x21, 0x3F -> null to CommandData.None
            0x22, 0x23, 0x28, 0x29, 0x2A, 0x2B, 0x2C -> null to CommandData.None
            0x24, 0x25, 0x26, 0x27, 0x2D -> null to CommandData.None
            0x2E -> null to CommandData.None
            0x2F -> null to CommandData.None

            //0x3x
            0x30 -> null to CommandData.None
            0x31 -> null to CommandData.None
            0x32 -> "nop" to CommandData.None
            0x33, 0x34, 0x35, 0x36, 0x37 -> null to readOtherData(2)
            0x38, 0x39, 0x3A, 0x3B, 0x3C -> null to readOtherData(2)
            0x3D -> null to CommandData.None

            //0x4x
            0x40, 0x41, 0x42 -> null to CommandData.None
            0x43 -> null to readOtherData(1)
            0x44 -> null to readOtherData(2)
            0x45 -> null to readOtherData(1)
            0x46 -> null to readOtherData(1)
            0x47 -> null to readOtherData(1)
            0x48 -> null to readOtherData(2)
            0x49 -> null to readOtherData(2)
            0x4A -> null to readOtherData(3)
            0x4B -> null to readOtherData(2)
            0x4C -> null to readOtherData(2)
            0x4D -> null to readOtherData(3)
            0x4E -> null to readOtherData(2)
            0x4F -> null to readOtherData(3)

            //0x5x
            0x50 -> null to readOtherData(1)
            0x51 -> null to readOtherData(2)
            0x52 -> null to CommandData.None
            0x53 -> null to readOtherData(1)
            0x54 -> null to CommandData.None
            0x55 -> null to readOtherData(3)
            0x56 -> null to readOtherData(1)
            0x57, 0x58 -> null to readOtherData(1)
            0x59 -> null to readOtherData(2)
            0x5A -> null to readOtherData(2)
            0x5B -> null to readOtherData(2)
            0x5C -> null to readOtherData(2)
            0x5D -> null to CommandData.None
            0x5E -> null to CommandData.None
            0x5F -> null to CommandData.None

            //0x6x
            0x60 -> null to readOtherData(4)
            0x61 -> null to readOtherData(4)
            0x62 -> null to readOtherData(1)
            0x63 -> null to readOtherData(2)
            0x64 -> "init" to CommandData.None
            0x65 -> null to CommandData.None
            0x66 -> null to readOtherData(1)
            0x67 -> null to readOtherData(1)
            0x68 -> null to readOtherData(1)
            0x69 -> null to readOtherData(1)
            0x6A -> null to CommandData.None
            0x6B -> null to CommandData.None
            0x6C -> "addChapter" to readChapterData()
            0x6D -> null to readOtherData(2)
            0x6E -> null to readOtherData(1)
            0x6F -> null to CommandData.None

            //0x7x
            0x71 -> null to readOtherData(2)
            0x72 -> null to readOtherData(2)
            0x73 -> null to readOtherData(3)
            0x74, 0x9A -> null to readOtherData(1)
            0x75 -> null to CommandData.None
            0x76 -> null to readOtherData(3)
            0x77 -> null to readOtherData(5)
            0x78 -> null to readOtherData(3)
            0x79 -> null to readOtherData(2)
            0x7A -> null to readOtherData(2)
            0x7B -> null to readOtherData(4)
            0x7C -> null to readOtherData(4)
            0x7D -> null to readOtherData(2)
            0x7E -> null to readOtherData(5)
            0x7F -> null to readOtherData(3)

            //0x8x
            0x80 -> null to readOtherData(2)
            0x81 -> null to readOtherData(2)
            0x82 -> null to readOtherData(2)
            0x83 -> null to readOtherData(2)
            0x84 -> null to readOtherData(2)
            0x85 -> null to readOtherData(2)
            0x86 -> null to readOtherData(2)
            0x87 -> null to readOtherData(2)
            0x88 -> null to readOtherData(2)
            0x89 -> null to readOtherData(3)
            0x8A -> null to readOtherData(1)
            0x8B -> null to CommandData.None
            0x8C -> null to CommandData.None
            0x8D -> null to CommandData.None
            0x8E -> null to CommandData.None
            0x8F -> null to CommandData.None

            //0x9x
            0x90 -> null to readOtherData(1)
            0x91 -> null to readOtherData(7)
            0x92 -> null to readOtherData(1)
            0x93 -> null to readOtherData(2)
            0x94 -> null to readOtherData(2)
            0x95 -> null to readOtherData(1)
            0x96 -> null to readOtherData(2)
            0x97 -> null to CommandData.None
            0x98 -> null to CommandData.None
            0x99 -> null to readOtherData(2)
            0x9B -> null to CommandData.None
            0x9C -> null to CommandData.None
            0x9D -> null to CommandData.None
            0x9E -> null to CommandData.None
            0x9F -> null to CommandData.None

            //0xax
            0xA0 -> null to CommandData.None
            0xA1 -> null to readOtherData(2)
            0xA2 -> null to readOtherData(2)
            0xA3 -> null to readOtherData(2)
            0xA4 -> null to readOtherData(2)
            0xA5 -> null to readOtherData(5)
            0xA6 -> null to readOtherData(5)
            0xA7 -> null to CommandData.None
            0xA8 -> null to readOtherData(1)
            0xA9 -> null to CommandData.None
            0xAA -> null to CommandData.None
            0xAB -> null to CommandData.None
            0xAC -> null to readOtherData(9)
            0xAD -> null to CommandData.None
            0xAE -> null to readOtherData(1)
            0xAF -> null to readOtherData(2)

            //0xbx
            0xB0 -> null to CommandData.None
            0xB1 -> null to readOtherData(3)
            0xB2 -> null to CommandData.None
            0xB3 -> null to readOtherData(1)
            0xB4 -> null to CommandData.None
            0xB5 -> null to readOtherData(2)
            0xB6 -> null to readOtherData(1)
            0xB7 -> null to CommandData.None
            0xB8 -> null to CommandData.None
            0xB9 -> null to readOtherData(2)
            0xBA -> null to readOtherData(1)
            0xBB -> null to CommandData.None
            0xBC -> null to CommandData.None
            0xBD -> null to CommandData.None
            0xBE -> null to readOtherData(1)
            // P2
            0xBF -> null to readOtherData(3)

            //0xcx
            0xC0 -> null to readOtherData(1)
            0xC1 -> null to readOtherData(4)
            0xC2 -> null to CommandData.None
            0xC3 -> null to readOtherData(3)
            0xC4 -> null to CommandData.None
            0xC5 -> null to CommandData.None
            0xC6 -> null to CommandData.None
            0xC7 -> null to CommandData.None
            0xC8 -> null to CommandData.None
            0xC9 -> null to CommandData.None
            0xCA -> null to CommandData.None
            0xCB -> null to CommandData.None
            0xCC -> null to CommandData.None
            0xCD -> null to CommandData.None
            0xCE -> null to CommandData.None
            0xCF -> null to CommandData.None

            //0xdx
            0xD0 -> null to CommandData.None
            0xD1 -> null to readOtherData(1)
            0xD2 -> null to readOtherData(1)
            0xD3 -> null to readOtherData(1)
            0xD4 -> null to readOtherData(1)
            0xD5 -> null to readOtherData(1)
            0xD6 -> null to readOtherData(1)
            0xD7 -> null to readOtherData(1)
            0xD8 -> null to readOtherData(1)
            0xD9 -> null to readOtherData(1)
            0xDA -> null to readOtherData(2)
            0xDB -> null to CommandData.None
            0xDC -> null to readOtherData(4)
            0xDD -> null to readOtherData(3)
            0xDE -> null to CommandData.None
            0xDF -> null to readOtherData(2)

            //0xex
            0xE0 -> null to readOtherData(2)
            0xE1 -> null to readOtherData(2)
            0xE2 -> null to readOtherData(2)
            0xE3 -> null to CommandData.None
            0xE4 -> null to readOtherData(2)
            0xE5 -> null to readOtherData(2)
            0xE6 -> null to readOtherData(2)
            0xE7 -> null to readOtherData(1)
            0xE8 -> null to readOtherData(1)
            0xE9 -> null to readOtherData(1)
            0xEA -> null to readOtherData(1)
            0xEB -> null to readOtherData(1)
            0xEC -> null to readOtherData(2)
            0xED -> null to readOtherData(2)
            0xEE -> null to readOtherData(2)
            0xEF -> null to readOtherData(1)

            //0xfx
            0xF0 -> null to readOtherData(2)

            else -> error("Unknown opcode ${op.toWHex()} at ${pos.toWHex()} in section ${index.toWHex()}")
          }
          commands.add(Command(pos, op, name, data))
        }
      }
      index to commands
    }
      .toMap()
  }

  fun writeSectionsTo(dir: File) {
    sections.forEachIndexed { index, data ->
      dir.child("SCRIPT_${index}.BIN").writeBytes(data)
    }
  }

  private fun KioInputStream.readOtherData(argsCount: Int): CommandData.Other {
    val args = (0..<argsCount).map { readShort() }
    return CommandData.Other(args)
  }

  private fun KioInputStream.readChapterData(): CommandData.Chapter {
    val index = readShort().toUnsignedInt()
    val size = readShort().toUnsignedInt()
    val text = readStringAndTrim(size - 2, Charsets.WINDOWS_932)
    return CommandData.Chapter(index, text)
  }

  private fun KioInputStream.readTextData(): CommandData {
    val index = readShort().toUnsignedInt()
    val nameSize = readShort().toUnsignedInt()
    val name = readStringAndTrim(nameSize - 2, Charsets.WINDOWS_932).takeIf { it.isNotEmpty() }
    val textSize = readShort().toUnsignedInt()
    val text = readStringAndTrim(textSize - 2, Charsets.WINDOWS_932)
    return CommandData.Text(index, name, text)
  }
}

data class Command(val pos: Int, val op: Int, val name: String?, val data: CommandData)

sealed interface CommandData {
  data object None : CommandData

  data class Other(val args: List<Short>) : CommandData

  data class Text(val index: Int, val name: String?, val text: String) : CommandData

  data class FragmentText(val index: Int, val name: String?, val fragmentReference: List<Byte>) : CommandData

  data class Chapter(val index: Int, val text: String) : CommandData
}

fun List<Command>.printCommands() {
  this.forEachIndexed { index, cmd ->
    print("[#${index.toShort().toWHex()} @${cmd.pos.toWHex()}: ${cmd.op.toShort().toWHex()}] ")
    when {
      cmd.name.isNullOrBlank() -> null to print("op${cmd.op.toByte().toWHex()}(")
      else -> null to print("${cmd.name}(")
    }
    when (cmd.data) {
      CommandData.None -> Unit
      is CommandData.Other -> print(cmd.data.args.joinToString(", ") { it.toWHex() })
      is CommandData.Text -> print("${cmd.data.index.toWHex()}, ${cmd.data.name?.quoteWrap()}, ${cmd.data.text.quoteWrap()}")
      is CommandData.FragmentText ->
        print("${cmd.data.index.toWHex()}, ${cmd.data.name?.quoteWrap()}, fragment ${cmd.data.fragmentReference.joinToString(separator = "") { it.toWHex() }}")
      is CommandData.Chapter -> print("${cmd.data.index.toWHex()}, ${cmd.data.text.quoteWrap()}")
    }
    println(")")
  }
}

private fun String.quoteWrap() = "\"$this\""
