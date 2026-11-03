package com.alex.aceshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileKindsTest {
    @Test
    fun groupFiles_usesWebCategoryOrder() {
        val files = listOf(
            "安装包.exe",
            "备份.zip",
            "说明.txt",
            "表格.xlsx",
            "配乐.wav",
            "clip.mkv",
            "demo.mp4",
            "chameleon_8277446.svg",
            "配置.json",
            "default.lcf",
            "My Account _ Billing.pdf",
        )

        val groups = FileKinds.groupFiles(files)

        assertEquals(
            listOf(
                FileCategory.Image,
                FileCategory.Video,
                FileCategory.Audio,
                FileCategory.Document,
                FileCategory.Text,
                FileCategory.Archive,
                FileCategory.Other,
            ),
            groups.map { it.category },
        )
        assertEquals(listOf("chameleon_8277446.svg"), groups[0].files)
        assertEquals(listOf("clip.mkv", "demo.mp4"), groups[1].files)
        assertEquals(listOf("安装包.exe", "default.lcf"), groups[6].files)
    }

    @Test
    fun previewOf_matchesWebPreviewableTypes() {
        assertEquals(PreviewKind.Image, FileKinds.previewOf("IMG_6515.PNG"))
        assertEquals(PreviewKind.Image, FileKinds.previewOf("chameleon_8277446.svg"))
        assertNull(FileKinds.previewOf("photo.heic"))
        assertEquals(FileCategory.Image, FileKinds.categoryOf("photo.heic"))

        assertEquals(PreviewKind.Video, FileKinds.previewOf("demo.mp4"))
        assertNull(FileKinds.previewOf("clip.mkv"))
        assertEquals(FileCategory.Video, FileKinds.categoryOf("clip.mkv"))

        assertEquals(PreviewKind.Audio, FileKinds.previewOf("语音.mp3"))
        assertEquals(PreviewKind.Audio, FileKinds.previewOf("配乐.WAV"))
        assertNull(FileKinds.previewOf("old.wma"))

        assertEquals(PreviewKind.Pdf, FileKinds.previewOf("说明书.pdf"))
        assertNull(FileKinds.previewOf("表格.xlsx"))
        assertEquals(FileCategory.Document, FileKinds.categoryOf("表格.xlsx"))

        assertEquals(PreviewKind.Text, FileKinds.previewOf("配置.json"))
        assertEquals(PreviewKind.Text, FileKinds.previewOf("说明.TXT"))
        assertNull(FileKinds.previewOf("备份.zip"))
        assertEquals(FileCategory.Archive, FileKinds.categoryOf("备份.tar.gz"))
        assertNull(FileKinds.previewOf("readme"))
        assertEquals(FileCategory.Other, FileKinds.categoryOf("readme"))
    }

    @Test
    fun groupFiles_omitsEmptyCategories() {
        assertEquals(emptyList<FileGroup>(), FileKinds.groupFiles(emptyList()))
        assertEquals(
            listOf(FileCategory.Text),
            FileKinds.groupFiles(listOf("a.txt", "b.md")).map { it.category },
        )
    }
}
