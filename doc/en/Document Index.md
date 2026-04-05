# Kilacraft-AI Document Index
> **Version**: v1.4.0  
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
| [Skill SPI Integration Document](sslocal://flow/file_open?url=.%2FKilacraft-AI-Skill-SPI-Integration-Document.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | How to develop custom skills for Kilacraft-AI | Plugin Developers |
### 📖 Technical Reference Documents
| Document Name | Description | Target Audience |
|--------------|-------------|----------------|
| [Bukkit API Reference Manual](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Detailed descriptions and examples of 44+ built-in APIs | Server Owners, Developers |
| [Personality System Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Personality-System-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Management and customization methods for multiple personalities | Server Owners, Advanced Users |
| [Knowledge Base Enhancement Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | Usage and optimization techniques for RAG knowledge base | Server Owners, Content Creators |
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
- ✅ Detailed explanation of intelligent segmentation algorithm and three-level scoring mechanism
- ✅ Configuration option descriptions (max_chunks, similarity_threshold, etc.)
- ✅ Advanced usage:
    - Multilingual knowledge base
    - Permission-controlled knowledge base
    - Time-sensitive knowledge
    - Versioned knowledge base
- ✅ Performance optimization suggestions
- ✅ Troubleshooting guide
  **Recommended Chapters**:
- Chapter 2: Document Writing Specifications (Key to improving retrieval accuracy)
- Chapter 3: Detailed Explanation of Retrieval Mechanism (Understand working principles)
- Chapter 5: Performance Optimization
---
## 🔍 Find Documents by Topic
### Installation and Configuration
- [Server Owner's Guide - Quick Start](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-quick-start5-minute-getting-started&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Server Owner's Guide - Advanced Configuration Details](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-advanced-configuration-details&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Feature Usage
- [Server Owner's Guide - Core Feature Showcase](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-core-feature-showcase&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Bukkit API Reference Manual](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Personality System Configuration Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Personality-System-Configuration-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Knowledge Base Management
- [Knowledge Base Enhancement Guide](sslocal://flow/file_open?url=.%2FKilacraft-AI-Knowledge-Base-Enhancement-Guide.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Server Owner's Guide - Knowledge Base Enhancement](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23%EF%B8%8F-knowledge-base-enhancementrag-retrieval&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
### Developer Integration
- [Skill SPI Integration Document](sslocal://flow/file_open?url=.%2FKilacraft-AI-Skill-SPI-Integration-Document.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
- [Server Owner's Guide - Plugin Command Mode](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-plugin-command-mode-complete-example-advanced-feature&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
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
| Developer Documents | 1 | ~33 KB |
| Technical Reference | 3 | ~57 KB |
| **Total** | **6** | **~145 KB** |
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
1. Must read the [Skill SPI Integration Document](sslocal://flow/file_open?url=.%2FKilacraft-AI-Skill-SPI-Integration-Document.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
2. Refer to [Server Owner's Guide - Plugin Command Mode](sslocal://flow/file_open?url=.%2FKilacraft-AI-Server-Owner%27s-Guide.md%23-plugin-command-mode-complete-example-advanced-feature&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
3. Check the [Bukkit API Reference Manual](sslocal://flow/file_open?url=.%2FKilacraft-AI-Bukkit-API-Reference-Manual.md&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) to learn about available APIs
---
## 📝 Document Maintenance
- **Last Updated**: 2026-04-05
- **Maintainer**: Zm_Mmm
- **Feedback Channels**: [GitHub Issues](sslocal://flow/file_open?url=https%3A%2F%2Fgithub.com%2FZm-Mmm%2FKilacraft-AI%2Fissues&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=) | [Gitee Issues](sslocal://flow/file_open?url=https%3A%2F%2Fgitee.com%2Fzm_mmm%2Fkilacraft-ai%2Fissues&flow_extra=eyJsaW5rX3R5cGUiOiJjb2RlX2ludGVycHJldGVyIn0=)
  If you find document errors or need to add content, please submit an Issue or Pull Request!
---
> **Note**: This document will be continuously maintained with project updates. It is recommended to check for the latest version regularly.