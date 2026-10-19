This directory contains source code of the tools used to make the English translation patches for Umineko Portable.
If you just want to download the patch look at the main [README](https://github.com/kotcrab/umineko-portable-english)
instead.

The tools are not packaged into a single application, instead they are intended to be run directly from the IDE. Don't
be afraid to jump into the source code and make any changes as necessary. However, note that this code is provided with
no additional support whatsoever and this README only describes initial requirements and how to open the project.

This repository is licensed under the GPLv3 license, this license only applies to files in this repository and not to
any other patch components or resources.

## Prerequisites

Required tools:

- JDK
    - Any modern version should work, to avoid issues just install
      [Temurin 21 x64](https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64&package=jdk).
- IntelliJ IDEA
    - Free community version is fine.
- Homebrew PSP SDK
    - The patch was build with Minimalist PSPSDK 0.11.2r3
        - [Download](https://sourceforge.net/projects/minpspw/files/SDK%20%2B%20devpak/pspsdk%200.11.2/)
          from SourceForge, [mirror](https://mega.nz/file/DZs3mbgK#H6bGiR6GIR2BZ5nuCWuY-UQRm49ocuh3CnRI3-I8I50).
        - The installer should have SHA256 hash `93B08FABA72D47C6F91AC72E8361D05F96257D2456EB402096EF7BC32193D662`.
        - Don't change default install location.
    - Modern PSP SDK will probably work too.
- PPSSPP

## Environment variables setup

You need to set up few environment variables:

- `FE_PROJECT_BASE` - empty directory you created somewhere for all the project files.
- `PSPSDK_HOME` - should be `C:\pspsdk`.
- `PPSSPP_HOME` - should be PPSSPP installation directory.

## Building tools source code

Clone `tranlsation-tools` into this directory:

```
git clone https://github.com/ccc-tl/translation-tools.git
cd translation-tools
git checkout 355a765e0aaa7f8abaaca4993d71eca4d56f0ab8
```

Then open `umineko/build.gradle` in IntelliJ IDEA and wait for the project to load.
You should now be able to build the project and launch tools from the `cli` package, though to actually use them you
will need to add project files to the directories in `$FE_PROJECT_BASE\UminekoPortable` (see `UminekoPaths.kt`).

The main patch working directory is `$FE_PROJECT_BASE\UminekoPortable\_Patch`. A tree listing of it is provided for
reference in the `toolkit-tree.txt` file.
