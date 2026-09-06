# Codex composer + DSH model-switch parity

## Problem Statement

Switching a DSH model while the previous thinking effort is still selected fails the whole switch: the Host returns `session/model-unavailable` because the old effort is not advertised on the new model. The user has to keep the old model or manually drop High first.

Codex chat already receives `item/tool/requestUserInput`, but the card is a thin option/text form: no DSH-style single-select instant submit, no durable draft, no multi-question progress, and pending requests occupy a one-at-a-time scroll instead of the native response dock. The composer model/effort controls are full-width dropdowns instead of the compact chips used by DSH/OpenCode.

## Solution

When the operator picks a DSH model, keep the current thinking effort only if the new model advertises it; otherwise send that model's default effort (or omit effort when the model has none). The switch succeeds and the composer shows the effective effort.

Codex questions use the same card grammar as DSH: header, option descriptions, single-select tap-to-submit for a lone question, explicit submit for batches/custom answers, and unlock on a rejected reply. Approvals and questions sit in the shared response dock above the composer. Model and reasoning effort become compact chips in the native composer control row.

## User Stories

1. As an operator on a DSH session, I want switching from GLM High to Grok to succeed, so that I am not blocked by an effort the new model does not support.
2. As an operator switching DSH models, I want a still-supported effort to stay selected, so that a compatible High is not silently dropped.
3. As an operator switching to a model with no thinking levels, I want the effort chip to disappear and the request to omit effort, so that the Host does not reject the switch.
4. As an operator whose DSH model switch fails for another reason, I want the previous selection restored with an error, so that a catalog or network failure is retryable.
5. As an operator answering a Codex `requestUserInput` prompt, I want DSH-like option cards, so that I can pick a labeled choice without typing it.
6. As an operator answering a single-select Codex question, I want tapping one option to submit immediately, so that I do not need a second Submit tap.
7. As an operator answering several Codex questions or a custom/other answer, I want an explicit Submit, so that a partial batch is not sent.
8. As an operator whose Codex answer is rejected, I want the card unlocked with the error, so that I can retry the same request.
9. As an operator with more than one pending Codex request, I want every request discoverable in the response dock, so that later questions are not hidden behind the first card.
10. As an operator writing in Codex chat, I want compact model and effort chips in the native composer row, so that the controls match DSH density instead of full-width menus.

## Implementation Decisions

- Named product is this mobile behavior only. Transports stay independent: DSH uses `session/selectModel`; Codex keeps app-server JSON-RPC. Do not route Codex questions through DSH `$events/result`.
- DSH model switch computes a compatible effort before the RPC. Compatibility is: current effort is in the selected model's advertised efforts; otherwise the model's `defaultEffort`; otherwise omit `reasoningEffort`. Catalog membership remains advisory for the route itself; only the effort parameter is rewritten.
- Preserve the existing DSH receipt rules: a late or disconnected generation cannot apply; Host receipt remains authoritative after a successful call.
- Store each DSH model's default effort beside the existing variant map so the composer can resolve a fallback without a second catalog RPC.
- Codex `item/tool/requestUserInput` remains the question tool. Parse unknown extra fields without dropping the request. If a future payload carries a multi-select flag, honor it; otherwise Codex questions stay single-select plus `isOther` custom text.
- Codex answer wire shape stays `{ answers: { <id>: { answers: [<labels or custom text>] } } }`. Do not put custom text in a DSH `custom` field on the Codex socket.
- Reuse `ChatResponseDock` for Codex pending approvals and questions. Show every pending request, not only the first. Keep existing reply locking and generation validation.
- Extract the DSH question card into a shared composable only if that extraction is smaller than duplicating the interaction grammar. Do not refactor the 8k-line Chat screen beyond the card and dock wiring this product needs.
- Codex composer chips follow the native DSH/OpenCode control row: compact labels, horizontal overflow, paperclip stays pinned. Model and effort pickers may remain dropdowns behind those chips. Do not restyle the timeline, session list, or status sheet.
- Secret Codex questions keep password masking. Auto-resolution remaining time is optional; do not block answering on it.

## Testing Decisions

- Test observable selection and wire payloads, not Compose internals.
- DSH: a model whose efforts exclude the current High is selected with that model's default (or omitted effort); a model that still advertises High keeps High; a model with no reasoning omits effort; an RPC failure leaves the previous selection.
- Codex: parse a `requestUserInput` with header, options, `isOther`, and `isSecret`; a single-option answer submits the labeled list; a custom/other answer submits the typed text as that question's answers list; a rejected reply unlocks the same request id.
- Prior art: `DshModelSelectionProjectionTest`, `CodexAppServerClientTest` user-input reply shape, `ChatResponseDockTest`, `CodexChatInteractionTest`.

## Out of Scope

- Codex session list, timeline grammar, plan/diff/status sheet, and child-thread navigation (explicitly deferred this round).
- Changing DSH Host `selectModel` to auto-fallback; the mobile client rewrites the request.
- OpenCode model switching.

## Further Notes

- Delivery baseline: repo `/root/CODE/oc-remote`, HEAD `217e23f27140061618e3182df573396f98ba1a38`, pre-existing untracked `.agent-teams/`, `.dsh-filess/`, `.opencode/goals/`, `.pi-subagents/`. Private capture: `/flyshop/dev/tmp/codecarry-codex-dsh-parity-baseline`.
- Current app metadata is `1.13.2` / `122`; bump only if this delivery ships a release. Manual GitHub Release remains operator-owned.
- Codex protocol evidence on this host is client-kit `0.144.3`; `requestUserInput` has `isOther`/`isSecret` and no required multi-select field. Preserve unknown extras.
