package tl.umineko.patcher

import kio.KioOutputStream
import kio.util.WINDOWS_932
import tl.umineko.file.Command
import tl.umineko.file.CommandData
import java.io.ByteArrayOutputStream
import java.io.File

class ScrWriter(
  private val sections: List<List<Command>>,
) {
  fun writeTo(file: File) {
    val sectionsBytes = sections.map { it.serializeCommands() }

    with(KioOutputStream(file)) {
      writeNullTerminatedString("scr")
      writeInt(sectionsBytes.size)
      var sectionStart = pos() + sectionsBytes.size * 4
      sectionsBytes.forEach { sectionBytes ->
        writeInt(sectionStart)
        sectionStart += sectionBytes.size
      }
      sectionsBytes.forEach { sectionBytes ->
        writeBytes(sectionBytes)
      }
      close()
    }
  }

  private fun List<Command>.serializeCommands(): ByteArray {
    val out = ByteArrayOutputStream()
    with(KioOutputStream(out)) {
      fun writeName(name: String?) {
        if (name == null) {
          writeShort(4)
          writeShort(0)
        } else {
          val nameBytes = serializeScriptText(name)
          writeShort((nameBytes.size + 2).toShort())
          writeBytes(nameBytes)
        }
      }

      forEach { cmd ->
        writeShort(cmd.op.toShort())
        when (cmd.data) {
          CommandData.None -> Unit
          is CommandData.Other -> cmd.data.args.forEach { writeShort(it) }
          is CommandData.Text -> {
            writeShort(cmd.data.index.toShort())
            writeName(cmd.data.name)
            val textBytes = serializeScriptText(cmd.data.text)
            writeShort((textBytes.size + 2).toShort())
            writeBytes(textBytes)
          }
          is CommandData.FragmentText -> {
            writeShort(cmd.data.index.toShort())
            writeName(cmd.data.name)
            writeShort(6)
            if (cmd.data.fragmentReference.size != 4) {
              error("Fragment reference must be 4 bytes")
            }
            cmd.data.fragmentReference.forEach {
              writeByte(it)
            }
          }
          is CommandData.Chapter -> {
            writeShort(cmd.data.index.toShort())
            val bytes = serializeScriptText(cmd.data.text)
            writeShort((bytes.size + 2).toShort())
            writeBytes(bytes)
          }
        }
      }
      close()
    }
    return out.toByteArray()
  }

  private fun serializeScriptText(text: String): ByteArray {
    val bytesOut = ByteArrayOutputStream()
    with(KioOutputStream(bytesOut)) {
      writeNullTerminatedString(text, Charsets.WINDOWS_932)
      align(2)
      close()
    }
    return bytesOut.toByteArray()
  }
}
