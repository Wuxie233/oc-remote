# Chat code copy and workspace path preview

## Problem Statement

In Codex (and the shared Markdown renderer used by DSH/OpenCode) fenced and indented code cannot be selected, and there is no one-tap copy. Markdown links and bare workspace paths such as `handoff.txt` or `/flyshop/.../handoff.txt` are not tappable, so the operator must ask the agent to dump file contents as plaintext.

## Solution

Code fences and indented blocks stay selectable and expose a copy control that writes the code to the clipboard. Tapping a Markdown link or a recognized workspace file path opens an in-app preview of that remote file (with copy-path), instead of sending the operator through another agent turn. HTTP(S) links still open in the system browser.

## User Stories

1. As an operator reading a fenced code block, I want to long-press or drag to select part of it, so that I can copy a snippet without asking the agent again.
2. As an operator reading a code block, I want a one-tap copy control, so that I can copy the whole block even when selection is awkward on a phone.
3. As an operator looking at `handoff.txt` or an absolute workspace path in assistant Markdown, I want to tap it and preview the remote file, so that I do not need a follow-up turn that dumps the contents.
4. As an operator tapping an `https://` Markdown link, I want the system browser, so that ordinary web links still work.
5. As an operator whose remote file cannot be read, I want an error and a copy-path action, so that a missing file or unsupported daemon is recoverable.
6. As an operator on DSH or OpenCode chat, I want the same code-copy and link/path behavior, because they share the Markdown renderer.

## Implementation Decisions

- Named product is chat Markdown interaction: selectable code, copy button, tappable links and workspace paths, in-app remote-file preview. Ship as CodeCarry 1.13.3 with a GitHub Release.
- Code fences (non-Mermaid) and indented code currently wrap `DisableSelection`. Keep Mermaid diagrams unselected. Ordinary code becomes selectable and hosts a copy IconButton using existing `chat_copy` / clipboard APIs, matching the Shell card copy affordance.
- Detect tappable targets in assistant Markdown: GFM links, autolinks, backtick-wrapped absolute Unix paths, and bare absolute Unix paths that look like files (`/` plus a filename with an extension, or an explicit `[label](path)`). Relative names like `handoff.txt` resolve against the current Codex thread cwd / DSH-OpenCode session directory.
- HTTP(S) opens through the existing `openMessageLink` system-view intent. `file://` and absolute/relative workspace paths never go to Android's file opener; they request a remote preview.
- Codex preview reads bytes through daemon `fs/readFile` (reuse the existing image reader, generalized to text with a size cap). DSH/OpenCode preview uses the existing `readFileText` / host file APIs already used for mentions. Binary or oversized files show a failure plus copy-path, not a crash.
- Preview is a modal/sheet owned by the chat screen: path, text body with selection, copy-path, copy-contents, dismiss. Do not navigate away from the conversation.
- Compose Markdown currently paints links but only the KaTeX WebView path handles clicks. Wire Compose paragraphs/lists through the same open-target classifier. Do not invent a second Markdown parser.
- Release: bump `versionName` `1.13.3` and increment `versionCode` from 122, add `RELEASE_NOTES_1.13.3.md`, update README current-version notes, then tag `v1.13.3` and trigger the existing manual release workflow.

## Testing Decisions

- Test classifiers and copy payloads, not Compose pointer internals.
- Code copy: a fence's copy action uses the exact inner code (no surrounding fences).
- Path classifier: `https://example.com` is web; `/flyshop/opencode/handoff.txt` and `[handoff.txt](/abs/handoff.txt)` are workspace files; `handoff.txt` relative to a known cwd becomes that absolute path; `not a path` is ignored.
- Preview error: a failed remote read leaves the sheet in an error state with the path still copyable.
- Prior art: `SafeMarkdownHighlightingTest`, `ChatMarkdownRendererTest`, `CodexAppServerClientTest` `fs/readFile`, `MarkdownMessageRendererTest` for `openMessageLink`.

## Out of Scope

- Opening Android-local files or the system file picker for Linux daemon paths.
- Editing remote files from the preview.
- Selecting/copying Mermaid diagrams.
- Changing timeline grammar or tool cards beyond adding copy on Markdown code.

## Further Notes

- Delivery baseline: repo `/root/CODE/oc-remote`, HEAD `a9bc4fdebc3118d447c293866cf309a353f45556`, pre-existing untracked `.agent-teams/`, `.dsh-filess/`, `.opencode/goals/`, `.pi-subagents/`. Private capture: `/flyshop/dev/tmp/codecarry-codeblock-link-baseline`.
- Manual GitHub Release remains operator-owned after tag + workflow_dispatch, per README.
