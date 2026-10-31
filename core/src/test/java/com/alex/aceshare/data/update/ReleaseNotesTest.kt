package com.alex.aceshare.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun parseReleaseNotes_formatsHeadingsAndBullets() {
        val notes = """
            ## 新功能

            - 手机：新增手机、平板和折叠屏界面。窄屏使用底部导航，宽屏使用侧边导航；长文本在宽屏左右分栏。
            - 电视、手机：文件页支持一次选择多个文件上传，并显示进度和结果。

            ## 问题修复

            - 修复发现页在弱网下重复弹窗。
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNoteBlock.Heading("新功能"),
                ReleaseNoteBlock.Bullet("手机：新增手机、平板和折叠屏界面。窄屏使用底部导航，宽屏使用侧边导航；长文本在宽屏左右分栏。"),
                ReleaseNoteBlock.Bullet("电视、手机：文件页支持一次选择多个文件上传，并显示进度和结果。"),
                ReleaseNoteBlock.Heading("问题修复"),
                ReleaseNoteBlock.Bullet("修复发现页在弱网下重复弹窗。"),
            ),
            parseReleaseNotes(notes),
        )
    }

    @Test
    fun parseReleaseNotes_keepsEverySection() {
        val notes = buildString {
            appendLine("## 新功能")
            appendLine()
            repeat(12) { index ->
                appendLine("- 第 ${index + 1} 条更新说明。")
            }
        }

        val bullets = parseReleaseNotes(notes).filterIsInstance<ReleaseNoteBlock.Bullet>()
        assertEquals(12, bullets.size)
        assertEquals("第 12 条更新说明。", bullets.last().text)
    }

    @Test
    fun parseReleaseNotes_omitsVersionHeadingAlreadyShown() {
        val notes = """
            ## v1.2.1

            **Features**

            - 支持多选上传。

            **Bug Fixes**

            - 修复预览被当成下载。
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNoteBlock.Heading("Features"),
                ReleaseNoteBlock.Bullet("支持多选上传。"),
                ReleaseNoteBlock.Heading("Bug Fixes"),
                ReleaseNoteBlock.Bullet("修复预览被当成下载。"),
            ),
            parseReleaseNotes(notes, displayedVersion = "1.2.1"),
        )
    }

    @Test
    fun parseReleaseNotes_stripsInlineMarkdown() {
        assertEquals(
            listOf(ReleaseNoteBlock.Bullet("查看 说明 了解 详情，接口为 GET /api/list。")),
            parseReleaseNotes("- 查看 [说明](https://example.com) 了解 **详情**，接口为 `GET /api/list`。"),
        )
    }

    @Test
    fun parseReleaseNotes_joinsWrappedBulletLines() {
        val notes = """
            - 窄屏使用底部导航，
              宽屏使用侧边导航。
            - Upload files
              from the phone.
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNoteBlock.Bullet("窄屏使用底部导航，宽屏使用侧边导航。"),
                ReleaseNoteBlock.Bullet("Upload files from the phone."),
            ),
            parseReleaseNotes(notes),
        )
    }

    @Test
    fun parseReleaseNotes_skipsRulesCommentsAndFences() {
        val notes = """
            开头说明

            ---

            <!-- hidden -->

            ```
            不展示围栏
            ```

            1. 第一条
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNoteBlock.Paragraph("开头说明"),
                ReleaseNoteBlock.Paragraph("不展示围栏"),
                ReleaseNoteBlock.Bullet("第一条"),
            ),
            parseReleaseNotes(notes),
        )
    }

    @Test
    fun parseReleaseNotes_blankInputIsEmpty() {
        assertEquals(emptyList<ReleaseNoteBlock>(), parseReleaseNotes("  \n\n"))
    }

    @Test
    fun parseReleaseNotes_keepsOnlyTheRequestedPlatform() {
        val notes = """
            两端都会用到的说明。

            ## 电视端

            **Bug Fixes**

            - 遥控器可以翻阅更新说明。

            ## 手机端

            **Features**

            - 发现页底部显示当前版本号。

            **Bug Fixes**

            - 下载进度不再挡住页面。

            ## 通用

            - 修复权限申请后的发现。
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNoteBlock.Paragraph("两端都会用到的说明。"),
                ReleaseNoteBlock.Heading("Bug Fixes"),
                ReleaseNoteBlock.Bullet("遥控器可以翻阅更新说明。"),
                ReleaseNoteBlock.Bullet("修复权限申请后的发现。"),
            ),
            parseReleaseNotes(notes, audience = ReleaseNoteAudience.Tv),
        )
        assertEquals(
            listOf(
                ReleaseNoteBlock.Paragraph("两端都会用到的说明。"),
                ReleaseNoteBlock.Heading("Features"),
                ReleaseNoteBlock.Bullet("发现页底部显示当前版本号。"),
                ReleaseNoteBlock.Heading("Bug Fixes"),
                ReleaseNoteBlock.Bullet("下载进度不再挡住页面。"),
                ReleaseNoteBlock.Bullet("修复权限申请后的发现。"),
            ),
            parseReleaseNotes(notes, audience = ReleaseNoteAudience.Mobile),
        )
    }

    @Test
    fun parseReleaseNotes_showsEverythingWhenNoPlatformHeading() {
        val notes = """
            **Bug Fixes**

            - 修复预览被当成下载。
        """.trimIndent()

        assertEquals(
            parseReleaseNotes(notes),
            parseReleaseNotes(notes, audience = ReleaseNoteAudience.Tv),
        )
    }
}
