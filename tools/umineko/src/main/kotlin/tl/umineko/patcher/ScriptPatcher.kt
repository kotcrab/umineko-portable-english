package tl.umineko.patcher

import kio.KioOutputStream
import kio.util.WINDOWS_932
import kio.util.child
import kio.util.readJson
import kio.util.writeJson
import org.apache.commons.csv.CSVFormat
import tl.umineko.file.Command
import tl.umineko.file.CommandData
import tl.umineko.file.ScrFile
import java.io.ByteArrayOutputStream
import java.io.File

class ScriptPatcher(
  private val mode: UminekoPatcher.Mode,
  private val translationDir: File,
  private val publicBuild: Boolean,
  private val warn: (String) -> Unit,
) {
  companion object {
    private const val DEBUG_DUMP = false

    const val MAX_FRAGMENTS = 7
  }

  private val csvReader = CSVFormat.DEFAULT.builder()
    .setHeader()
    .setSkipHeaderRecord(true)
    .get()

  fun patchScript(scrFile: File, fragmentDir: File) {
    val sections = ScrFile(scrFile).parseSections()
      .mapValues { it.value.toMutableList() }
    val fragmentsSink = FragmentsSink()

    patchChapters(sections)
    insertUminekoProjectTranslation(sections, fragmentsSink)
    if (mode == UminekoPatcher.Mode.PORTABLE_1) {
      patchCommands1(sections)
    }

    ScrWriter(sections.values.toList())
      .writeTo(scrFile)
    if (mode.useScriptFragments) {
      fragmentsSink.writeFragments(fragmentDir)
    }
  }

  private fun patchChapters(sections: Map<Int, MutableList<Command>>) {
    val chapters = csvReader.parse(translationDir.child("chapters-${mode.number}.csv").readText().reader())
    val locationIndex = chapters.headerMap.getValue("location")
    val englishIndex = chapters.headerMap.getValue("english")

    chapters.forEach { row ->
      val (section, position) = row[locationIndex].split("-").map { it.toInt() }
      val english = row[englishIndex]
      val commands = sections.getValue(section)
      val commandIndex = commands.indexOfFirst { it.pos == position }
      val command = commands[commandIndex]
      if (command.data !is CommandData.Chapter) {
        error("Command in section $section, at positon $position is not a chapter command")
      }
      commands[commandIndex] = command.copy(
        data = command.data.copy(text = applyScriptReplacements(english))
      )
    }
  }

  private fun insertUminekoProjectTranslation(sections: Map<Int, MutableList<Command>>, fragmentsSink: FragmentsSink) {
    val namesMap = readNamesMap()
    val ep1Lines by lazy { readEp1Lines() }
    val ep2Lines by lazy { readEp2Lines() }
    val ep3Lines by lazy { readEp3Lines() }
    val ep4Lines by lazy { readEp4Lines() }
    val trailerLines = readLines("trailer-${mode.number}.json").iterator()
    val characters = readSectionFile("characters-${mode.number}.csv").iterator()
    val tips = readSectionFile("tips-${mode.number}.csv").iterator()

    sections.forEach { (i, commands) ->
      println("Processing section $i...")
      if (commands.none { it.data is CommandData.Text }) {
        return@forEach
      }
      val (matchedLines, fragmentId) = when (mode) {
        UminekoPatcher.Mode.PORTABLE_1 -> {
          when (i) {
            in 0..21 -> ep1Lines to 0
            in 22..41 -> ep2Lines to 1
            42 -> ep1Lines to 2 // Tea party
            44 -> ep2Lines to 2
            46 -> ep1Lines to 2 // ????
            48 -> ep2Lines to 2
            51 -> trailerLines to 2
            else -> null to null
          }
        }
        UminekoPatcher.Mode.PORTABLE_2 -> {
          when (i) {
            in 0..22 -> ep3Lines to 0
            in 23..43 -> ep4Lines to 1
            44 -> ep3Lines to 2 // Tea party
            46 -> ep4Lines to 2
            48 -> ep3Lines to 2 // ????
            50 -> ep4Lines to 2
            53 -> trailerLines to 2
            else -> null to null
          }
        }
      }
      val directLines = when (mode) {
        UminekoPatcher.Mode.PORTABLE_1 -> {
          when (i) {
            49 -> characters
            50 -> tips
            else -> null
          }
        }
        UminekoPatcher.Mode.PORTABLE_2 -> {
          when (i) {
            51 -> characters
            52 -> tips
            else -> null
          }
        }
      }

      when {
        matchedLines != null -> {
          commands.replaceAll { command ->
            if (command.data !is CommandData.Text) {
              return@replaceAll command
            }

            val scriptParts = parseScriptText(command.data.text)
            val scriptTextParts = scriptParts.mapNotNull { (it as? TextPart.Normal)?.text }
            val (jpParts, enParts) = matchedLines.next()
            val strippedJpParts = stripUminekoProjectTextsForMatching(jpParts)
            val convertedEnParts = convertUminekoProjectEnglishText(enParts)
            if (DEBUG_DUMP) {
              println(scriptTextParts.joinToString("|"))
              println(strippedJpParts.joinToString("|"))
              println(jpParts.joinToString("|"))
              println(enParts.joinToString("|"))
              println(convertedEnParts.joinToString("|"))
            }
            if (enParts.size != jpParts.size) {
              error("TL ERROR: Not matching JP and EN parts size")
            }
            if (strippedJpParts.size != convertedEnParts.size) {
              error("TL ERROR: Not matching processed JP and EN parts size")
            }
            if (scriptTextParts.size != strippedJpParts.size) {
              error("TL ERROR: Not matching text part count")
            }
            if (scriptTextParts != strippedJpParts) {
              println("TL WARN: Not matching content:\n$scriptTextParts\n$strippedJpParts\n$enParts")
            }
            if (strippedJpParts.size != jpParts.size) {
              println("TL WARN: Parts size before and after stripping are different:\n$strippedJpParts\n$jpParts")
            }

            val enPartsIterator = convertedEnParts.iterator()
            val newCommandName = command.data.name?.let { namesMap[it] ?: error("Missing translation for name: $it") }
            val newCommandText = scriptParts
              .joinToString(separator = "") { part ->
                when (part) {
                  is TextPart.ControlCode -> {
                    val code = ControlCode.entries.first { it.name.equals(part.name, ignoreCase = true) }
                    val arg = when {
                      code == ControlCode.SP -> "07"
                      else -> part.arg
                    }
                    "${code.replacement}${arg}"
                  }
                  is TextPart.Normal -> {
                    enPartsIterator.next()
                  }
                }
              }
              .replace("${ControlCode.KW.replacement}${ControlCode.VC.replacement}071680<<REMOVED>>", "") // EP 1
              .replace("${ControlCode.WT.replacement}1e<<REMOVED>>", "") // EP 4
            if (mode.useScriptFragments && fragmentId != null) {
              command.copy(
                data = CommandData.FragmentText(
                  command.data.index,
                  newCommandName,
                  fragmentsSink.appendText(fragmentId, applyScriptReplacements(newCommandText))
                )
              )
            } else {
              command.copy(
                data = CommandData.Text(
                  command.data.index,
                  newCommandName,
                  applyScriptReplacements(newCommandText)
                )
              )
            }
          }
        }
        directLines != null -> {
          commands.replaceAll { command ->
            if (command.data !is CommandData.Text) {
              return@replaceAll command
            }
            val newCommandName = command.data.name?.let { namesMap[it] ?: error("Missing translation for name: $it") }
            command.copy(
              data = CommandData.Text(
                command.data.index,
                newCommandName,
                applyScriptReplacements(convertUminekoProjectEnglishText(listOf(directLines.next())).first())
              )
            )
          }
        }
        else -> {
          warn("Unknown TL source for section $i")
        }
      }
    }
  }

  private fun patchCommands1(sections: Map<Int, MutableList<Command>>) {
    fun changeMessageMode(command: Command): Command {
      if (command.op != 1 || command.data !is CommandData.Other) {
        error("Not a message mode command")
      }
      return command.copy(data = CommandData.Other(listOf(1, 1)))
    }

    sections[7]?.let { commands ->
      commands.indexOfFirst { it.pos == 0xF4D0 }
        .let { index ->
          commands[index] = changeMessageMode(commands[index])
        }
    }
    sections[9]?.let { commands ->
      commands.indexOfFirst { it.pos == 0xC200 }
        .let { index ->
          commands[index] = changeMessageMode(commands[index])
        }
    }
  }

  private fun dumpSection(commands: List<Command>, outFile: File) {
    val entries = commands
      .filter { it.data is CommandData.Text }
      .map { command ->
        val scriptParts = parseScriptText((command.data as CommandData.Text).text)
        val scriptTextParts = scriptParts.mapNotNull { (it as? TextPart.Normal)?.text }
        listOf(scriptTextParts, List(scriptTextParts.size) { index -> index.toString() })
      }
    outFile.writeJson(entries)
  }

  private fun readSectionFile(name: String): List<String> {
    val rows = csvReader.parse(translationDir.child(name).readText().reader())
    val enIndex = rows.headerMap.getValue("en")
    return rows
      .map { it[enIndex] }
  }

  private fun readNamesMap(): Map<String, String> {
    val chapters = csvReader.parse(translationDir.child("names-${mode.number}.csv").readText().reader())
    val jpIndex = chapters.headerMap.getValue("jp")
    val enIndex = chapters.headerMap.getValue("en")

    return chapters.associate { row ->
      row[jpIndex] to row[enIndex]
    }
  }

  private fun readEp1Lines(): Iterator<List<List<String>>> {
    val lines = readLines("ep1.json")
    run {
      // handle script correction
      val index = lines.indexOfFirst { it[0][0] == "「……それで紗音は、" }
      lines[index] = listOf(
        lines[index][0].toMutableList().also { it.add(2, "<<REMOVED>>") },
        lines[index][1].toMutableList().also {
          // also correct mistakes in v1 patch (where it was missing part initial spaces and ending quote) in a way that keeps save compatibility
          it[1] = it[1].replaceFirst(" ..", " ")
          it[2] = it[2].replaceFirst(" ..", " ")

          it.add(2, "<<REMOVED>>")
        }
      )
    }
    return lines.iterator()
  }

  private fun readEp2Lines(): Iterator<List<List<String>>> {
    val lines = readLines("ep2.json")
    run {
      // uncensor line
      val index = lines.indexOfFirst { it[0][0].startsWith("戦人は酒棚") }
      lines[index] = listOf(
        lines[index][0].take(1),
        listOf(lines[index][1].joinToString("")),
      )
    }
    return lines.iterator()
  }

  private fun readEp3Lines(): Iterator<List<List<String>>> {
    val lines = readLines("ep3.json")
    return lines.iterator()
  }

  private fun readEp4Lines(): Iterator<List<List<String>>> {
    val lines = readLines("ep4.json")
    run {
      // change blank to match portable version
      val index = lines.indexOfFirst { it[0].getOrNull(4) == "！" }
      lines[index] = listOf(
        lines[index][0],
        lines[index][1].toMutableList()
          .also {
            it[3] = it[3].dropLast(1)
            it[4] = "!"
          },
      )
    }
    run {
      // change blank to dummy to remove it later
      val index = lines.indexOfFirst { it[1].getOrNull(1)?.startsWith("{Comment:Intentionally") ?: false }
      lines[index] = listOf(
        lines[index][0],
        lines[index][1].toMutableList()
          .also {
            it[1] = "<<REMOVED>>"
          },
      )
    }
    run {
      // remove extra blank
      val index = lines.indexOfFirst { it[0][0] == "「悲しい？" }
      lines[index] = listOf(
        lines[index][0].toMutableList().also { it.removeAt(1) },
        lines[index][1].toMutableList().also { it.removeAt(1) }
      )
    }
    return lines.iterator()
  }

  private fun readLines(name: String): MutableList<List<List<String>>> {
    return translationDir.child(name)
      .readJson<List<List<List<String>>>>()
      .toMutableList()
  }

  private fun convertUminekoProjectEnglishText(texts: List<String>): List<String> {
    data class PendingControlCode(
      val name: String,
      val args: MutableList<String> = mutableListOf(),
      val buffer: StringBuilder = StringBuilder(),
    ) {
      fun commitArg() {
        args.add(buffer.toString())
        buffer.clear()
      }
    }

    fun convertControlCode(
      name: String,
      args: List<String>,
    ): String {
      return when (name) {
        "ruby" -> {
          "${ControlCode.RB.replacement}${args[0]}${ControlCode.RS.replacement}${args[1]}${ControlCode.RE.replacement}"
        }
        "loghint" -> {
          "${ControlCode.RB.replacement}${scriptLogHintMarker}${args[0]}${ControlCode.RS.replacement}${args[1]}${ControlCode.RE.replacement}"
        }
        "i" -> { // italics, unsupported
          args[0]
        }
        "p" -> {
          when (val id = args[0].toInt()) {
            0 -> args[1] // japanese text
            1 -> "${ControlCode.CO.replacement}01${args[1]}${ControlCode.CO.replacement}00" // red text
            2 -> "${ControlCode.CO.replacement}02${args[1]}${ControlCode.CO.replacement}00" // blue text
            4 -> args[1] // japanese text in menu
            else -> error("Unhandled preset id: $id")
          }
        }
        "f" -> {
          when (val id = args[0].toInt()) {
            5 -> args[1].uppercase() // small caps font
            7 -> args[1].uppercase() // italics small caps font
            else -> error("Unhandled font id: $id")
          }
        }
        "c" -> {
          val color = args[0]
          when {
            color.equals("86EF9C", ignoreCase = true) -> args[1] // grimoire, ignore
            color.equals("FF0000", ignoreCase = true) -> "${ControlCode.CO.replacement}01${args[1]}${ControlCode.CO.replacement}00" // red text
            else -> error("Unhandled color: $color")
          }
        }
        "a" -> { // text align, not needed
          args[1]
        }
        "nobreak", "nobr" -> {
          args[0]
        }
        "e" -> {
          args[1]
        }
        "m" -> { // margin
          args[1]
        }
        "y" -> "" // condition check
        "n" -> args[1] // condition check
        else -> error("Unhandled control code: $name")
      }
    }

    val partSeparator = "<<END>>"

    val output = StringBuilder()
    val stack = mutableListOf<PendingControlCode>()

    fun sink(): StringBuilder =
      stack.lastOrNull()?.buffer ?: output

    val text = texts
      .joinToString(partSeparator)
      .replace("{n}", ControlCode.CR.replacement)
      .replace("{t}", ControlCode.T0.replacement)
      .replace("{0}", scriptZeroWidthSpace.toString()) // zero width space
      .replace("{-}", scriptZeroWidthSpace.toString()) // soft hyphen, unsupported
      .replace("\u00A0", "") // no-break space, very rare, looks like mistakes in script
    var index = 0

    while (index < text.length) {
      when (val c = text[index]) {
        '{' -> {
          val nameStart = ++index
          while (index < text.length && text[index] != ':' && text[index] != '}') {
            index++
          }
          val name = text.substring(nameStart, index)
          stack.add(PendingControlCode(name))
          index++
        }
        ':' -> {
          val controlCode = stack.lastOrNull()
          when {
            controlCode != null -> controlCode.commitArg()
            else -> output.append(c)
          }
          index++
        }
        '}' -> {
          val controlCode = stack.removeLastOrNull()
            ?: error("Unexpected } at $index")
          controlCode.commitArg()
          sink().append(convertControlCode(controlCode.name, controlCode.args))
          index++
        }
        else -> {
          sink().append(c)
          index++
        }
      }
    }

    if (stack.isNotEmpty()) {
      error("Control code stack is not empty after parsing: $stack")
    }
    return applySmartQuotes(output.toString())
      .trimStart()
      .split(partSeparator)
      .filter { it.isNotBlank() }
  }

  private fun stripUminekoProjectTextsForMatching(texts: List<String>): List<String> {
    val texts = texts.map {
      it
        .replace("{n}", "")
        .replace("〜", "～")
    }

    val currentPart = StringBuilder()
    val newTexts = mutableListOf<String>()
    var partIndex = 0
    var textIndex = 0
    var controlCodeLevel = 0

    while (partIndex < texts.size) {
      while (textIndex < texts[partIndex].length) {
        when {
          texts[partIndex].startsWith("{t}", textIndex) -> {
            textIndex += 3
          }
          texts[partIndex].startsWith("{e:070:", textIndex) ||
            texts[partIndex].startsWith("{e:120:", textIndex) ||
            texts[partIndex].startsWith("{e:170:", textIndex) -> {
            textIndex += 7
            controlCodeLevel++
          }
          texts[partIndex].startsWith("{a:c:", textIndex) -> {
            textIndex += 5
            controlCodeLevel++
          }
          texts[partIndex].startsWith("{i:", textIndex) -> {
            textIndex += 3
            controlCodeLevel++
          }
          texts[partIndex].startsWith("{p:0:", textIndex) ||
            texts[partIndex].startsWith("{p:1:", textIndex) ||
            texts[partIndex].startsWith("{p:2:", textIndex) -> {
            textIndex += 5
            controlCodeLevel++
          }
          texts[partIndex][textIndex] == '}' -> {
            textIndex++
            controlCodeLevel--
          }
          else -> {
            currentPart.append(texts[partIndex][textIndex])
            textIndex++
          }
        }
      }
      if (currentPart.isNotEmpty()) {
        newTexts.add(currentPart.toString())
        currentPart.clear()
      }
      partIndex++
      textIndex = 0
    }

    if (controlCodeLevel != 0) {
      error("Not all control codes matched: $texts")
    }

    return newTexts
  }

  private val rubyRegex = Regex("""rb.*?rs(.*?)re""")
  private val ksRegex = Regex("""ks(.*?)ke""")
  private val lsRegex = Regex("""ls(.*?)le""")

  private fun parseScriptText(text: String): List<TextPart> {
    val parts = mutableListOf<TextPart>()
    val currentPart = StringBuilder()
    val text = text
      .replace(rubyRegex, "$1")
      .replace(ksRegex, "$1")
      .replace(lsRegex, "$1")
    var index = 0

    fun addCurrentPart() {
      if (currentPart.isNotBlank()) {
        if (currentPart.any { it.code < 127 }) {
          error("ASCII in JP text part is not allowed: $currentPart")
        }
        parts.add(TextPart.Normal(currentPart.toString()))
        currentPart.clear()
      }
    }

    fun addControlCode(length: Int) {
      require(length >= 2)
      addCurrentPart()
      parts.add(TextPart.ControlCode(text.substring(index, index + 2), text.substring(index + 2, index + length)))
      index += length
    }

    while (index < text.length) {
      when {
        text.startsWith("cr", index) ||
          text.startsWith("t0", index) -> index += 2
        text.startsWith("co", index) ||
          text.startsWith("zm", index) -> index += 4
        text.startsWith("vc", index) -> addControlCode(8)
        text.startsWith("sp", index) -> addControlCode(4)
        text.startsWith("wt", index) -> addControlCode(4)
        text.startsWith("kw", index) ||
          text.startsWith("sf", index) ||
          text.startsWith("ev", index) ||
          text.startsWith("ls", index) ->
          addControlCode(2)
        else -> {
          currentPart.append(text[index])
          index++
        }
      }
    }

    addCurrentPart()

    return parts
  }

  private sealed interface TextPart {
    data class Normal(val text: String) : TextPart

    data class ControlCode(val name: String, val arg: String) : TextPart
  }

  fun applySmartQuotes(input: String): String {
    val charOpeningSingleQuote = '‘'
    val charClosingSingleQuote = '’'
    val charOpeningDoubleQuote = '“'
    val charClosingDoubleQuote = '”'
    val charApostrophe = '’'

    var openedSingleQuotes = 0
    var openedDoubleQuotes = 0
    var prevChar: Char? = null

    return buildString {
      for (char in input) {
        val newChar = when (char) {
          '"' -> {
            when {
              // stacking double quotes: “" -> ““
              prevChar == charOpeningDoubleQuote -> {
                openedDoubleQuotes++
                charOpeningDoubleQuote
              }

              // beginning of string, space or after explicit new line.
              prevChar == null || prevChar == ' ' -> {
                if (openedDoubleQuotes == 0) {
                  openedDoubleQuotes++
                  charOpeningDoubleQuote
                } else {
                  openedSingleQuotes++
                  charOpeningSingleQuote
                }
              }

              // close nested single quotes first
              openedSingleQuotes > 0 -> {
                openedSingleQuotes--
                charClosingSingleQuote
              }

              // otherwise close double quotes.
              else -> {
                openedDoubleQuotes--
                charClosingDoubleQuote
              }
            }
          }
          '\'' -> charApostrophe
          else -> char
        }

        append(newChar)
        prevChar = when (newChar) {
          '\n' -> null
          else -> newChar
        }
      }
    }
  }

  private class FragmentsSink {
    private val fragments = mutableMapOf<Int, KioOutputStream>()

    fun appendText(fragmentId: Int, text: String): List<Byte> {
      if (fragmentId !in 0..MAX_FRAGMENTS) {
        error("Fragment ID out of range")
      }
      val out = fragments.getOrPut(fragmentId) { KioOutputStream(ByteArrayOutputStream()) }
      val pos = out.pos()
      if (pos !in 0..0x1FFFFF) {
        error("Fragment position out of range")
      }
      out.writeNullTerminatedString(applyScriptReplacements(text), Charsets.WINDOWS_932)
      out.align(2)
      return listOf(
        scriptFragmentTextMarker.code.toByte(),
        ((fragmentId shl 5) or (pos ushr 16)).toByte(),
        (pos ushr 8).toByte(),
        pos.toByte()
      )
    }

    fun writeFragments(fragmentDir: File) {
      fragments.forEach { (id, stream) ->
        stream.close()
        fragmentDir.child("$id.BIN")
          .writeBytes(stream.getAsByteArrayOutputStream().toByteArray())
      }
    }
  }
}
