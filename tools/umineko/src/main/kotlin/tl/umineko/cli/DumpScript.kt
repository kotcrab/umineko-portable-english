package tl.umineko.cli

import kio.util.child
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import tl.umineko.file.Command
import tl.umineko.file.CommandData
import tl.umineko.file.ScrFile
import tl.umineko.file.printCommands
import tl.umineko.uminekoExtract
import tl.umineko.uminekoOutput
import java.io.File

fun main() {
  ScrFile(uminekoExtract.child("ISO 1/SCR.BIN"))
    .also { scr ->
      scr.writeSectionsTo(uminekoOutput.child("Script 1").also { it.mkdirs() })
      val sections = scr.parseSections()
      sections.forEach { (i, commands) ->
        println("Section $i:")
        commands.printCommands()
      }
      writeChaptersCsv(sections, uminekoOutput.child("chapters-1.csv"))
      writeNamesCsv(sections, uminekoOutput.child("names-1.csv"))
      writeSectionCsv(sections.getValue(49), uminekoOutput.child("characters-1.csv"))
      writeSectionCsv(sections.getValue(50), uminekoOutput.child("tips-1.csv"))
      writeSectionCsv(sections.getValue(51), uminekoOutput.child("trailer-1.csv"))
    }

  ScrFile(uminekoExtract.child("ISO 2/SCR.BIN"))
    .also { scr ->
      scr.writeSectionsTo(uminekoOutput.child("Script 2").also { it.mkdirs() })
      val sections = scr.parseSections()
      sections.forEach { (i, commands) ->
        println("Section $i:")
        commands.printCommands()
      }
      writeChaptersCsv(sections, uminekoOutput.child("chapters-2.csv"))
      writeNamesCsv(sections, uminekoOutput.child("names-2.csv"))
      writeSectionCsv(sections.getValue(51), uminekoOutput.child("characters-2.csv"))
      writeSectionCsv(sections.getValue(52), uminekoOutput.child("tips-2.csv"))
      writeSectionCsv(sections.getValue(53), uminekoOutput.child("trailer-2.csv"))
    }

  println("Done")
}

private fun writeChaptersCsv(sections: Map<Int, List<Command>>, outFile: File) {
  val chaptersCsv = StringBuilder()
  chaptersCsv.appendLine("location,japanese,english")
  sections.forEach { (i, commands) ->
    commands
      .filter { it.data is CommandData.Chapter }
      .forEach { command ->
        val data = command.data as CommandData.Chapter
        chaptersCsv.appendLine("$i-${command.pos},${data.text}")
      }
  }
  outFile.writeText(chaptersCsv.toString())
}

private fun writeNamesCsv(sections: Map<Int, List<Command>>, outFile: File) {
  val names = mutableSetOf<String>()
  sections.forEach { (_, commands) ->
    commands
      .filter { it.data is CommandData.Text }
      .forEach { command ->
        val data = command.data as CommandData.Text
        data.name?.let { names.add(it) }
      }
  }

  val namesCsv = StringBuilder()
  namesCsv.appendLine("jp,en")
  names.sorted().forEach { name ->
    namesCsv.appendLine(name)
  }
  outFile.writeText(namesCsv.toString())
}

private fun writeSectionCsv(commands: List<Command>, outFile: File) {
  val format = CSVFormat.DEFAULT.builder()
    .setHeader("jp", "en")
    .get()

  outFile.bufferedWriter().use { writer ->
    CSVPrinter(writer, format).use { csv ->
      commands
        .filter { it.data is CommandData.Text }
        .forEach { command ->
          val data = command.data as CommandData.Text
          csv.printRecord(data.text.replace("cr", "\n"), "")
        }
    }
  }
}
