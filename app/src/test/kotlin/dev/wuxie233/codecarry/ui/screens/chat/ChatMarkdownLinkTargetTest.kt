package dev.wuxie233.codecarry.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownLinkTargetTest {
    @Test
    fun `https and http hrefs are web targets`() {
        assertEquals(
            ChatMarkdownLinkTarget.Web("https://example.com"),
            classifyChatMarkdownLink("https://example.com"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.Web("http://example.com/docs"),
            classifyChatMarkdownLink("http://example.com/docs"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.Web("HTTPS://example.com"),
            classifyChatMarkdownLink("HTTPS://example.com"),
        )
    }

    @Test
    fun `absolute unix file paths are workspace files`() {
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/flyshop/opencode/handoff.txt"),
            classifyChatMarkdownLink("/flyshop/opencode/handoff.txt"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/tmp/handoff"),
            classifyChatMarkdownLink("/tmp/handoff"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/root/CODE/oc-remote/AGENTS.md"),
            classifyChatMarkdownLink("`/root/CODE/oc-remote/AGENTS.md`"),
        )
    }

    @Test
    fun `markdown link href is classified not the label`() {
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/abs/handoff.txt"),
            classifyChatMarkdownLink("/abs/handoff.txt"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.Web("https://opencode.ai"),
            classifyChatMarkdownLink("https://opencode.ai"),
        )
    }

    @Test
    fun `relative name with extension resolves against cwd`() {
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/workspace/handoff.txt"),
            classifyChatMarkdownLink("handoff.txt", cwd = "/workspace"),
        )
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/workspace/notes.md"),
            classifyChatMarkdownLink("notes.md", cwd = "/workspace/"),
        )
        assertNull(classifyChatMarkdownLink("handoff.txt", cwd = null))
        assertNull(classifyChatMarkdownLink("handoff.txt", cwd = "workspace"))
    }

    @Test
    fun `file urls preview as workspace files never as android files`() {
        assertEquals(
            ChatMarkdownLinkTarget.WorkspaceFile("/flyshop/opencode/handoff.txt"),
            classifyChatMarkdownLink("file:///flyshop/opencode/handoff.txt"),
        )
    }

    @Test
    fun `prose and non file tokens are ignored`() {
        assertNull(classifyChatMarkdownLink("not a path"))
        assertNull(classifyChatMarkdownLink("handoff"))
        assertNull(classifyChatMarkdownLink("see notes"))
        assertNull(classifyChatMarkdownLink("javascript:alert(1)"))
        assertNull(classifyChatMarkdownLink("content://media/1"))
        assertNull(classifyChatMarkdownLink("/"))
        assertNull(classifyChatMarkdownLink("../secrets.txt", cwd = "/workspace"))
        assertNull(classifyChatMarkdownLink("sub/dir/handoff.txt", cwd = "/workspace"))
    }

    @Test
    fun `bare path annotator marks workspace files without overlapping markdown urls`() {
        val source = androidx.compose.ui.text.buildAnnotatedString {
            append("See handoff.txt and /flyshop/opencode/handoff.txt then ")
            val start = length
            append("docs")
            addStringAnnotation(
                tag = ChatMarkdownUrlAnnotationTag,
                annotation = "https://example.com",
                start = start,
                end = length,
            )
        }
        val annotated = annotateBareWorkspacePaths(source, cwd = "/workspace")
        val paths = annotated.getStringAnnotations(ChatMarkdownPathAnnotationTag, 0, annotated.length)
        assertEquals(2, paths.size)
        assertTrue(paths.any { it.item == "/workspace/handoff.txt" })
        assertTrue(paths.any { it.item == "/flyshop/opencode/handoff.txt" })
        val urls = annotated.getStringAnnotations(ChatMarkdownUrlAnnotationTag, 0, annotated.length).map { it.item }
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("/workspace/handoff.txt"))
        assertTrue(urls.contains("/flyshop/opencode/handoff.txt"))
    }

    @Test
    fun `bare path node annotator consumes a workspace filename`() {
        val markdown = "See handoff.txt now"
        val document = org.intellij.markdown.parser.MarkdownParser(
            org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor(),
        ).buildMarkdownTreeFromString(markdown)
        val textNode = document.children
            .flatMap { it.children }
            .first { it.type == org.intellij.markdown.MarkdownTokenTypes.TEXT }
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        val consumed = with(builder) {
            annotateBareMarkdownPathNode(
                content = markdown,
                node = textNode,
                cwd = "/workspace",
                linkStyle = androidx.compose.ui.text.TextStyle(),
            )
        }
        val annotated = builder.toAnnotatedString()
        assertTrue(consumed)
        assertEquals(
            listOf("/workspace/handoff.txt"),
            annotated.getStringAnnotations(ChatMarkdownUrlAnnotationTag, 0, annotated.length).map { it.item },
        )
    }
}
