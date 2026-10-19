package tl.umineko

import kio.util.child
import tl.projectBase

val uminekoBase = projectBase.child("UminekoPortable")
val uminekoPatch = uminekoBase.child("_Patch")
val uminekoExtract = uminekoBase.child("Extract")
val uminekoTools = uminekoBase.child("Tools")
val uminekoOutput = uminekoBase.child("TMP")
