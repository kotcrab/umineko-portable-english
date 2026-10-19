package tl.umineko.patcher

val scriptRemappedCharacters = mapOf(
  // Used UTF dashes:
  // ‐ E.g. between name and honorifics, SJIS supported, remap to ASCII -
  // – Only used 3 times between two years, SJIS unsupported, remap to SJIS ‐, must be done after above
  // — In general as sentence pause, SJIS unsupported, remap to SJIS ー and edit it in font
  // ― Rarely used, should create continuous line if multiple are next to each other, SJIS supported, leave as is
  '‐' to '-',
  '–' to '‐',
  '—' to 'ー',

  '•' to '・',

  // remapped
  '⁰' to 'а',
  '³' to 'б',
  '⁵' to 'в',
  '⁶' to 'г',
  '⁸' to 'д',
  'â' to 'е',
  'ç' to 'ё',
  'è' to 'ж',
  'é' to 'з',
  'ê' to 'и',
  'ò' to 'й',
  'ö' to 'к',
  // ...лмнопрстуфхцчшщъыьэюя
)

// they look weird in PSP save text so replace back with normal ASCII (this map direction is reversed than above)
val scriptSaveRemappedCharacters = mapOf(
  '‘' to '\'',
  '’' to '\'',
  '“' to '"',
  '”' to '"',
)

// unused in script and repurposed
const val scriptZeroWidthSpace = '@'
const val scriptFragmentTextMarker = '|'
const val scriptLogHintMarker = '$'

fun applyScriptReplacements(text: String): String {
  var text = text
  scriptRemappedCharacters.forEach { (from, to) ->
    text = text.replace(from, to)
  }
  return text
}
