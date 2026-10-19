package tl.umineko.patcher

enum class ControlCode(val replacement: String) {
  // order is important
  // note: replacements can't be changed without also changing patch.cpp checks
  CR("α"),
  KW("β"),
  RB("γ"),
  RS("δ"),
  RE("ε"),
  WT("ζ"),
  EV("η"),
  SP("θ"),
  SF("ι"),
  VC("κ"),
  ZM("λ"),
  LS("μ"),
  LE("ν"),
  KS("ξ"),
  KE("ο"),
  CO("π"),
  T0("ρ"),
  ZH("σ"),
  ZZ("τ"),
  // ...υφχψω
}
