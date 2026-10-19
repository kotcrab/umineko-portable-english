package tl.umineko.cli

import kio.util.child
import kio.util.writeJson
import tl.umineko.uminekoOutput
import tl.umineko.util.next
import java.io.File

fun main(args: Array<String>) {
  ParseUminekoProjectScript(
    File(args[0]),
    uminekoOutput.child("script-tl").also { it.mkdir() }
  )

  println("Done")
}

class ParseUminekoProjectScript(
  uminekoProjectDir: File,
  outDir: File,
) {
  init {
    val textPartRegex = Regex("`(.*?)`")

    val enChars = mutableSetOf<Char>()

    val episodes = (1..8).map { "ep$it" }
    episodes.forEach { episode ->
      val enDir = uminekoProjectDir.child("story/$episode/en")
      val jpDir = uminekoProjectDir.child("story/$episode/jp")
      val episodeLines = enDir.listFiles()!!
        .sortedWith(
          compareBy<File>(
            { !it.nameWithoutExtension.endsWith("op") },
            { it.nameWithoutExtension.split("_").last().toIntOrNull() }
          )
        )
        .flatMap { enFile ->
          println("Processing ${enFile.name}...")
          val scriptFile = uminekoProjectDir.child("game/main/${enFile.name}")
          val scriptLines = scriptFile.readLines()
            .filter { it.startsWith("d ") || it.startsWith("d2 ") || it.startsWith("setlog 1") }
            .map { line ->
              textPartRegex.findAll(line)
                .map { it.value.trim('`') }
                .toList()
            }
          val jpFile = jpDir.child(enFile.name)
          val jpLines = jpFile.readLines().map { it.trim('`') }
          val enLines = enFile.readLines().map { it.trim('`') }
          if (jpLines.size != enLines.size) {
            error("Mismatch between translation number of lines: ${jpLines.size} != ${enLines.size}")
          }
          val scriptLinesWithoutStandaloneNewLines = scriptLines
            .map { lines ->
              lines.filterNot { it == "{n}" || it == "{n}{n}" }
            }
          if (jpLines.size != scriptLinesWithoutStandaloneNewLines.sumOf { it.size }) {
            error("Mismatch between script number of lines: ${jpLines.size} != ${scriptLinesWithoutStandaloneNewLines.sumOf { it.size }}")
          }

          enLines.forEach { line ->
            enChars.addAll(line.asIterable())
          }
          val enLinesIterator = enLines.iterator()
          scriptLinesWithoutStandaloneNewLines
            .mapIndexed { lineIndex, lineParts ->
              val allLineParts = scriptLines[lineIndex]
              val enPartsIterator = enLinesIterator.next(lineParts.size).iterator()
              val fixedEnParts = buildList {
                var pendingPrefix = ""
                allLineParts.forEach { linePart ->
                  if (linePart == "{n}" || linePart == "{n}{n}") {
                    pendingPrefix += linePart
                    return@forEach
                  }
                  when {
                    pendingPrefix.isNotEmpty() -> {
                      add(pendingPrefix + enPartsIterator.next().trimStart())
                      pendingPrefix = ""
                    }
                    else -> add(enPartsIterator.next())
                  }
                }
                if (pendingPrefix.isNotEmpty()) {
                  error("Trailing standalone new line in script")
                }
              }
              if (enPartsIterator.hasNext()) {
                error("Parts not fully processed")
              }
              listOf(lineParts, fixedEnParts)
            }
        }
      outDir.child("$episode.json")
        .writeJson(episodeLines)
    }

    println("Used chars:\n${enChars.sorted().minus("\n").joinToString("")}")
  }
}
