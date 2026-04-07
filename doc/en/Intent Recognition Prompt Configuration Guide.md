# Intent Recognition Prompt Configuration Guide

## 📋 Overview

The intent recognition prompt system defines how the LLM understands user input and identifies skill intents through the `intent_prompts.yml` configuration file.

**Important Notes**: 
- **Intent Recognition System** focuses on task step orchestration and Agent capability Skill selection
- **Personality System** is an independent feature used in plugin command mode, configured via `personalities.yml` with different personality prompts
- Intent recognition prompts do **NOT** include multi-personality configurations; they only are responsible for identify what operations users want to execute

## 📁 File Location

```
plugins/Kilacraft-AI/
├── intent_prompts.yml          # Intent recognition prompt configuration file
└── config.yml                  # Main configuration file
```

The default `intent_prompts.yml` will be automatically copied from the jar package to the plugin directory on first startup.

## 🔧 Configuration Structure

The configuration file uses YAML format and is divided into 6 main sections + 1 output format rule:

### 1. Role Definition (role_definition)

Defines the identity and core responsibilities of the AI intent recognition assistant.

```yaml
role_definition: |
  You are an intelligent skill intent recognition assistant, specializing in analyzing natural language input from users in Minecraft games...
```

**Purpose**: Helps the LLM clarify its task boundaries and work objectives (intent recognition, parameter extraction, task decomposition).

**Note**: This defines the role of the **intent recognition assistant**, not the personality of a game NPC. The personality system is independently configured through `personalities.yml`.

**Optimization Tips**: 
- Keep it concise and clear
- Highlight core responsibilities (intent recognition, parameter extraction, task decomposition)
- Avoid mixing in personality-related descriptions

---

### 2. Output Format Rules (output_format_rules)

Defines format specifications for AI JSON output.

```
output_format_rules: |
  【Output Format Mandatory Requirements】
  1. **Return only pure JSON objects**, do not include any Markdown markers (such as ```json, ```)
  2. **Do not add any extra text**, including: recognition reasoning, comments, explanations, summaries, etc.
  3. **Do not use line breaks and indentation**, JSON should be on one line (for easy parsing)
  4. **STRICTLY PROHIBITED** to add any text explanation after JSON
```

**Purpose**: Ensures LLM output JSON can be directly parsed by programs, avoiding format errors.

**Note**: This is an independent top-level configuration item, not a sub-item of response_format.

---

### 3. Response Format Specification (response_format)

Defines JSON templates for three standard response formats.

#### Single Intent Format (single_intent)
Used for simple, independent single operations.

```yaml
single_intent: |
  {
    "skill_name": "Skill Name",
    "action": "Specific Action",
    "entities": { "param_name": "param_value" },
    "confidence": 0.95,
    "reasoning": "Recognition reasoning explanation"
  }
```

#### Invalid Intent Format (invalid_intent)
Used when user input is unrelated to any skills.

```yaml
invalid_intent: |
  {
    "skill_name": null,
    "action": null,
    "entities": {},
    "confidence": 0.0,
    "reasoning": "Reason why no relevant skill could be identified"
  }
```

#### Multi-Step Task Format (multi_step_task)
Used for complex tasks requiring multiple coordinated steps.

```yaml
multi_step_task: |
  {
    "goal": "Overall task goal description",
    "steps": [
      {
        "id": "step_1",
        "skill_name": "Skill Name",
        "action": "Specific Action",
        "entities": { "param_name": "param_value" },
        "depends_on": []
      }
    ]
  }
```

**Key Field Descriptions**:
- `depends_on`: List of dependent step IDs; empty array means no dependencies on other steps
- `goal`: Overall task goal, helping subsequent analysis understand task intent

---

### 4. Decision Rules (decision_rules)

Guides the AI on how to choose response types in different scenarios.

#### When to Use Single Intent (when_use_single_intent)

```yaml
when_use_single_intent: |
  - User only needs to perform one independent operation
  - All necessary parameters are clearly provided
  - No dependency on return values from other operations
  - Examples: "Query diamond price", "Tell me current time"
```

#### When to Use Multi-Step Tasks (when_use_multi_step)

```yaml
when_use_multi_step: |
  - Task requires multiple independent operations to complete
  - Subsequent steps depend on results from previous steps
  - Some steps may fail, requiring conditional judgment
  - User asks for multiple related information, requiring different skills
  - User's question contains connectors like "simultaneously", "and", "also"
  - User's question covers multiple dimensions (e.g., both item list and statistics)
  - User uses pronouns like "this", "it" but specific object is unknown, requiring prior retrieval
```

#### When to Return Invalid Intent (when_return_invalid)

```yaml
when_return_invalid: |
  - User input is unrelated to any available skills
  - User is just chatting or greeting (e.g., "Hello", "Thanks")
  - User input is too vague to determine specific intent
  - Note: If there is clear context from previous conversation, should combine history to understand intent rather than directly returning invalid
```

**Optimization Tips**:
- Add more trigger conditions based on actual usage
- Provide clear positive/negative comparison examples
- Regularly adjust rules based on test data

---

### 5. Critical Constraint Rules (critical_rules)

Hard rules to avoid common errors, with highest priority.

#### Multi-Step Task Planning Rules (multi_step_mandatory)

```
multi_step_mandatory: |
  【Multi-Step Task Planning Rules - Core Decision Process】
    
  When users ask multiple independent questions simultaneously, handle according to the following decision process:
    
  **Step 1: Identify Parallel Structures**
  - Identify parallel structures in user questions (e.g., "A and B", "A with B", "A, B, C")
  - Check if each part can be answered independently
    
  **Step 2: Match Skills One by One (Semantic Matching Principle)**
  - For each sub-question, check if there is a corresponding skill in the 【Available Skills List】
  - **Semantic Matching**: The skill's purpose must completely match the user's intent
  - Judgment method: Ask yourself "What is this skill designed for? Is the user's question exactly what this skill solves?"
  - Counter-example: User asks "crafting recipe", market_query's design purpose is "query market prices", not "query recipes", so it doesn't match
  - Can only use skill names explicitly listed, strictly prohibited to fabricate
    
  **Step 3: Choose Output Format Based on Matching Results**
    
  - **All sub-questions have corresponding skills** → Return multi-step format
  - **Some sub-questions have corresponding skills**:
    - Exactly 1 sub-question has a skill → Return single intent format (execute only that one)
    - 2 or more sub-questions have skills → Return multi-step format (only include steps with skills)
  - **No sub-questions have corresponding skills** → Return invalid intent
  
  **Strictly Prohibited to Substitute with Unrelated Skills**:
  - **This is a common cause of task failure!**
  - Each skill has clear purposes and semantic scope
  - **Semantic Matching** requires skill purpose to completely match user intent, cannot substitute based solely on functional similarity
  - Judgment criteria: If skill name or description doesn't contain core keywords from user's question, it's not semantic matching
  - Counter-example: User asks "crafting recipe", even though market_query can "query", it doesn't match because market_query's semantics is "market commodity prices", not "recipes"
  - If a sub-question cannot find a semantically matching skill in the 【Available Skills List】, that sub-question has no corresponding skill, do not force a match
    
  **About Degradation Explanation**:
  - Your responsibility is to identify intents and match existing skills, not to completely answer every user question
  - When some sub-questions have no corresponding skills, return single intent or multi-step format to execute the parts with skills
  - The system will execute corresponding skills based on your recognition results, then use LLM secondary analysis combined with execution results to answer user's questions as completely as possible
  - Therefore, even if only partial steps are executed, user's complete question can still be answered
  - Do not return invalid intent just because skills don't cover all questions; as long as one sub-question has a corresponding skill, you should return the corresponding intent format
    
  **Core Principles**:
  - As long as one sub-question has a corresponding skill, must return the corresponding intent format (single intent or multi-step)
  - Better to execute partial steps than to give up completely
  - Strictly prohibited to have skill_name: null in steps array
  - Strictly prohibited to fabricate non-existent skill names
```

#### Continuous Conversation Handling (continuous_conversation)

```
continuous_conversation: |
  【Continuous Conversation Handling Principles】
  1. Always combine conversation history to understand current input
  2. If user uses pronouns ("this", "it", "that") or vague instructions ("check again", "what about now"), search for referent objects in history
  3. If user says "check once more", "same question", repeat previous intent
  4. If user is asking about attributes of "currently held item" and doesn't know specific item name, use multi-step task: first retrieve item information then execute query
```

#### Entity Parameter Format (entity_format)

```
entity_format: |
  【Entity Parameter Format Specification】
  1. Item format: Use "item_name:quantity" format, e.g., "diamond:64", "iron_ingot:1"
  2. If quantity is 1, can omit quantity, e.g., "diamond" equals "diamond:1"
  3. Multiple items separated by commas, e.g., "diamond:10, gold_ingot:5"
  4. If parameter is not applicable, use null or omit the field
```

**Importance**: These rules directly affect intent recognition accuracy; modify with extra caution.

**Note**: Actual configuration contains 4 critical constraint rules:
- `placeholder_usage`: Placeholder usage restrictions
- `continuous_conversation`: Continuous conversation handling principles
- `entity_format`: Entity parameter format specification
- `multi_step_mandatory`: Multi-step task planning rules (core decision process)

---

### 6. Special Scenario Handling Guidelines (special_scenarios)

Guiding principles for handling complex situations.

#### Conflicting Intents Handling (conflicting_intents)

```yaml
conflicting_intents: |
  【Conflicting Intents Handling】
  1. If user input may correspond to multiple skills, choose the one with highest confidence
  2. If multiple skills are reasonable, prefer the more specific and better-matching skill
  3. Explain selection reasoning in the reasoning field
```

#### Missing Parameters Handling (missing_parameters)

```yaml
missing_parameters: |
  【Missing Parameters Handling】
  1. If required parameters are missing but have default values, return intent normally
  2. If required parameters are missing and cannot be inferred, consider using multi-step task to first retrieve parameters
  3. Do not fabricate non-existent parameter values
  4. If parameters are completely undeterminable, return invalid intent and explain in reasoning
```

#### Skill Name Strict Restriction (skill_name_restriction)

```yaml
skill_name_restriction: |
  【Skill Name Strict Restriction - Highest Priority Rule】
  
  **This is the most important rule; violation will cause task failure!**
  
  1. **Absolutely Prohibited to Fabricate Skill Names**
     - Can only use skill names explicitly listed in the system-dynamically-provided 【Available Skills List】
     - Strictly prohibited to use any names not in the list, even if you think they "should exist"
     - Strictly prohibited to infer skill names based on functionality (e.g., seeing "query recipe" and fabricating "knowledge_base", "recipe_query", etc.)
  
  2. **Strict Whitelist Mechanism**
     - Treat the 【Available Skills List】 as the only whitelist
     - If user's needed functionality is not in the whitelist, treat that functionality as unavailable
     - **Strictly prohibited to substitute with functionally mismatched skills** (e.g., using market query skill to check crafting recipes)
  
  3. **Strictly Prohibited to Use skill_name: null in Steps Array**
     - Each step in steps must have a valid skill_name
     - If a sub-question has no corresponding skill, refer to the degradation strategy in 【Multi-Step Task Planning Rules】
  
  4. **Common Error Examples (Strictly Prohibited)**
     - Error: User asks for functionality not in skill list, fabricate a "seemingly reasonable" skill name
     - Error: User asks for functionality not in skill list, substitute with a functionally similar skill
     - Error: Multi-step task contains {"skill_name": null} step
     - Correct: Check 【Available Skills List】, if no semantically matching skill exists, treat that sub-question as having no corresponding skill
  
  5. **LLM Responsibility Boundaries**
     - Your responsibility: Identify intent → Match existing skills
     - Not your responsibility: Create new skills, infer skill names, assume functionality exists
     - If user needs exceed current skill scope, honestly explain limitations
```

---

### 7. Output Quality Requirements (output_quality_requirements)

Ensures output format standardization.

```yaml
output_quality_requirements: |
  【Output Quality Requirements】
  1. JSON format must be strictly correct and parseable
  2. All string values use double quotes, not single quotes
  3. Boolean values use true/false, not strings
  4. Numeric types use numbers directly, without quotes
  5. confidence value range: 0.0 - 1.0
  6. reasoning should be concise and clear, explaining recognition basis
  7. If uncertain, better to return invalid intent than guess wrong skill
  8. In multi-step tasks, ensure depends_on references existing step ids
  9. Avoid circular dependencies (step_a depends on step_b, step_b depends on step_a)
  10. Step order should follow logical execution order
```

---

## 🔄 Prompt Construction Flow

The system constructs the complete system prompt in the following fixed order during each intent recognition (see `IntentPromptConfigManager.buildSystemPrompt()` method):

```
1. 【Role Definition】
   ↓
2. 【Available Skills List】(Dynamically generated, extracted from registered Skills, passed as parameter)
   ↓
3. 【Response Format Specification】
   ├─ Single Intent Format
   ├─ Invalid Intent Format
   └─ Multi-Step Task Format
   ├─ Output Format Rules (output_format_rules)
   ↓
4. 【Decision Rules】
   ├─ When to Use Single Intent
   ├─ When to Use Multi-Step Tasks
   └─ When to Return Invalid Intent
   ↓
5. 【Critical Constraint Rules】
   ├─ Placeholder Usage Restrictions
   ├─ Continuous Conversation Handling Principles
   ├─ Entity Parameter Format Specification
   └─ Multi-Step Task Planning Rules
   ↓
6. 【Special Scenario Handling Guidelines】
   ├─ Conflicting Intents Handling
   ├─ Missing Parameters Handling
   └─ Skill Name Strict Restriction
   ↓
7. 【Output Quality Requirements】
```

This structured design ensures:
- ✅ Clear hierarchy of information received by LLM
- ✅ Important rules placed first with high priority
- ✅ Dynamic parts (skill list) inserted at appropriate positions
- ✅ Output format requirements follow response format to reinforce constraints

---

## 🛠️ Usage Instructions

### Editing Configuration File

1. Open `plugins/Kilacraft-AI/intent_prompts.yml`
2. Modify prompt content in corresponding sections as needed
3. Save the file

### Reloading Configuration

Execute the following command to apply changes:

```
/kilacraft reload
```

This command will reload all configurations at once:
- Main configuration (config.yml)
- Language configuration (language.yml)
- Skill configuration (skills/)
- **Intent recognition prompt configuration (intent_prompts.yml)** ✨

Permission requirement: `kilacraft.reload` (default OP only)

---

## 💡 Optimization Strategies

### 1. Progressive Optimization

Don't make large-scale modifications to configuration at once; instead:

1. First run with default configuration for a period
2. Observe intent recognition logs, record error cases
3. Adjust related rules targetedly
4. Change only a small part each time for easier problem localization
5. Test and verify before proceeding to next optimization step

### 2. Common Problem Troubleshooting

#### Problem 1: AI Always Returns Single Intent, Never Uses Multi-Step Tasks

**Symptoms**: Even when user's question obviously requires multiple steps, AI only returns single skill invocation.

**Solution**:
```yaml
decision_rules:
  when_use_multi_step: |
    # Strengthen trigger conditions, add more specific scenarios
    - When user's question requires multiple independent operations to complete
    - When subsequent steps depend on results from previous steps
    - When user asks questions like "is the thing in my hand for sale" that require retrieval then query
    - When user uses pronouns like "this", "it" but no clear referent in context
```

#### Problem 2: AI Mishandles Continuous Conversation

**Symptoms**: User says "what about now?", "check again", AI returns `skill_name: null` or fails to understand context.

**Solution**:
```yaml
critical_rules:
  continuous_conversation: |
    【Continuous Conversation Handling Principles】
    1. **STRICTLY PROHIBITED** to directly return skill_name: null
    2. Must analyze core intent of previous conversation
    3. If previous was querying status, re-execute same query step
    4. Example: User previously asked "what's in my hand", then says "can this be sold"
       → Should use multi-step: first get_player_hand_item, then query_availability
```

#### Problem 3: Incorrect Placeholder Usage

**Symptoms**: Single intent contains `{step_1.xxx}` placeholder, causing execution failure.

**Solution**:
```
critical_rules:
  placeholder_usage: |
    【Critical Rule】
    1. {step_x.xxx} placeholders are used **ONLY** in multi-step tasks
    2. Single intent entities must be specific values or null
    3. Wrong example: { "entities": { "item": "{step_1.name}" } } ❌
    4. Correct example: { "entities": { "item": "diamond:1" } } ✅
```

#### Problem 4: Inaccurate Reference Resolution

**Symptoms**: User says "how much is this", AI cannot correctly identify what "this" refers to.

**Solution**:
```yaml
critical_rules:
  continuous_conversation: |
    【Continuous Conversation Handling Principles】
    1. Prioritize searching for explicit item names from previous conversation
    2. If referring to "currently held item" and don't know specific name, MUST use multi-step task
    3. Do not guess or fabricate item names
    4. Examples:
       - Previous: "How much is diamond?" → AI: "100 gold" → User: "What about this?"
         → Should recognize as querying price of emerald or other items (based on context)
       - User: "Can this be sold?" (no context)
         → Should use multi-step: first get_player_hand_item, then query_availability
```

---

## 🧪 Testing and Debugging

If you have special requirements for JSON format:

```
output_quality_requirements: |
  【Output Quality Requirements】
  1. Must use double quotes
  2. Trailing commas not allowed
  3. Arrays cannot be empty (must contain at least one element or use null)
  4. ... More custom requirements
```

---

## 🧪 Testing and Debugging

### Enabling Debug Mode

Set in `config.yml`:

```yaml
settings:
  debug_mode: true
```

### Observing Intent Recognition Results

View through console logs:
- Raw JSON returned by LLM
- Parsed intent objects
- Executed task plans
- Execution results of each step

### Collecting Error Cases

Establish an error case library, recording:
1. User input
2. Expected recognition result
3. Actual recognition result
4. Error cause analysis
5. Configuration adjustment plan

Regularly optimize configuration based on these cases.

---

## 📝 Best Practices

### 1. Maintain Rule Consistency

- Avoid contradictory rules in different sections
- If updating a rule, check other sections for conflicts
- Use unified terminology and expression methods

### 2. Document Changes

Suggest adding change records at the top of configuration file:

```yaml
# ==================== Intent Recognition Prompt Configuration ====================
# Last Modified: 2026-04-06
# Modified By: XXX
# Change Log:
# - 2026-04-06: Initial version
# - 2026-04-XX: Optimized multi-step task trigger conditions
# - ...
```

### 3. Backup Configuration Files

Backup before making large-scale modifications:

```bash
cp plugins/Kilacraft-AI/intent_prompts.yml plugins/Kilacraft-AI/intent_prompts.yml.backup
```

Facilitates quick recovery when problems occur.

---

## ⚠️ Precautions

1. **YAML Format Must Be Strict**: 
   - Use `|` symbol for multi-line text
   - Pay attention to indentation (usually 2 spaces)
   - Avoid using Tab characters

2. **Do Not Delete Required Configuration Items**: 
   - If a configuration item doesn't exist, default value will be used
   - But may result in incomplete prompts
   - Suggest commenting out rather than deleting

3. **Test Immediately After Reloading**: 
   - Must test effects after modifying configuration
   - Ensure changes meet expectations
   - Observe whether there are side effects

4. **Performance Considerations**: 
   - Longer prompts increase token consumption
   - Streamline redundant descriptions
   - Maintain clarity of core rules

---

## 🔗 Related Files

- **Configuration Manager**: `IntentPromptConfigManager.java`
- **Intent Recognizer**: `SkillIntentRecognizer.java`
- **Command Handler**: `KilacraftCommand.java` (handleReloadCommand method)
- **Language Configuration**: `language.yml`
- **Permission Definition**: `PluginPermissionEnum.java` (RELOAD enum)

---

## 📌 Difference from Personality System

### Intent Recognition System vs Personality System

| Feature | Intent Recognition System | Personality System |
|---------|--------------------------|-------------------|
| **Configuration File** | `intent_prompts.yml` | `personalities.yml` |
| **Activation Timing** | Entry point for all AI interactions (keyword trigger, continuous conversation, plugin commands) | Used only in plugin command mode (`/kilacraft plugins`) |
| **Core Function** | Identify user intent, select Skills, plan task steps | Define AI's response style and tone |
| **Configuration Content** | Intent recognition rules, response formats, decision logic | Different personality prompts (Strict Teacher, Adventure Partner, etc.) |
| **Multiple Instances** | No, globally unique set of rules | Yes, can configure multiple personalities |
| **Reload Command** | `/kilacraft reload` | `/kilacraft personalities reload` |

### Workflow Examples

#### Scenario 1: Player Triggers via Keyword
```
Player: @ai What am I holding?
  ↓
【Intent Recognition】(intent_prompts.yml)
  → Identified as: GenericBukkitAPI.get_player_hand_item
  ↓
【Execute Skill】
  → Retrieve player's held item
  ↓
【Generate Response】(using default system_prompt or current personality)
  → "You are holding a diamond sword"
```

#### Scenario 2: Plugin Command Mode (with Personality)
```
Console: /kilacraft plugins strict_teacher Hello UUID xxx
  ↓
【Intent Recognition】(intent_prompts.yml)
  → Identify intent of user input "Hello" (may be chat, return invalid intent)
  ↓
【Fallback to Normal Conversation】
  → Use "strict_teacher" personality prompt (personalities.yml)
  ↓
【Generate Response】
  → "Student, no chatting during class time! Ask specific questions if you have any."
```

### Key Differences

1. **Intent Recognition is Universal**: Regardless of which personality is used, intent recognition rules remain the same
2. **Personality Affects Response Style**: Personality only affects how to answer finally, not recognizing what user wants to do
3. **Independently Configured**: Modifying personality won't affect intent recognition, and vice versa

### Configuration Recommendations

- **intent_prompts.yml**: Focus on how to make AI more accurately understand what operations users want to perform
- **personalities.yml**: Focus on how to make AI answer questions with different styles and tones

**DO NOT** mix personality-related descriptions into `intent_prompts.yml`, as this will confuse intent recognition logic.

---

## 🚀 Advanced Techniques

### 1. Optimize for Specific Skills

If your server has special Skills, emphasize in configuration:

```yaml
critical_rules:
  your_skill_specific_rule: |
    【Your Skill Name Specific Rules】
    1. When user mentions keyword X, prioritize using Y Skill
    2. Parameter Z must be provided in W format
    3. ...
```

### 2. Seasonal Adjustments

Adjust prompts according to activities in different periods:

```yaml
# During festivals
decision_rules:
  when_use_multi_step: |
    - When user asks about festival activities
    - When user wants to participate in limited-time events
    - ...
```

### 3. Newcomer Guidance Optimization

Provide more friendly intent recognition for new players:

```yaml
role_definition: |
  You are a patient Minecraft game assistant, especially good at helping new players...
  
critical_rules:
  newbie_friendly: |
    【Newcomer Friendly Principles】
    1. For vague newcomer questions, prioritize recommending basic skills
    2. Don't assume players already understand advanced features
    3. ...
```

---

## 📊 Monitoring and Evaluation

### Key Metrics

Regularly monitor the following metrics:

1. **Intent Recognition Accuracy**: Correctly identified intents / Total requests
2. **Multi-Step Task Ratio**: Multi-step tasks / Total tasks
3. **Invalid Intent Ratio**: Invalid intents / Total requests
4. **Average Confidence**: Average confidence value of all recognition results

### Optimization Cycle

Suggested optimization cycle:

- **Daily**: Review error logs, record typical cases
- **Weekly**: Analyze statistical data, adjust configuration
- **Monthly**: Comprehensively review configuration, clean up outdated rules
- **Quarterly**: Re-evaluate overall architecture, consider major improvements

---

## ❓ Frequently Asked Questions

### Q: Configuration changes not taking effect after modification?

A: Ensure you executed `/kilacraft reload` command and check console for error messages.

### Q: How to determine if configuration is reasonable?

A: Observe intent recognition accuracy; if below 80%, optimization is needed. Focus on multi-step task recognition.

### Q: Can different personalities use different prompts?

A: Current version does not support this; all personalities share the same set of intent recognition prompts. Future versions may support personalized configurations.

### Q: Will long prompts affect performance?

A: It will increase token consumption and response time, but the impact is minor. Recommended to keep within 2000-3000 tokens.

### Q: How to rollback to previous configuration?

A: If you have backup files, simply replace:
```bash
cp plugins/Kilacraft-AI/intent_prompts.yml.backup plugins/Kilacraft-AI/intent_prompts.yml
/kilacraft reload
```

---

## 📞 Support and Feedback

If you encounter problems during use or have improvement suggestions:

1. Check console logs to confirm if there are error messages
2. Verify configuration file format is correct
3. Try restoring to default configuration to confirm if it's a configuration issue
4. Collect relevant logs and configuration snippets, provide feedback to developers

---

**Last Updated**: 2026-04-06  
**Applicable Version**: Kilacraft-AI v1.4.1+
