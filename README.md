# Umineko Portable - English Patch

English translation patch for the Umineko Portable games released on the PSP.

There are two Umineko Portable games. The first one contains episodes 1 and 2, and the second one contains episodes 3
and 4. Currently, the patch is available for the first game. It is 100% complete with everything translated and is fully
playable on PSP and PS Vita. Patch for the second game is planned to be released later this year. This patch uses
translated script from the Umineko Project.

## Notes

- Playing on PPSSPP: **currently PPSSPP has a bug that causes text corruption**.
    - Until it's fixed in the next PPSSPP release you should instead play on PSP or PS Vita.
    - If the text corruption happens you can quickly clear it by saving and loading savestate (F2 and F4 hotkeys).
- The game has 3 different fonts you can choose from, as well as customizable outline and shadow. Try them if you don't
  like the default font look.
- Japanese saves can't be loaded on the English patched version and vice versa (this also applies to the "Continue"
  option in the main menu). However, system data (settings and overall progression) can be shared between both.
    - If you want to change the language you need to start the episode again and use scene jump in the backlog to resume
      from your last point.

## Applying the Patch

First make a dump of your own copy of Umineko Portable 1 in ISO format.

### Method 1 - web patcher

This method works on any OS and on phones.

1. Download [release ZIP](https://github.com/kotcrab/umineko-portable-english/releases/latest) and extract it
2. Go to the [xdelta online patcher](https://kotcrab.github.io/xdelta-wasm/)
3. In the "Source file" select your ISO file
4. In the "Patch file" select `patch1.xdelta` file from the extracted ZIP `data` folder
5. Press "Apply Patch" and wait for patching to complete
6. Your browser will ask you where to save the ISO, or it will save it in your Downloads folder

### Method 2 - native patcher

This method is for Windows only.

1. Download [release ZIP](https://github.com/kotcrab/umineko-portable-english/releases/latest) and extract it
2. Drag and drop your ISO file onto the `.bat` file in the extracted folder
3. Wait for patching to complete
4. The patched ISO will be saved next to the `.bat` file

## ISO Info

Umineko Portable 1:

```
Name: ULJM05968.ISO
Size: 1183383552 bytes (1128 MiB)
CRC32: 4B81E5A9
SHA256: 05A3696776D314AC40D55F3B125DC3EC2E60AD12FEC12DE4995A4E4E1C9E17EA
SHA1: 5819B5CFA90065C63605E544D3BE0262AF3ACC99
```

## Credits

- [Umineko Project](https://www.umineko-project.org/) - this patch uses translations and many of the edited images from
  Umineko Project and wouldn't be possible without it
- Kotcrab - PSP hacking, UI image editing
- SnowyAria - UI translation
- ItsumoKnight - trailer translation helper

## Support

If the game is crashing or not starting at all (PSP is turning off):

- check your CFW Recovery settings and make sure "Force High Memory Layout" or "Use Extra Memory" (depends on CFW) is
  disabled.
- try disabling any plugins you might have.
- check if the ISO was correctly patched, for v1.0 CRC32 after patching should be `7CB7E758` or try running ISO in the
  emulator.

You can join our Discord and ping Kotcrab if you have any other issues or questions:

- https://discord.gg slash YbbqZ67dhQ
