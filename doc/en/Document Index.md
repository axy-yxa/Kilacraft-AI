# Kilacraft-AI Document Index
> **Version**: v1.4.1  
> **Language**: English  
> **Last Updated**: 2026-04-05
---
## 📚 Document Overview
This document index lists all Chinese technical documents of the Kilacraft-AI project to help you quickly find the required information.
---
## 🎯 Quick Navigation
### 👥 Server Owner/Administrator Documents
| Document Name | Description | Target Audience |
|--------------|-------------|----------------|
| [Server Owner's Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Complete installation, configuration, and usage guide | Server Administrators |
| [Changelog](sslocal://flow/file_open?url=.%2FKilacraft-AI-Changelog.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Version history and change descriptions | Everyone |
### 🔌 Developer Documents
| Document Name | Description | Target Audience |
|--------------|-------------|----------------|
| [Skill SPI Integration Document](./Skill-SPI-Integration-Document) | How to develop custom skills for Kilacraft-AI | Plugin Developers |
| [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide) | Complete guide for third-party plugin integration (callback mechanism, async optimization, etc.) | Plugin Developers |
### 📖 Technical Reference Documents
| Document Name | Description | Target Audience |
|--------------|-------------|----------------|
| [Bukkit API Reference Manual](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Detailed descriptions and examples of 44+ built-in APIs | Server Owners, Developers |
| [Personality System Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Personality-System-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Management and customization methods for multiple personalities | Server Owners, Advanced Users |
| [Knowledge Base Enhancement Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Usage and optimization techniques for RAG knowledge base | Server Owners, Content Creators |
| [Intent Recognition Prompt Configuration Guide](./Intent-Recognition-Prompt-Configuration-Guide) | Configuration and optimization of LLM intent recognition system | Server Owners, Developers |
| [System Architecture Details](./System-Architecture-Details) | Complete call chains and design philosophy of three interaction modes | Developers, Technical Personnel |
---
## 📋 Detailed Document Descriptions
### 1. Server Owner's Guide
**File**: `Kilacraft-AI-Server-Owner's-Guide.md`  
**Size**: ~45 KB  
**Content**:
- ✅ Why Choose Kilacraft-AI (Advantage Comparison)
- ✅ Core Feature Showcase (Intelligent Chat, Knowledge Base, Intent Recognition, etc.)
- ✅ Quick Start (5-Minute Getting Started Tutorial)
- ✅ Performance and Resource Usage Analysis
- ✅ Complete Command List
- ✅ Dependence Requirements
- ✅ Advanced Configuration Details
- ✅ Troubleshooting Guide
  **Recommended Chapters**:
- Chapters 1-3: Understand core features and quick start
- Chapter 7: Plugin Command Mode Integration (Must-read for third-party plugin developers)
- Chapter 9: Troubleshooting
---
### 2. Changelog
**File**: `Kilacraft-AI-Changelog.md`  
**Size**: ~10 KB  
**Content**:
- ✅ Change records for all versions from v1.0.0 to v1.4.0
- ✅ New features, optimizations, and compatibility notes for each version
- ✅ Packaging changes and breaking update warnings
  **Recommended Usage Scenarios**:
- Check compatibility notes before upgrading
- Learn about the latest features and improvements
- Troubleshoot version-related issues
---
### 3. Skill SPI Integration Document
**File**: `Kilacraft-AI-Skill-SPI-Integration-Document.md`  
**Size**: ~33 KB  
**Content**:
- ✅ SPI Architecture Overview and Data Flow
- ✅ Quick Start (5-Minute Integration Tutorial)
- ✅ Detailed Explanation of Core Interfaces (SkillProvider, Skill, SkillContext, SkillResult)
- ✅ Skill Development Specifications
- ✅ Multi-step Task Data Transfer Mechanism
- ✅ Error Isolation and Exception Handling
- ✅ Permission and Availability Control
- ✅ Naming Conventions and Conflict Resolution
- ✅ Complete Example Code
  **Recommended Chapters**:
- Chapter 3: Quick Start (Must-read for beginners)
- Chapters 4-5: Core Interfaces and Development Specifications
- Chapter 7: Error Isolation Mechanism
- Chapter 11: Complete Examples
---
### 4. Bukkit API Reference Manual
**File**: `Kilacraft-AI-Bukkit-API-Reference-Manual.md`  
**Size**: ~23 KB  
**Content**:
- ✅ Complete list of 44+ built-in APIs
- ✅ Configuration examples and usage scenarios for each API
- ✅ Parameter type and return type descriptions
- ✅ Detailed permission management
- ✅ Performance optimization suggestions
- ✅ Troubleshooting guide
  **API Categories**:
- 👤 Player-related APIs (16): Health, Hunger, Inventory, Location, etc.
- 🌍 World-related APIs (6): Time, Weather, Difficulty, Seed, etc.
- 🖥️ Server-related APIs (8): Online Players, TPS, Version, etc.
- 🐾 Entity-related APIs (3): Nearby entity count statistics
  **Recommended Usage Scenarios**:
- Need to know which game data the AI can query
- Configure custom Bukkit API calls
- Set fine-grained permission control
---
### 5. Personality System Configuration Guide
**File**: `Kilacraft-AI-Personality-System-Configuration-Guide.md`  
**Size**: ~18 KB  
**Content**:
- ✅ Personality system architecture and configuration file structure
- ✅ Best practices for writing system prompts
- ✅ Detailed language style configuration (tone, formality, emojis, etc.)
- ✅ 4 complete personality configuration examples:
    - Default Assistant (Friendly and Professional)
    - Newbie Mentor (Patient and Gentle)
    - Technical Expert (Rigorous and Detailed)
    - Humorous Chat Partner (Funny and Witty)
- ✅ Personality switching methods (commands, permissions, automatic assignment)
- ✅ Advanced usage (dynamic switching, personality combination, seasonal personalities)
- ✅ Frequently Asked Questions
  **Recommended Chapters**:
- Chapter 2: Writing System Prompts (Core Content)
- Chapter 3: Complete Configuration Examples (Can be copied and modified directly)
- Chapter 5: Frequently Asked Questions
---
### 6. Knowledge Base Enhancement Guide

**File**: `Kilacraft-AI-Knowledge-Base-Enhancement-Guide.md`  
**Size**: ~16 KB  
**Content**:
- ✅ RAG technology principles and workflow
- ✅ Document writing specifications (Markdown best practices)
- ✅ Detailed explanation of intelligent segmentation algorithm and comprehensive scoring mechanism (HanLP TF-IDF + BM25)
- ✅ Configuration option descriptions (max_relevant_chunks, segment, keywords, bm25, custom_dictionary)
- ✅ Custom dictionary support (improves Chinese word segmentation accuracy)
- ✅ Performance optimization suggestions
- ✅ Troubleshooting guide
  **Recommended Chapters**:
- Chapter 2: Document Writing Specifications (Key to improving retrieval accuracy)
- Chapter 3: Detailed Explanation of Retrieval Mechanism (Understand working principles)
- Chapter 5: Performance Optimization
---

### 7. System Architecture Details

**File**: `Kilacraft-AI-System-Architecture-Details.md`  
**Size**: ~15 KB  
**Content**:

- ✅ Overview of three interaction modes (ChatListener / KilacraftCommand / Plugin Command)
- ✅ Complete call chain for Mode 1 & 2 (Agent Enabled)
  - Entry Layer → Intent Recognition Layer → Skill Execution Layer → Secondary Analysis Layer → Response Layer
  - Intelligent intent recognition, multi-step orchestration, knowledge enhancement, failure fallback
- ✅ Complete call chain for Mode 3 (Agent Disabled)
  - Why plugin command mode doesn't enable Agent capabilities
  - Entry Layer → Personality Configuration → Normal AI Dialogue → Callback Layer
  - Pure text output, personality, isolated history, callback mechanism
- ✅ Core differences comparison table (detailed comparison across 12 dimensions)
- ✅ Design philosophy summary (responsibility separation, performance optimization, reliability, flexibility)
- ✅ How to choose which mode to use (decision guide)

**Recommended Chapters**:
- Chapter 2: Mode 1 & 2 Call Chains (Understand Agent workflow)
- Chapter 3: Mode 3 Call Chain (Understand plugin integration principles)
- Chapter 4: Core Differences Comparison Table (Quickly understand the differences between two modes)

**Applicable Scenarios**:
- Need in-depth understanding of internal system mechanisms
- Developing third-party plugin integration features
- Troubleshooting complex interaction issues
- Optimizing performance and response speed

---

### 8. Intent Recognition Prompt Configuration Guide
**File**: `Kilacraft-AI-Intent-Recognition-Prompt-Configuration-Guide.md`  
**Size**: ~23 KB  
**Content**:
- ✅ Intent recognition system architecture and configuration file structure
- ✅ Response format specification (single intent, invalid intent, multi-step task)
- ✅ Decision rules details (when to use single/multi-step/return invalid)
- ✅ Critical constraint rules (placeholder usage, reference resolution, vague instruction handling)
- ✅ Example library (single intent examples, multi-step examples, invalid intent examples)
- ✅ Special scenario handling (continuous conversation, conflicting intents, missing parameters)
- ✅ Output quality requirements and JSON format specification
- ✅ Difference from personality system explanation
- ✅ Optimization strategies and common problem troubleshooting
  **Recommended Chapters**:
- Chapter 2: Response Format Specification (Understand three response types)
- Chapter 4: Critical Constraint Rules (Avoid common errors)
- Chapter 6: Difference from Personality System (Important concept distinction)
- Chapter 8: Optimization Strategies (Improve recognition accuracy)
---

### 9. Plugin Command Mode Detailed Guide

**File**: `Kilacraft-AI-Plugin-Command-Mode-Detailed-Guide.md`  
**Size**: ~25 KB  
**Content**:

- ✅ Three interaction modes comparison analysis
- ✅ Why plugin command mode doesn't enable Agent capabilities
- ✅ Command format and parameter details
- ✅ Callback mechanism deep dive
- ✅ **Callback method optimization best practices** (key techniques to avoid blocking main thread)
- ✅ Complete Java code integration examples
- ✅ MythicMobs configuration-driven plugin integration examples
- ✅ Personality system configuration details
- ✅ History isolation mechanism
- ✅ FAQ and debugging tips

**Recommended Chapters**:
- Chapter 3: Callback Mechanism Details (Understand working principles)
- Chapter 4: Callback Method Optimization Best Practices (Must-read! Avoid server lag)
- Chapter 5: Complete Integration Examples (Can be referenced directly)
- Chapter 8: FAQ (Quick problem solving)

**Applicable Scenarios**:
- Developing third-party plugin integration features
- Implementing NPC intelligent dialogue
- Need to process AI responses asynchronously
- Troubleshooting callback execution issues

---

## 🔍 Find Documents by Topic
### Installation and Configuration
- [Server Owner's Guide - Quick Start](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-quick-start5-minute-getting-started&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Server Owner's Guide - Advanced Configuration Details](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-advanced-configuration-details&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Feature Usage
- [Server Owner's Guide - Core Feature Showcase](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-core-feature-showcase&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Bukkit API Reference Manual](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Personality System Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Personality-System-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Intent Recognition Prompt Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Intent-Recognition-Prompt-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Knowledge Base Management
- [Knowledge Base Enhancement Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Server Owner's Guide - Knowledge Base Enhancement](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23%EF%B8%8F-knowledge-base-enhancementrag-retrieval&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Developer Integration
- [Skill SPI Integration Document](./Skill-SPI-Integration-Document)
- [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide) (Complete guide for third-party plugin integration)
- [System Architecture Details](./System-Architecture-Details) (Deep understanding of three interaction modes)
- [Server Owner's Guide - Plugin Command Mode Introduction](./Server%20Owner%20Guide#4-plugin-command-mode--personality-system-advanced-features)
### Performance Optimization
- [Server Owner's Guide - Performance and Resource Usage](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-performance-and-resource-usage&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Knowledge Base Enhancement Guide - Performance Optimization](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md%23-performance-optimization&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Bukkit API Reference Manual - Performance Optimization Suggestions](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md%23%EF%B8%8F-performance-optimization-suggestions&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Troubleshooting
- [Server Owner's Guide - Troubleshooting](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-troubleshooting&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Bukkit API Reference Manual - Troubleshooting](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md%23-troubleshooting&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Knowledge Base Enhancement Guide - Troubleshooting](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md%23-troubleshooting&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
---
## 📊 Document Statistics
| Category | Number of Documents | Total Size |
|----------|--------------------|------------|
| Server Owner Documents | 2 | ~55 KB |
| Developer Documents | 2 | ~58 KB |
| Technical Reference | 5 | ~95 KB |
| **Total** | **9** | **~208 KB** |
---
## 💡 Usage Suggestions
### New Users
1. First read Chapters 1-3 of the [Server Owner's Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
2. Complete installation and configuration according to the "Quick Start"
3. Refer to other chapters as needed
### Advanced Users
1. Deeply read the advanced configuration chapter of the [Server Owner's Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
2. Learn the [Personality System Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Personality-System-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) to customize AI behavior
3. Use the [Knowledge Base Enhancement Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) to optimize retrieval results
### Plugin Developers
1. Must read the [Skill SPI Integration Document](./Skill-SPI-Integration-Document)
2. Must read the [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide) (Third-party plugin integration guide)
3. Read [System Architecture Details](./System-Architecture-Details) to understand three interaction modes
4. Check the [Bukkit API Reference Manual](./Bukkit-API-Reference-Manual) to learn about available APIs
---
## 📝 Document Maintenance
- **Last Updated**: 2026-04-05
- **Maintainer**: Zm_Mmm
- **Feedback Channels**: [GitHub Issues](sslocal://flow/file_open?url=https%3A%2F%2Fgithub.com%2FZm-Mmm%2FKilacraft-AI%2Fissues&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | [Gitee Issues](sslocal://flow/file_open?url=https%3A%2F%2Fgitee.com%2Fzm_mmm%2Fkilacraft-ai%2Fissues&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
  If you find document errors or need to add content, please submit an Issue or Pull Request!
---
> **Note**: This document will be continuously maintained with project updates. It is recommended to check for the latest version regularly.