# docs/

Planning documents an agent reads before touching code. Copy this folder into a
project root, fill the placeholders, then point a session at the whole folder:

```
Read PROFILE.md, PLAN.md and everything in @docs/ before you start.
```

Referencing the folder beats pasting four documents into a chat box. The files
stay in the repo, they get reviewed in diffs, and every future session reads the
same version.

## What lives where

PROFILE.md at the repo root is canonical. Nothing in this folder may contradict
it. When they disagree, PROFILE.md wins and the doc here gets fixed.

| Question | File |
|---|---|
| What is it, who is it for, what is NOT in v1 | `PROFILE.md` (root) |
| Sacred Rules, stack, data model, keys | `PROFILE.md` (root) |
| What gets built, in what order | `PLAN.md` (root) |
| How a user moves through the app | `docs/app-flow.md` |
| What it looks like and why | `docs/ui-guidelines.md` |
| What must be true before it goes public | `docs/security-checklist.md` |

There is deliberately no `PRD.md` here. That is PROFILE.md sections 1–5. There
is deliberately no `tech-stack.md`. That is PROFILE.md section 7. Two files
describing the same decision is how a decision gets quietly reversed.

## Copying it into a project

```powershell
Copy-Item -Recurse "C:\Users\USER\.claude\skills\new-project\templates\docs" "<project>\docs"
```

Then delete any file you are not going to fill in. An empty template is worse
than a missing one, because an agent will read it and treat the placeholders as
requirements.

## Filling them in

`app-flow.md` and `ui-guidelines.md` both start with pen and paper. Sketch the
flow, sketch the screens, then write the doc from the sketch. Describing a screen
you have already drawn is a different job from inventing one in prose, and the
result reads differently.

`security-checklist.md` is not a template. It ships filled in and gets ticked
off, once before the first public deploy and again before launch.
