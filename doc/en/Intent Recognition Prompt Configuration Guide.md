# Kilacraft-AI - Intent Recognition Prompt Configuration Guide

> **Last Updated**: 2026-04-23  
> **Description**: This document details how to configure intent recognition prompts through intent_prompts.yml to guide LLM in understanding user input and identifying skill intents

---

## 📁 File Location

```
plugins/Kilacraft-AI/
├── intent_prompts.yml          # Intent recognition prompt configuration file
└── config.yml                  # Main configuration file
```

On first startup, the default `intent_prompts.yml` will be automatically copied from jar package to plugin directory.

## 🔧 Configuration Structure

The configuration file uses YAML format and is divided into 6 main sections + 1 output format rule:

### 1. Role Definition (role_definition)

Defines the identity and core responsibilities of the AI intent recognition assistant.

```yaml
role_definition: |
  You are an intelligent skill intent recognition assistant, specializing in analyzing natural language input from users in Minecraft games...
```

**Purpose**: Helps LLM clarify its task boundaries and work objectives (intent recognition, parameter extraction, task decomposition).

**Note**: This defines the role of **intent recognition assistant**, not a game NPC's personality. The personality system is independently configured through `personalities.yml`.

**Optimization Tips**: 
- Keep it concise and clear
- Highlight core responsibilities (intent recognition, parameter extraction, task decomposition)
- Avoid mixing in personality-related descriptions

---

### 2. Output Format Rules (output_format_rules)

Defines format specifications for AI JSON output.

```yaml
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
- `depends_on`: List of dependent step IDs, empty array means no dependencies on other steps
- `goal`: Overall task goal, helping subsequent analysis understand task intent

---

### 4. Decision Rules (decision_rules)

Guides AI on how to choose response types in different scenarios.

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
  - User is just chatting or greeting (e.g., "Hello", "Thank you")
  - User input is too vague to determine specific intent
  - Note: If there is clear context in previous conversation, should understand intent by combining with history, not directly return invalid
```

**Optimization Tips**:
- Add more trigger conditions based on actual usage
- Provide clear positive/negative comparison examples
- Regularly adjust configuration based on test data

---

### 5. Critical Rules (critical_rules)

Avoid common errors as hard constraints, highest priority.

#### Multi-Step Task Planning Rules (multi_step_mandatory)

```yaml
multi_step_mandatory: |
  【Multi-Step Task Planning Rules - Core Decision Flow】
    
  When user asks multiple independent questions simultaneously, process according to the following decision flow:
    
  **Step 1: Identify Parallel Structure**
  - Identify parallel structures in user's question (e.g., "A and B", "A & B", "A, B, C")
  - Check if each part can be independently answered
    
  **Step 2: Match Skills Individually (Semantic Matching Principle)**
  - For each sub-question, check if there is a corresponding skill in the【Available Skill List】
  - **Semantic Matching**: The skill's purpose must completely correspond to user's intent
  - Judgment method: Ask yourself "What is this skill's design purpose? Does the user's question exactly match what this skill is supposed to solve?"
  - Counter-example: User asks "crafting recipe", market_query's design purpose is "query market prices", not "query recipes", so no match
  - Only use skill names explicitly listed, absolutely no fabrication
    
  **Step 3: Choose Output Format Based on Matching Results**
    
  - **All sub-questions have corresponding skills** → Return multi-step format
  - **Partial sub-questions have corresponding skills**:
    - Only 1 sub-question has corresponding skills → Return single intent format (only execute that one)
    - 2 or more sub-questions have corresponding skills → Return multi-step format (only include steps with skills)
  - **All sub-questions have no corresponding skills** → Return invalid intent
  
  **Strictly Prohibit Using Unrelated Skills as Substitutes**:
  - **This is a common cause of task failure!**
  - Each skill has a clear purpose and semantic scope
  - **Semantic Matching** requires the skill's purpose to completely correspond to user's intent, cannot substitute based only on functional similarity
  - Judgment standard: If the skill name or description does not contain the core keywords of the user's question, it is not semantic matching
  - Counter-example: User asks "crafting recipe", even if market_query can "query", it doesn't match because market_query's semantic is "market commodity prices", not "recipes"
  - If a certain sub-question cannot find a skill with completely semantic match in the【Available Skill List】, that sub-question has no corresponding skill, do not force match
    
  **About Downgrading Explanation**:
  - Your responsibility is to identify intent and match existing skills, not fully answer every question of the user
  - When part of sub-questions have no corresponding skills, return single intent or multi-step format to execute the parts with skills
  - The system will execute corresponding skills based on your recognition results, then through LLM secondary analysis combine execution results to fully answer the user's question
  - Therefore, even if only part of the steps are executed, the user's complete question can still get an answer
  - Do not return invalid intent just because skills don't cover all questions, as long as one sub-question has a corresponding skill, you should return the corresponding intent format (single intent or multi-step)
    
  **Core Principles**:
  - As long as one sub-question has a corresponding skill, must return the corresponding intent format (single intent or multi-step)
  - Prefer executing part of steps rather than completely giving up
  - Strictly prohibit skill_name: null in steps array
  - Strictly prohibit inventing non-existent skill names
```

#### Continuous Conversation Handling (continuous_conversation)

```yaml
continuous_conversation: |
  【Continuous Conversation Handling Principles】
  1. Always combine conversation history to understand current input
  2. If user uses pronouns ("this", "it", "that") or ambiguous commands ("check again", "now?"), look for referent objects from history
  3. If user says "check again", "same question", repeat the previous round's intent
  4. If user is asking about "current hand item" related properties and doesn't know specific item name, use multi-step task: first get item info then execute query
```

#### Entity Parameter Format (entity_format)

```yaml
entity_format: |
  【Entity Parameter Format Specification】
  1. Item format: Use "ItemName:Quantity" format, such as "Diamond:64", "Iron Ingot:1"
  2. If quantity is 1, can omit quantity, such as "Diamond" is equivalent to "Diamond:1"
  3. Multiple items separated by commas, such as "Diamond:10, Gold Ingot:5"
  4. If parameter is not applicable, use null or omit the field
```

**Importance**: These rules directly affect intent recognition accuracy, modify with extreme caution.

**Note**: Actual configuration contains 4 critical constraint rules:
- `placeholder_usage`: Placeholder usage restrictions
- `continuous_conversation`: Continuous conversation handling principles
- `entity_format`: Entity parameter format specifications
- `multi_step_mandatory`: Multi-step task planning rules (core decision flow)

---

### 6. Special Scenarios Handling Guidelines (special_scenarios)

Handling guidance principles for complex situations.

#### Conflict Intent Handling (conflicting_intents)

```yaml
conflicting_intents: |
  【Conflict Intent Handling】
  1. If user input may correspond to multiple skills, choose the one with highest confidence
  2. If multiple skills are all reasonable, prioritize the more specific, more matching one
  3. Explain the choice reason in the reasoning field
```

#### Missing Parameter Handling (missing_parameters)

```yaml
missing_parameters: |
  【Missing Parameter Handling】
  1. If required parameters are missing but have default values, return intent normally
  2. If required parameters are missing and cannot be inferred, consider using multi-step task to first obtain parameters
  3. Do not invent non-existent parameter values
  4. If parameters are completely undeterminable, return invalid intent and explain in reasoning
```

#### Skill Name Strict Restriction (skill_name_restriction)

```yaml
skill_name_restriction: |
  【Skill Name Strict Restriction - Highest Priority Rule】
  
  **This is the most important rule, violation will cause task failure!**
  
  1. **Absolutely prohibit inventing skill names**
     - Only use skill names explicitly listed in the system-dynamic-provided【Available Skill List】
     - Strictly prohibit using any names not in the list, even if you think it "should exist"
     - Strictly prohibit inferring skill names based on functionality (e.g., seeing "query recipes" and inventing "knowledge_base", "recipe_query", etc.)
  
  2. **Strict Whitelist Mechanism**
     - Treat the【Available Skill List】as the unique whitelist
     - If user needs functionality is not in the whitelist, consider that function unavailable
     - **Strictly prohibit using function-mismatched skills as substitutes** (such as using market query skill to check crafting recipes)
  
  3. **Strictly prohibit using skill_name: null in steps array**
     - Each step in steps must have a valid skill_name
     - If a certain sub-question has no corresponding skill, refer to the downgrade strategy in【Multi-Step Task Planning Rules】
  
  4. **Common Error Examples (Strictly prohibit this type of error)**
     - Error: User asks functionality not in skill list, invent a "looks reasonable" skill name
     - Error: User asks functionality not in skill list, use a functionally similar skill as substitute
     - Error: Multi-step task appears {"skill_name": null} step
     - Correct: Check【Available Skill List】, if there is no skill with completely semantic match, that sub-question is considered as no corresponding skill
  
  5. **LLM's Responsibility Boundary**
     - Your responsibility: Identify intent → Match existing skills
     - Not your responsibility: Create new skills, infer skill names, assume functionality exists
     - If user needs exceed current skill scope, honestly explain limitations
```

---

### 7. Output Quality Requirements (output_quality_requirements)

Ensure output format standardization.

```yaml
output_quality_requirements: |
  【Output Quality Requirements】
  1. JSON format must be strictly correct, can be parsed by parsers
  2. All string values use double quotes, do not use single quotes
  3. Boolean values use true/false, do not use strings
  4. Numeric types directly use numbers, without quotes
  5. confidence value range: 0.0 - 1.0
  6. reasoning should be concise and clear, explaining recognition basis
  7. If uncertain, prefer returning invalid intent rather than guessing wrong skill
  8. In multi-step tasks, ensure depends_on references existing step ids
  9. Avoid circular dependencies (step_a depends on step_b, step_b depends on step_a)
  10. Step order should conform to logical execution order
```

---

## 🔄 Prompt Building Flow

### Intent Recognition Flow

The system adopts LLM-based intent recognition mechanism:

```
User Input
  ↓
SkillIntentRecognizer (LLM Intent Recognition)
  ├─ 1. Build system prompt (including all available Skill descriptions)
  ├─ 2. Call LLM for intent analysis
  ├─ 3. Parse JSON response
  └─ 4. Determine task type
       ├─ Single intent → SkillIntent
       ├─ Multi-step → TaskPlan
       └─ Invalid intent → Fallback to normal AI dialogue
```

**LLM Intent Recognition** (Full Injection of All Skills)

The system builds the complete system prompt in a fixed order during each intent recognition (see `IntentPromptConfigManager.buildSystemPrompt()` method):

```
1. 【Role Definition】
   ↓
2. 【Available Skill List】(Dynamic generation, extracted from registered Skills, passed in as parameters)
   ↓
3. 【Response Format Specification】
   ├─ Single intent format
   ├─ Invalid intent format
   └─ Multi-step task format
   ├─ Output format mandatory rules (output_format_rules)
   ↓
4. 【Decision Rules】
   ├─ When to use single intent
   ├─ When to use multi-step task
   └─ When to return invalid intent
   ↓
5. 【Critical Rules】
   ├─ Placeholder usage restrictions
   ├─ Continuous conversation handling principles
   ├─ Entity parameter format specifications
   └─ Multi-step task planning rules
   ↓
6. 【Special Scenarios Handling Guidelines】
   ├─ Conflict intent handling
   ├─ Missing parameter handling
   └─ Skill name strict restriction
   ↓
7. 【Output Quality Requirements】
```

This structured design ensures:
- ✅ LLM receives information in a clear hierarchy
- ✅ Important rules are placed at the beginning, with high priority
- ✅ Dynamic parts (Skill list) are inserted at appropriate locations
- ✅ Output format requirements follow response format, reinforcing constraints

---

## 🛠️ Usage Methods

### Edit Configuration File

1. Open `plugins/Kilacraft-AI/intent_prompts.yml`
2. Modify the corresponding section's prompt content according to your needs
3. Save the file

### Reload Configuration

Execute the following command to make changes effective:

```bash
/kilacraft reload
```

This command will one-time reload:
- Main configuration (config.yml)
- Language configuration (language.yml)
- Skills configuration (skills/)
- **Intent recognition prompt configuration (intent_prompts.yml)** ✨

Permission requirements: `kilacraft.reload` (default only OP)

---

## 💡 Optimization Strategies

### 1. Progressive Optimization

Do not modify configuration massively at once, instead:

1. First use default configuration to run for a while
2. Observe intent recognition logs, record error cases
3. Make targeted adjustments to related rules
4. Only change a small part each time, making it easy to locate problems
5. Test and verify before proceeding to the next optimization

### 2. Common Problem Troubleshooting

#### Problem 1: AI always returns single intent, does not use multi-step tasks

**Symptoms**: Even when the user's question obviously needs multiple steps, AI only returns a single skill call.

**Solution**:
```yaml
decision_rules:
  when_use_multi_step: |
    # Strengthen trigger conditions, add more specific scenarios
    - When user's question needs multiple independent operations to complete
    - When subsequent steps depend on previous step's results
    - When user asks "does the item I'm holding have for sale", this type of situation requires first getting then querying
    - When user uses "this", "it" etc. pronouns but no explicit referent object in context
```

#### Problem 2: AI handles continuous conversations improperly

**Symptoms**: User says "now?", "check again", AI returns `skill_name: null` or cannot understand context.

**Solution**:
```yaml
critical_rules:
  continuous_conversation: |
    【Continuous Conversation Handling Principles】
    1. **Strictly prohibit** directly returning skill_name: null
    2. Must analyze the core intent of the previous round's conversation
    3. If the previous round is querying status, then execute the same query again
    4. Example: User previously asked "what am I holding", then says "can this sell?"
         → Should use multi-step: first get hand item, then query market
```

#### Problem 3: Placeholder Usage Errors

**Symptoms**: Single intent appears `{step_1.xxx}` placeholder, causing execution failure.

**Solution**:
```yaml
critical_rules:
  placeholder_usage: |
    【Critical Rules】
    1. {step_x.xxx} placeholder **ONLY** used in multi-step tasks
    2. Entities of single intent must be specific values or null
    3. Error example: { "entities": { "item": "{step_1.name}" } } ❌
    4. Correct example: { "entities": { "item": "Diamond:1" } } ✅
```

#### Problem 4: Anaphora Resolution Inaccuracy

**Symptoms**: User says "how much is this", AI cannot correctly identify "this" refers to which item.

**Solution**:
```yaml
critical_rules:
  continuous_conversation: |
    【Continuous Conversation Handling Principles】
    1. Prioritize finding explicit item names from previous round's conversation
    2. If referring to "current hand item" and unknown specific name, must use multi-step task
    3. Do not guess or invent item names
    4. Example:
       - Previous: "How much is the diamond?" → AI: "100 gold coins" → User: "What about this one?"
         → Should identify as querying emerald or other item price (based on context)
       - User: "Can this sell?" (no context)
         → Should use multi-step: first get_player_hand_item, then query_availability
```

---

## 🧪 Testing and Debugging

### Enable Debug Mode

In `config.yml`, set:

```yaml
settings:
  debug_mode: true
```

### Observe Intent Recognition Results

View through console logs:
- LLM returned original JSON
- Parsed intent object
- Executed task plan
- Each step's execution result

### Collect Error Cases

Build error case library, recording:
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
- If updating a certain rule, check if other sections have conflicts
- Use unified terminology and expression methods

### 2. Document Changes

It's recommended to add change records at the top of the configuration file:

```yaml
# ==================== Intent Recognition Prompt Configuration ====================
# Last Modified: 2026-04-19
# Modified By: XXX
# Change Log:
# - 2026-04-19: Initial version
# - 2026-04-XX: Optimized multi-step task trigger conditions
# - ...
```

### 3. Backup Configuration File

Before making large-scale modifications, backup first:

```bash
cp plugins/Kilacraft-AI/intent_prompts.yml plugins/Kilacraft-AI/intent_prompts.yml.backup
```

Convenient for quick recovery when problems occur.

---

## ⚠️ Important Notes

1. **YAML Format Must Be Strict**: 
   - Multi-line text uses `|` symbol
   - Note indentation (usually use 2 spaces)
   - Avoid using Tab characters

2. **Do Not Delete Required Configuration Items**: 
   - If a certain configuration item does not exist, will use default values
   - But may cause incomplete prompts
   - It's recommended to comment out instead of deleting

3. **Test Immediately After Reload**: 
   - After modifying configuration, must test effect
   - Ensure changes meet expectations
   - Observe if there are side effects

4. **Performance Considerations**: 
   - Too long prompts will increase token consumption
   - Simplify redundant descriptions
   - Keep core rules clear

---

## 🔗 Related Files

- **Configuration Manager**: `IntentPromptConfigManager.java`
- **Intent Recognizer**: `SkillIntentRecognizer.java`
- **Command Handler**: `KilacraftCommand.java` (handleReloadCommand method)
- **Language Configuration**: `language.yml`
- **Permission Definition**: `PluginPermissionEnum.java` (RELOAD enum)

---

## 📌 Difference from Personality System

### Intent Recognition System vs. Personality System

| Feature | Intent Recognition System | Personality System |
|---------|-------------------------|-------------------|
| **Configuration File** | `intent_prompts.yml` | `personalities.yml` |
| **Usage Timing** | All AI interaction entrances (keyword trigger, continuous conversation, plugin command) | Only used in plugin command mode (`/kilacraft plugins`) |
| **Core Function** | Identify user intent, select Skill, plan task steps | Define AI's answering style and tone |
| **Configuration Content** | Intent recognition rules, response formats, decision logic | Different personality prompts (strict teacher, adventure partner, etc.) |
| **Multi-Instance** | No, globally unique set of rules | Yes, can configure multiple personalities |
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
  → Get player hand item
  ↓
【Generate Response】(using default system_prompt or current personality)
  → "You are holding a diamond sword"
```

#### Scenario 2: Plugin Command Mode (with Personality)
```
Console: /kilacraft plugins strict_teacher Hello UUID xxx
  ↓
【Intent Recognition】(intent_prompts.yml)
  → Identify user input "Hello" intent (may be chatting, return invalid intent)
  ↓
【Fallback to Normal Dialogue】
  → Use "strict_teacher" personality's prompt (personalities.yml)
  ↓
【Generate Response】
  → "Classmate, don't chat during class! Ask if you have questions."
```

### Key Differences

1. **Intent recognition is universal**: Regardless of which personality is used, intent recognition rules are the same
2. **Personality affects response style**: Personality only affects final how to answer, does not affect identifying what user wants to do
3. **Two are independently configured**: Modifying personality will not affect intent recognition, and vice versa

### Configuration Recommendations

- **intent_prompts.yml**: Focus on how to make AI more accurately understand what operations users want to execute
- **personalities.yml**: Focus on how to make AI answer in different styles and tones

**DO NOT** mix personality-related descriptions in `intent_prompts.yml`, this will confuse intent recognition logic.

---

## 🚀 Advanced Tips

### 1. Optimize for Specific Skills

If your server has special Skills, you can emphasize them in the configuration:

```yaml
critical_rules:
  your_skill_specific_rule: |
    【Your Skill Name Dedicated Rules】
    1. When user mentions keyword X, prioritize using Y Skill
    2. Parameter Z must be provided in W format
    3. ...
```

### 2. Seasonal Adjustments

Adjust prompts according to different periods' activities:

```yaml
# During holidays
decision_rules:
  when_use_multi_step: |
    - When user asks about holiday event content
    - When user wants to participate in limited-time events
    - ...
```

### 3. Newbie-Friendly Optimization

Provide more friendly intent recognition for new players:

```yaml
role_definition: |
  You are a patient Minecraft game assistant, especially good at helping novice players...
  
critical_rules:
  newbie_friendly: |
    【Newbie-Friendly Principles】
    1. For vague novice questions, prioritize recommending basic skills
    2. Do not assume players already understand advanced features
    3. ...
```

---

## 📊 Monitoring and Evaluation

### Key Indicators

Regularly monitor the following metrics:

1. **Intent Recognition Accuracy**: Correctly recognized intents count / Total requests
2. **Multi-Step Task Ratio**: Multi-step tasks count / Total tasks
3. **Invalid Intent Ratio**: Invalid intent count / Total requests
4. **Average Confidence**: Average confidence value of all recognition results

### Optimization Cycle

Recommended optimization cycle:
- **Daily**: View error logs, record typical cases
- **Weekly**: Analyze statistical data, adjust configuration
- **Monthly**: Comprehensive review of configuration, clean outdated rules
- **Quarterly**: Re-evaluate overall architecture, consider major improvements

---

## ❓ Common Questions

### Q: No effect after modifying configuration?

A: Ensure you executed `/kilacraft reload` command and check if there are error messages in console.

### Q: How to determine if configuration is reasonable?

A: Observe intent recognition accuracy, if below 80%, indicates need for optimization. Focus on multi-step task recognition situations.

### Q: Can I use different prompts for different personalities?

A: Current version does not support, all personalities share the same set of intent recognition prompts. Future versions may support personalized configuration.

### Q: Will too long prompts affect performance?

A: Will increase token consumption and response time, but impact is small. Recommended to keep within 2000-3000 tokens.

### Q: How to roll back to previous configuration?

A: If there is a backup file, directly replace it:
```bash
cp plugins/Kilacraft-AI/intent_prompts.yml.backup plugins/Kilacraft-AI/intent_prompts.yml
/kilacraft reload
```

---

## 📞 Support and Feedback

If you encounter problems or have improvement suggestions during usage:

1. Check console logs to confirm if there are error messages
2. Check if configuration file format is correct
3. Try restoring to default configuration, confirm if it's a configuration problem
4. Collect relevant logs and configuration snippets, provide feedback to developers

---

**Last Updated**: 2026-04-19
**Compatible Version**: Kilacraft-AI v1.4.6+
