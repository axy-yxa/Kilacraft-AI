# Kilacraft-AI - Intent Recognition Prompt Configuration Guide

> **Last Updated**: 2026-06-15  
> **Corresponding Version**: v2.1.1 (Two-Phase Intent Recognition Architecture)  
> **Description**: This document details the two-phase intent recognition architecture and how to configure intent recognition prompts via `intent_prompts.yml` to guide the LLM in understanding user input and recognizing skill intents.

---

## 📖 Overview

Kilacraft-AI's intent recognition uses a **two-phase architecture (Phase 1 + Phase 2)**, splitting the original "single full skill list to LLM" into two lightweight calls. This significantly reduces Token consumption while maintaining recognition accuracy.

```
User Input
   │
   ▼
┌─────────────────────────────────────────┐
│  Phase 1 (Coarse Selection)              │
│  Sends only one line of name + desc      │
│  LLM determines which skill categories   │
└─────────────────────────────────────────┘
   │
   ├── skill_names = null (pure small talk) ──▶ go directly to normal AI conversation (no Phase 2)
   │
   ▼ skill_names = ["SkillA", "SkillB", ...]
┌─────────────────────────────────────────┐
│  Phase 2 (Precise Selection)             │
│  Sends full details only for matched     │
│  skills (actions + hints)                │
│  LLM selects action + extracts params    │
└─────────────────────────────────────────┘
   │
   ▼
Single Intent / Multi-Step Task / Invalid Intent
```

**Why two phases?** Before the refactor, every recognition stuffed the full details of all skills (including BukkitAPI's 77 action definitions) into the prompt at once. After the refactor, pure small talk only goes through one ultra-lightweight Phase 1, and skill scenarios only carry the full details of matched skills. See [📊 Performance Evaluation](#-performance-evaluation).

---

## 📁 File Location

```
plugins/Kilacraft-AI/
├── intent_prompts.yml          # Intent recognition prompts (Chinese mode)
├── intent_prompts_en.yml       # Intent recognition prompts (English mode, used when language: en)
├── llm.yml                     # LLM API config (model, temperature, API key, etc.)
├── config.yml                  # Main config (language settings.language, debug mode, etc.)
└── skills/                     # Skill descriptions and action definitions
    ├── afktask/AFKTaskSkill.yml
    ├── bukkit/apis.yml         # BukkitAPI's 77 action definitions
    └── ...
```

On first startup the plugin copies default configs from the JAR. The prompt file is auto-selected by current language (`zh` → `intent_prompts.yml`, `en` → `intent_prompts_en.yml`). After editing, hot-reload with `/kila reload` — no restart needed.

---

## 🏗️ Two-Phase Architecture Details

### Phase 1 Prompt Composition

Phase 1 is assembled by `IntentPromptConfigManager.buildPhase1SystemPrompt()` and is **ultra-lightweight**:

```
[phase1.role_definition]          ← coarse-selection positioning (favor recall) + matching criteria
[Available Skills]
  1. SkillA - one-line description
  2. SkillB - one-line description
  ... (each skill only name + description, no actions/hints)
[phase1.output_format]            ← {"skill_names": [...]} or null
```

Phase 1's goal is **classification** — it does not need to select specific actions or extract parameters.

### Phase 2 Prompt Composition

Phase 2 is assembled by `IntentPromptConfigManager.buildSystemPrompt()` and contains the full decision and constraint rules, but the **skill list only includes the full details of skills matched in Phase 1**:

```
[Role Definition] role_definition (includes "Three Inviolable Rules")
[Available Skills] matched skills' name + description + all actions + all hints (+ optional dynamic context)
[Response Format Spec] single_intent / invalid_intent / multi_step_task (3 JSON templates)
[output_format_rules]              ← JSON output requirements
[Decision Rules] when_use_single_intent / when_use_multi_step / when_return_invalid
[Critical Rules] player_security / placeholder_usage / continuous_conversation / entity_format / multi_step_mandatory
[Special Scenarios] conflicting_intents / missing_parameters / skill_name_restriction
[afk_task_rules]                   ← ★ Conditionally injected: only when Phase 1 matches AFKTask
[Output Quality Requirements] output_quality_requirements
```

> **Conditional injection of afk_task_rules**: AFK task rules are long and only needed in AFK scenarios. During Phase 2 assembly, the code checks `selectedSkills.contains("AFKTask")` and only injects `afk_task_rules` when matched, avoiding Token waste in normal scenarios.

> **Skill-level dynamic context injection**: Beyond the yml-section conditional injection above, there is a **skill-level dynamic content entry point**. When assembling each matched skill's info in Phase 2, the code checks `instanceof DynamicContextProvider` — skills implementing this interface may append a runtime-assembled dynamic block (typical case: WatchSkill injects the available probe list, which is defined in Java code and cannot be written into static yml). This is an **internal enhancement interface** (`skills/framework/DynamicContextProvider`), not included in the SPI jar; third parties cannot access it. Built-in skills needing dynamic content simply implement the interface — no change to the `Skill` contract required.

---

## 📋 Config Structure Overview

`intent_prompts.yml` has **9 top-level sections** (8 for Phase 2 + 1 dedicated to Phase 1):

| # | Section | Sub-items | Purpose | Used by |
|---|---------|-----------|---------|---------|
| 1 | `role_definition` | — | Phase 2 role definition + Three Inviolable Rules | Phase 2 |
| 2 | `response_format` | `single_intent` / `invalid_intent` / `multi_step_task` | Three JSON response templates | Phase 2 |
| 3 | `output_format_rules` | — | JSON output requirements + "use multi-step when uncertain" | Phase 2 |
| 4 | `decision_rules` | `when_use_single_intent` / `when_use_multi_step` / `when_return_invalid` | Single/multi-step/invalid decision rules | Phase 2 |
| 5 | `critical_rules` | `player_security` / `placeholder_usage` / `continuous_conversation` / `entity_format` / `multi_step_mandatory` | 5 critical constraints (incl. arithmetic placeholder rules) | Phase 2 |
| 6 | `special_scenarios` | `conflicting_intents` / `missing_parameters` / `skill_name_restriction` | Conflict handling, missing params, skill name restriction | Phase 2 |
| 7 | `output_quality_requirements` | — | Output quality + confidence calibration + quick decision cheat sheet | Phase 2 |
| 8 | `afk_task_rules` | — | AFK task decision rules (**conditionally injected**) | Phase 2 (only when AFKTask matched) |
| 9 | `phase1` | `role_definition` / `output_format` | Phase 1 coarse-selection positioning and output format | Phase 1 |

---

## 🔧 Config Sections Detail

### 1. `role_definition` (Role Definition)

Defines the Phase 2 intent recognition assistant's identity, core responsibilities, and the most important "Three Inviolable Rules".

```yaml
role_definition: |
  You are an intelligent skill intent recognition assistant, analyzing the user's natural language input...
  You are processing Phase 2 (precise recognition). The [Available Skills] below was produced by Phase 1's coarse selection...

  [Three Inviolable Rules — priority above every other rule in this prompt]
  1. Never fabricate skill/action names: only use names that exist in [Available Skills]
  2. Never return null required params: when missing, switch to multi-step to query first, never set null
  3. Never force-match: the skill/action description must directly serve the user's intent
```

**Note**: This is the intent recognition assistant's role, not an in-game NPC personality (personality system is configured separately via `personalities.yml`).

### 2. `response_format` (Response Format Spec)

Defines three standard JSON templates:

```yaml
response_format:
  single_intent: |        # Single intent (simple task)
    {"skill_name": "...", "action": "...", "entities": {...}, "confidence": 0.95, "reasoning": "..."}
  invalid_intent: |       # Invalid intent (small talk / no match)
    {"skill_name": null, "action": null, "entities": {}, "confidence": 0.0, "reasoning": "..."}
  multi_step_task: |      # Multi-step task (complex task)
    {"goal": "...", "steps": [{"id": "step_0", "skill_name": "...", "action": "...", "entities": {...}, "depends_on": []}], "reasoning": "..."}
```

### 3. `output_format_rules` (Output Format Requirements)

```yaml
output_format_rules: |
  [Output Format Requirements]
  JSON forced output mode is enabled...
  2. **Always use multi-step when uncertain.** Structural reason: the multi-step path is not gated by confidence and a missing required param auto-degrades to single intent — it almost never fails; whereas single intent with insufficient confidence is silently demoted to normal conversation...
```

**Key design**: "default to multi-step when uncertain" is the core strategy to reduce intent recognition failure rate — multi-step costs at most one extra step but almost never fails.

### 4. `decision_rules` (Decision Rules)

Three sub-items define when to use single intent, multi-step, or invalid. See [🎯 Core Rules](#-core-rules).

### 5. `critical_rules` (Critical Rules)

Five critical constraints, where `multi_step_mandatory` contains the full multi-step decision tree and arithmetic placeholder rules (the `entity_format` "item:quantity" format is also in this section).

### 6. `special_scenarios` (Special Scenarios)

- `conflicting_intents`: conflict handling (how to choose when multiple skills/actions match)
- `missing_parameters`: parameter missing handling (**query-then-act**, with detailed arithmetic placeholder rules)
- `skill_name_restriction`: strict skill name restriction (anti-hallucination)

### 7. `output_quality_requirements` (Output Quality Requirements)

Includes reasoning conciseness, dependency validation, `delay_wait`/`notify_player` orchestration rules, **confidence calibration** (single intent must explicitly include `confidence`; < 0.5 is silently demoted), and the "quick decision cheat sheet" at the end.

### 8. `afk_task_rules` (AFK Task Rules, Conditionally Injected)

**Only injected when Phase 1 matches AFKTask.** Complements `skills/afktask/AFKTaskSkill.yml`'s `hints`: `hints` provides parameter-level guidance, this section provides decision-level guidance (Event vs CUSTOM type selection, callback rules, one-task-per-player limit).

> Global rule sections (1-7, 9) stay generic and abstract, **never referencing concrete skill or action names**; only this section (`afk_task_rules`) may reference the AFKTask skill's real name, as it is that skill's dedicated contract.

### 9. `phase1` (Phase 1 Config)

```yaml
phase1:
  role_definition: |      # Coarse-selection positioning: favor recall + core matching criteria + strict skill name restriction
  output_format: |        # {"skill_names": [...]} or null
```

Phase 1's `role_definition` emphasizes "favor recall over precision" — during coarse selection it's better to include a possibly-relevant skill than to miss one; Phase 2 will refine afterward.

---

## 🎯 Core Rules

### "Three Inviolable Rules" (Highest Priority)

Located at the end of `role_definition`, priority above all other rules in the prompt:

1. **Never fabricate skill/action names** — only use names that exist in [Available Skills]
2. **Never return null required params** — when missing, switch to multi-step to query first, never set null
3. **Never force-match** — the description must directly serve the user's intent

### Single Intent vs Multi-Step: Three-Condition Check

`decision_rules.when_use_single_intent` is explicit: use single intent **only when ALL three conditions hold**:

1. The request can be completed by **one action**
2. All required params of that action are **explicitly provided** by the user
3. No dependency on other actions' return values

**Any one not satisfied → must use multi-step.** The most common failure is "required param not provided by the user" — even if obtainable via another query action of the same skill, you must query first then act.

### Arithmetic Placeholders (Derived-Value Params)

Rule 6 of `special_scenarios.missing_parameters` defines arithmetic placeholders, enabling relative needs like "transfer half the balance" or "5 below current":

```
amount / quantity / price (scalar params) and condition_plan.threshold
all support {step_X.field} operator number, single binary operation (+ - * /), auto-evaluated at execution.

Example: transfer one-third of balance → amount="{step_0.balance}/3"
         entire balance             → amount="{step_0.balance}"
         10% off                    → amount="{step_0.price}*0.9"
```

**Limitations**: single binary operation only — no compound (e.g. `/2+100`), parentheses, or percent signs. If a ratio cannot be expressed as a single operation, query first, inform the user, and ask for a concrete value.

### Parameter Missing Handling: Query-Then-Act

The core principle of `missing_parameters`: **prefer one extra step to fetch a param, never return a null param.**

```json
// ✅ Correct: cross-skill query-then-act
{"goal":"Query A data then perform B based on result",
 "steps":[
   {"id":"step_0","skill_name":"SkillA","action":"query_action","entities":{},"depends_on":[]},
   {"id":"step_1","skill_name":"SkillB","action":"exec_action","entities":{"param":"{step_0.return_field}"},"depends_on":["step_0"]}
 ]}

// ❌ Wrong: single intent + null param (causes execution failure)
{"skill_name":"Skill","action":"exec_action","entities":{"required_param":null}}
```

---

## 📊 Performance Evaluation

This section estimates the Token savings of the two-phase architecture vs the pre-refactor (single-phase full) approach using **real prompt character counts**. All data is measured from the current built-in skill configs; the estimation basis is consistent and reproducible.

### Estimation Method

- **Data source**: real character counts of `intent_prompts.yml` and the `skills/` directory (`wc -m` measured, comments and blank lines excluded)
- **Pre-refactor baseline**: code confirms the pre-refactor (single-phase) iterates over **all** skills sending full `actions + hints`, line-for-line identical to the current Phase 2 logic, only with scope "all" instead of "matched" — so the comparison basis is consistent
- **Token conversion**: intent recognition prompts are Chinese-dominated mixed content; empirically ~**0.6 token/char** (Chinese is higher, English/symbols lower, mixed takes the median). **Savings ratios are based on character counts, independent of the token coefficient, and most robust**

### Three-Scenario Comparison

| Scenario | Prompt composition | Chars | vs baseline |
|----------|-------------------|-------|-------------|
| **Pre-refactor baseline** (single-phase, per recognition) | main rules ~26K + all skills full ~80K (incl. BukkitAPI 77 actions ~38K) | **≈ 106K** | — |
| **Pure small talk** | only Phase 1 (returns `null`, no Phase 2) | **≈ 8K** | **↓ ~92%** |
| **Normal skill** (1 built-in skill hit, median full size ~3.1K chars) | Phase 1 + Phase 2 | **≈ 38K** | **↓ ~65%** |
| **Complex skill** (hit incl. BukkitAPI 77 actions) | Phase 1 + Phase 2 | **≈ 73K** | **↓ ~32%** |

**Token conversion** (~0.6 token/char): baseline ≈ 64K → three scenarios ≈ 4.8K / 22.6K / 43.5K

### Data Basis

- Built-in skills total **12** (11 Skill yml + GenericBukkitAPI)
- BukkitAPI (`apis.yml`) has **77 action definitions**, full size about **38K chars** — the bulk of the full prompt
- Phase 1 sends only one line of `name + description` per skill; 12 skills total about **5K chars**
- Normal skill full-size median taken as **3.1K chars** (ServerHealthSkill)

### Estimation Limitations

- **Characters ≠ tokens**: actual token counts vary by tokenizer (DeepSeek/GLM, etc.). For precise values, count the actual prompt with the target model's tokenizer and replace the table
- **Excludes conversation history**: intent recognition also appends history (`intent_history_count`, default 5 rounds), but the history part is identical before and after refactor, so it does not affect savings ratios
- **Response speed cannot be statically estimated**: two-phase adds one ultra-lightweight Phase 1 call, but each recognition no longer carries the full skill list; net latency depends on model and network and requires real measurement

### How to Reproduce the Estimation

```bash
# Full skill scale (pre-refactor skill list)
cd plugins/Kilacraft-AI/skills
grep -vE '^\s*#|^\s*$' bukkit/apis.yml | wc -m          # BukkitAPI 77 actions
find . -name "*.yml" ! -name "*_en.yml" ! -name "apis.yml" -exec cat {} \; | grep -vE '^\s*#|^\s*$' | wc -m

# Prompt rule section scale
grep -vE '^\s*#|^\s*$' intent_prompts.yml | wc -m
```

---

## 🛠️ Usage

### Hot Reload

After editing `intent_prompts.yml`, no restart needed:

```
/kila reload
```

The log will output "Intent recognition prompt config reload complete".

### Language Switching

- `settings.language: zh` in `config.yml` → uses `intent_prompts.yml`
- `settings.language: en` → uses `intent_prompts_en.yml`

In English mode, `intent_prompts_en.yml` is auto-generated on first startup.

### Customizing Prompts

All prompt sections can be freely modified. **But note**:

- The **key names** of config items (e.g. `role_definition`, `decision_rules.when_use_single_intent`) cannot be changed — the code reads them by fixed paths
- The **values** (prompt text) can be adjusted freely
- Deleting a section makes it fall back to the code's built-in default, but may degrade recognition quality

---

## 💡 Optimization Strategies

- **Keep rules abstract**: do not hardcode specific skill names in global rule sections, or prompts break when skills are added/removed
- **Leverage conditional injection**: long rules specific to a skill category (like `afk_task_rules`) should go in a conditionally-injected section, not a global one
- **Confidence calibration**: single intent must carry `confidence` ≥ 0.8, otherwise it's silently demoted to normal conversation; when uncertain, just use multi-step
- **Prefer arithmetic placeholders**: relative-value needs (half, one-third, 10% off) should use arithmetic placeholders rather than letting the LLM fabricate concrete values

---

## 🧪 Testing & Debugging

- **DEBUG logs**: with debug mode on, the console outputs Phase 1 classification results, Phase 2 selected skills and actions, and the parsing process
- **Exception layering**: Phase 1/Phase 2 JSON parse exceptions are logged at DEBUG, unexpected exceptions at WARN — a WARN means the LLM returned an unexpected format
- **Null skill name diagnostics**: Phase 2 distinguishes "skill_name not returned" from "returned invalid skill_name", logging each separately

---

## 📝 Best Practices

1. **Back up before editing**: back up `intent_prompts.yml` before modifying
2. **Small steps**: change one section at a time, observe recognition with DEBUG logs
3. **Keep the Three Inviolable Rules**: the three rules at the end of `role_definition` are the baseline of recognition quality — do not remove them
4. **Don't hardcode skills**: referencing concrete skill names in global sections is a maintenance nightmare

---

## ⚠️ Notes

- Prompts are updated across versions. After upgrading, to get the latest prompt effects, delete the old `intent_prompts.yml` (and `_en.yml`) and restart to regenerate
- All prompt sections have code-level default fallbacks — **the plugin runs normally even if the entire file is deleted** (uses built-in default prompts)
- Third-party SPI skill descriptions are also dynamically injected into [Available Skills], and prompt rules apply to them as well

---

## ❓ FAQ

**Q: Why didn't my `intent_prompts.yml` edit take effect?**  
A: Confirm you ran `/kila reload`; confirm `settings.language` matches the file suffix (Chinese uses `intent_prompts.yml`, English uses `_en.yml`).

**Q: How to improve intent recognition accuracy?**  
A: Prioritize optimizing each skill's own `description` (Phase 1 only reads this one line), then adjust `decision_rules`. If accuracy is below 80%, focus on multi-step task recognition.

**Q: What happens if the prompt is too long?**  
A: It increases Token consumption and response time. The two-phase architecture already mitigates this significantly, but single sections should still be kept concise (keep the entire `intent_prompts.yml` rule section at a reasonable scale).

**Q: Can I use only single-phase?**  
A: Two-phase is the core architecture of v2.1.1 and cannot fall back to single-phase. If you worry about Phase 1 mis-filtering, strengthen the "favor recall over precision" principle in `phase1.role_definition`.

---

## 🆚 vs Personality System

| | Intent Recognition Prompts | Personality System |
|---|---|---|
| Config file | `intent_prompts.yml` | `personalities.yml` |
| Purpose | Guides LLM to recognize skill intents, select actions, extract params | Defines AI's tone, style, persona in normal conversation |
| Effective stage | Intent recognition stage | Normal conversation reply stage |
| Independent | The intent recognition assistant is not an in-game NPC personality | Personality does not affect skill intent recognition |

---

## 🔗 Related Files

- `llm.yml` — LLM API config (model, temperature, API key, max_tokens)
- `config.yml` — Main config (language, debug mode)
- `skills/` — Each skill's description, actions, hints
- `skills/bukkit/apis.yml` — BukkitAPI's 77 action definitions
- Changelog — Version change records

---

## 📞 Support & Feedback

For prompt configuration issues:
- Check the console DEBUG logs to locate the problem
- Refer to the FAQ section
- Contact developers via the Discord community in the README
