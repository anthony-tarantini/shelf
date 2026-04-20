package io.tarantini.shelf.processing.audiobook

import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun ffprobe(path: Path) =
    ProcessBuilder(
        "ffprobe",
        "-v",
        "quiet",
        "-print_format",
        "json",
        "-show_format",
        "-show_streams",
        "-show_chapters",
        path.absolutePathString(),
    )

fun ffmpeg(path: Path, tempCover: Path) =
    ProcessBuilder(
        "ffmpeg",
        "-v",
        "quiet",
        "-y",
        "-i",
        path.absolutePathString(),
        "-map",
        "0:v",
        "-c",
        "copy",
        "-f",
        "image2",
        tempCover.absolutePathString(),
    )
