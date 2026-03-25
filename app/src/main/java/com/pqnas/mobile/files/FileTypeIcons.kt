package com.pqnas.mobile.files

import com.pqnas.mobile.api.FileItemDto
import java.util.Locale

object FileTypeIcons {
    private val available = setOf(
        "7z","aac","apk","avi","bak","bash","bmp","bz2","c","cc","cfg","conf","cpp","crt",
        "css","csv","cxx","db","deb","default","dll","dmg","doc","docx","exe","flac",
        "folder","generic_archive","generic_audio","generic_code","generic_database",
        "generic_document","generic_file","generic_image","generic_presentation",
        "generic_spreadsheet","generic_video","gif","go","gz","h","heic","hh","hpp",
        "html","htm","hxx","ico","img","ini","iso","java","jpeg","jpg","js","json",
        "jsx","key","kt","lock","log","lua","m4a","m4v","md","mjs","mkv","mov","mp3",
        "mp4","msi","odp","ods","odt","ogg","otf","pdf","pem","php","png","ppt","pptx",
        "ps1","py","r","r.tff","rar","rb","rpm","rs","scss","sh","so","sqlite","sql",
        "svg","swift","tar","tgz","tif","tiff","tmp","toml","ts","tsx","ttf","txt",
        "tsv","wav","webm","webp","woff","woff2","xls","xlsx","xml","xz","yaml","yml",
        "zip","zsh"
    )

    fun iconFor(item: FileItemDto): String {
        if (item.type == "dir") return "folder"

        val ext = item.name
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)

        if (ext.isBlank()) return "generic_file"
        if (available.contains(ext)) return ext

        return when (ext) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "heic", "tif", "tiff" -> "generic_image"
            "mp4", "mkv", "mov", "avi", "webm", "m4v" -> "generic_video"
            "mp3", "wav", "ogg", "flac", "aac", "m4a" -> "generic_audio"
            "zip", "7z", "rar", "tar", "gz", "tgz", "xz", "bz2" -> "generic_archive"
            "txt", "md", "doc", "docx", "pdf", "odt" -> "generic_document"
            "xls", "xlsx", "csv", "tsv", "ods" -> "generic_spreadsheet"
            "ppt", "pptx", "odp", "key" -> "generic_presentation"
            "db", "sqlite", "sql" -> "generic_database"
            "c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx",
            "kt", "java", "js", "mjs", "jsx", "ts", "tsx",
            "py", "rb", "rs", "go", "php", "sh", "bash", "zsh",
            "lua", "swift", "scss", "css", "html", "htm", "xml",
            "json", "toml", "yaml", "yml", "conf", "cfg", "ini", "log" -> "generic_code"
            else -> "generic_file"
        }
    }

    fun assetPathFor(item: FileItemDto): String {
        return "filetypes/${iconFor(item)}.svg"
    }
}