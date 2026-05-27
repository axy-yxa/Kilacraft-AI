# Kilacraft-AI Admin Features Guide

> **Last Updated**: 2026-05-22
> **Description**: Complete usage guide for Kilacraft-AI's admin features — server health monitoring, player behavior analysis, and audit log querying — including configuration reference and scenario examples

## Overview

Kilacraft-AI's admin features provide an intelligent monitoring and analysis system designed for Minecraft server administrators. Through deep integration between AI and the Spark profiling plugin, it delivers real-time health monitoring, smart diagnostics, and player behavior analysis.

### Key Features

1. **AI-Powered Diagnostics**: Uses reasoning models for deep analysis, automatically identifying root causes of performance issues
2. **Zero-Config Monitoring**: A daemon thread runs 24/7 in the background with automatic anomaly detection and alerting
3. **Precise Profiling**: Spark Profiler's self-time analysis pinpoints method-level hotspots to identify problematic plugins and trigger paths
4. **Natural Language Interface**: No need to memorize commands — describe what you need in plain language to query alerts, player data, and more

### System Requirements

- **Recommended Plugin**: Spark (profiling plugin, soft dependency)
- **Reasoning Model**: A valid API key must be configured in `admin.yml` (without it, health diagnostics are unavailable; other features remain unaffected)
- **Full Dependencies**: The health monitoring daemon requires **both Spark and a reasoning model API key** to activate. If either is missing, the daemon won't start, but manual profiling commands and historical alert queries remain available (provided Spark is present)
- **Server Environment**: Supports Paper/Spigot and other major server cores
- **Network**: Server must have outbound internet access (for Spark Profiler data upload and reasoning model API calls)

#### Servers with Built-in Spark

The following server cores include Spark natively — no additional plugin installation needed:

| Server Core | Built-in Spark Since | Notes |
|-------------|----------------------|--------|
| **Paper** | 1.21+ | Paper officially bundled Spark from 1.21, replacing the deprecated Timings |
| **Folia** | 1.21+ | Paper-based, inherits built-in Spark |
| **Purpur** | 1.19.1+ | Paper-based, bundled Spark earlier |
| **Leaf** | 1.21+ | Paper/Gale fork, inherits built-in Spark |
| **Pufferfish** | 1.19+ | Paper-based, includes Spark |

> **Tip**: On the servers above, simply configure the reasoning model API key to enable full health monitoring. On servers without built-in Spark (e.g. Spigot), install the [Spark plugin](https://spark.lucko.me/) manually.

## Feature Overview

| Module | Purpose | Usage | Permission |
|--------|---------|-------|------------|
| **Server Health Monitoring** | Real-time performance monitoring, smart diagnostics & report review | Commands + natural language alert/report queries | `kilacraft.admin.health` |
| **Player Behavior Analysis** | Player activity and social relationship analysis | Natural language | `kilacraft.admin.player` |
| **Audit Log Query** | AI skill usage tracking | Natural language | `kilacraft.admin.audit` |

## 1. Server Health Monitoring

### 1.1 Monitoring Metrics & Alerts

The daemon thread polls Spark API every 10 seconds, checking the following metrics. Any threshold breach triggers an alert:

| Metric | Description | Normal Range | Alert Threshold |
|--------|-------------|--------------|-----------------|
| **TPS** | Ticks per second (1-min window) | 20.0 | < 15 |
| **MSPT** | Milliseconds per tick (10s/1min window) | < 50ms | 10s window Max > 50ms (requires 3 consecutive confirmations) OR 1min window P95 > 50ms |
| **CPU** | Process CPU usage (1-min window) | < 70% | > 80% |

Once an alert triggers, the daemon automatically launches Spark Profiler sampling, then calls the reasoning model to generate a diagnostic report upon completion.

#### Server Activity Metrics (Auxiliary Diagnostics)

Spark Profiler only captures CPU activity — it can't detect disk I/O, network, or other non-CPU bottlenecks. To fill this gap, the following metrics are collected during sampling and injected into the AI diagnostic prompt:

- **Chunk Loading Stats**: Chunk load counts per world during sampling. High chunk load increments + low CPU hotspots are a classic signal of disk I/O bottlenecks
- **Player Activity Distribution**: Online player counts and movement per world, helping identify the source of performance pressure

Diagnostic reports also display GC details, memory usage, entity distribution, and other metadata.

### 1.2 Automatic Diagnostics

#### Workflow

```
Poll Spark API every 10s → Metric exceeds threshold → Launch Profiler sampling (30s)
    → Download profiling data → Stream-parse call stacks → AI reasoning model diagnosis → Generate report → Notify admins
```

#### Protection Mechanisms

- **MSPT Consecutive Confirmation**: MSPT max uses the 10-second window for detection, requiring 3 consecutive threshold breaches before triggering — effectively filtering transient spikes
- **Unified Cooldown**: After one analysis completes, no new analysis triggers within 5 minutes (regardless of metric type)
- **Rate Limiting**: Sliding time window limits automatic analysis frequency (default: max 5 per hour) to prevent resource exhaustion
- **Mutex Lock**: Only one analysis task runs at a time; new alerts won't interrupt an ongoing analysis

### 1.3 Manual Profiling

For proactive troubleshooting, use manual diagnostic commands (requires Spark to be available):

**Start profiling:**
```bash
/kilacraft profile start [seconds]
```
- **Seconds**: Sampling duration, range 30-120 seconds, default 60 seconds
- A diagnostic report is automatically generated upon completion

**View profiling status:**
```bash
/kilacraft profile status
```

**Abort profiling and discard data:**
```bash
/kilacraft profile stop
```

> If the admin goes offline during manual profiling, the system automatically aborts the sampling.

### 1.4 Diagnostic Reports

#### Trigger Conditions

1. **Automatic**: Generated when the daemon detects a performance anomaly
2. **Manual**: Generated after `/kilacraft profile start` sampling completes

#### Report Contents

- **§1. Server Status Overview**: Trigger reason (automatic mode only), TPS/MSPT/CPU/memory, online player & entity stats, GC details
- **§2. Plugin Performance Analysis**: Installed plugin timing (quickly identify problematic plugins) + Top hotspot method trigger paths (pinpoint specific causes). All percentages are self time
- **§3. AI Diagnostic Conclusions**: Root cause analysis, optimization suggestions, concrete solutions

#### Report Files

Saved in `plugins/Kilacraft-AI/reports/`, filename format `health_report_{auto|manual}_YYYY-MM-DD_HH-mm-ss.md` (`auto` for automatic diagnostics, `manual` for manual profiling). Reports are kept permanently and never auto-deleted.

### 1.5 Alert Notifications

#### In-Game Notifications

- **Automatic mode**: Notifies all online admins with `kilacraft.admin.health` permission upon diagnosis completion
- **Manual mode**: Notifies only the command issuer

#### External Notifications (Automatic Mode Only)

After a successful automatic diagnosis, alert notifications can be pushed to external platforms via Discord Webhook or DingTalk group bot. Manually triggered diagnostics do not send external notifications.

Notification content includes: triggering metric and reason, real-time performance snapshot (TPS/MSPT/CPU), AI diagnostic conclusion summary.

> **Note**: Pushed content does not include the full diagnostic report attachment, preventing sensitive information (server version, Spark URL, player names, etc.) from leaking to external platforms. For the full report, log in to the server.

**Channel Comparison:**

| Feature | Discord | DingTalk |
|---------|---------|----------|
| Message Type | Embed card | Markdown text |
| Security Hardening | — | Optional HMAC-SHA256 signing |

> External notifications are not sent when AI diagnosis fails (error-only messages have no diagnostic value).

**Test notification channels:**
```bash
/kilacraft notify test
```
Sends a test message to all configured channels to verify webhook URLs and signing keys.

### 1.6 Historical Alert Query & Report Review

Query historical alert records and diagnostic reports via natural language:

#### Historical Alert Query

**Example queries:**
- "What alerts have there been in the past hour?"
- "Show health alert records from the last 3 days"
- "Which alerts had TPS below 15?"

**Returned data:** Alert record list, statistics aggregated by plugin/metric/time, key metrics like minimum TPS.

#### Diagnostic Report Review

AI can read historical diagnostic report files saved on the server, reviewing complete analysis conclusions.

**Example queries:**
- "List recent diagnostic reports" — Returns report file list (with timestamp, mode, file size)
- "Show the last automatic diagnostic report" — Reads and displays the full report (§1 Status Overview + §2 Plugin Analysis + §3 AI Conclusions)
- "Compare TPS and MSPT changes between these two reports" — Reads multiple reports consecutively for comprehensive analysis
- "Has MythicMobs been causing performance issues all month?" — Queries alert stats to locate suspicious plugin, then reads specific reports to confirm

> When reading reports, AI automatically strips the reasoning chain-of-thought (no value for machines), keeping only the final diagnostic conclusions for faster responses. To view the full reasoning process, open the report files directly in `plugins/Kilacraft-AI/reports/`.

## 2. Player Behavior Analysis

### 2.1 Overview

Query your server's player ecosystem through natural language:

- **Online Trends**: Player login/logout time distribution
- **Activity Rankings**: Most active player leaderboard
- **New Player Influx**: New player join statistics
- **Profile Coverage**: AI profile analysis coverage rate
- **Social Insights**: Player social network analysis

### 2.2 Usage

#### Online Population Trends

**Example:** "What's the player online trend over the past week?"

Returns login/logout counts within the time range, aggregated by hour or day.

#### Active Player Rankings

**Example:** "Show the most active players leaderboard"

Returns player login count rankings, playtime stats, last login time.

#### New Player Influx

**Example:** "How many new players joined this week?"

Returns new player count and time distribution trend.

#### Profile Analysis Coverage

**Example:** "How many players have AI profile analysis?"

Returns total player count, analyzed/pending counts, coverage percentage.

#### Social Graph Insights

**Example:** "Show player social network analysis"

Returns total social relations, average relationship strength, isolated player list.

## 3. Audit Log Query

### 3.1 Overview

Query AI skill usage through natural language:

- **Usage Records**: Who used what skill, with parameters and results
- **Success Rate**: Whether skill executions succeeded
- **Performance Stats**: Skill execution duration rankings
- **Error Tracking**: Failed execution records

### 3.2 Usage

#### Query Skill Execution Logs

**Example:** "What skills has player Steve used?", "Show execution logs for the server_health skill"

Returns skill name, executing player, success/failure status, execution duration, timestamp.

#### Skill Usage Statistics

**Example:** "Show skill usage statistics leaderboard"

Returns skill usage count rankings, success/failure counts, average execution duration.

#### Error Log Query

**Example:** "Show failed skill execution records"

Returns failed execution records, error messages, time distribution.

## 4. Configuration Guide

All configuration is in `plugins/Kilacraft-AI/admin.yml`. Supports hot-reload via `/kilacraft reload` (configs requiring a daemon restart are handled automatically).

### 4.1 Daemon Thread & Alert Thresholds

```yaml
health_guardian:
  enabled: true                    # Enable daemon thread
  interval_seconds: 10             # Polling interval (seconds)
  cooldown_minutes: 5              # Post-analysis cooldown (minutes)
  auto_profiler_timeout: 30        # Auto-mode sampling duration (seconds)
  max_auto_analysis_per_window: 5  # Max auto analyses per sliding window
  auto_analysis_window_minutes: 60 # Sliding window duration (minutes)
  mspt_consecutive_threshold: 3    # MSPT max consecutive confirmation count

  alerts:
    tps_threshold: 15              # TPS 1-minute window threshold
    mspt_max_threshold: 50         # MSPT max threshold (ms, 10s window)
    mspt_p95_threshold: 50         # MSPT P95 threshold (ms, 1min window)
    cpu_threshold: 80              # CPU process usage threshold (percent)
```

**Threshold Tuning by Server Size:**

| Server Size | TPS Threshold | MSPT Threshold | CPU Threshold |
|-------------|---------------|----------------|---------------|
| Small (1-20 players) | 18 | 40 | 70 |
| Medium (20-100 players) | 15 | 50 | 80 |
| Large (100+ players) | 12 | 60 | 85 |

### 4.2 Reasoning Model

```yaml
thinking_model:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-reasoner"
  max_tokens: 4096
  timeout_seconds: 120
```

**Supported Reasoning Models:**

| Provider | Recommended Models |
|----------|-------------------|
| DeepSeek | `deepseek-reasoner` |
| OpenAI | `o3`, `o4-mini`, `o1` |
| SiliconFlow | `deepseek-ai/DeepSeek-R1` |
| OpenRouter | `deepseek/deepseek-r1`, `openai/o3-mini` |
| Google Gemini | `gemini-2.5-flash-preview-05-20` (thinking mode) |

### 4.3 External Notifications

```yaml
notification:
  enabled: false                   # Enable external notifications
  channels:
    - type: discord                # Discord Webhook
      webhook_url: ""
    - type: dingtalk               # DingTalk group bot
      webhook_url: ""
      secret: ""                   # Signing key (optional, recommended)
```

### 4.4 Sampling Strategy & Resource Limits

**Manual sampling duration recommendations:**
- Routine monitoring: 60 seconds
- Issue investigation: 90-120 seconds
- Performance testing: 120 seconds

**Profiler Data Protection:**
```yaml
health_guardian:
  max_profiler_download_bytes: 52428800  # 50MB threshold
  download_when_exceeded: false          # Whether to continue downloading past threshold
```

## 5. Permission Management

| Permission | Scope | Default |
|------------|-------|---------|
| `kilacraft.admin.health` | Server health monitoring (manual profiling + alert notifications + historical alerts + report review + external notification testing) | OP |
| `kilacraft.admin.player` | Player behavior analysis | OP |
| `kilacraft.admin.audit` | Audit log query | OP |
| `kilacraft.admin.*` | All admin features | OP |

Fine-grained control via permission plugins (e.g. LuckPerms):
```
/lp user <player> permission set kilacraft.admin.health true
```

## 6. Scenario Examples

### 6.1 Real-Time Performance Diagnostics

#### Scenario 1: Server Lag — Immediate Diagnosis

When players report lag or you notice it yourself, immediately start manual profiling to pinpoint the issue.

```bash
/kilacraft profile start 90        # Standard investigation: 90s sampling
/kilacraft profile start 30        # Urgent scenario: 30s quick triage
```

After sampling completes, the system auto-generates a diagnostic report. Jump to §2 (plugin timing + hotspot method trigger paths) to identify the lag cause, then follow §3 AI suggestions to take action.

#### Scenario 2: Intermittent / Time-Specific Performance Degradation

For performance issues that aren't constant but occur under specific conditions, there are two strategies:

**Strategy A: Passive Capture (Daemon Auto-Monitoring)**

Ensure `health_guardian.enabled: true`. You can lower alert thresholds to increase sensitivity. When the issue recurs, the daemon automatically triggers diagnostics and generates a report.

**Strategy B: Proactive Profiling (Manual Sampling During Problem Period)**

Start manual profiling during the performance degradation window: `/kilacraft profile start 120`, then compare peak vs. off-peak reports to locate the bottleneck.

### 6.2 Change Impact Assessment

> Leveraging AI's report reading capability (`list_reports` + `read_report`), AI can read historical diagnostic reports and intelligently compare them — turning "it feels faster" into precise quantitative assessment.

#### Scenario 3: Before & After Plugin Install / Upgrade

**Steps:** Start sampling before the change to generate a baseline report → Apply the change → Sample again after → Ask AI to quantify the comparison

**Example dialogue:**
- "I upgraded MythicMobs yesterday, check if performance improved"
- AI automatically reads both pre- and post-upgrade reports, comparing §2 plugin timing percentages and §1 MSPT changes
- AI output: "Before upgrade, MythicMobs occupied 12.3%; after upgrade, dropped to 3.1%. MSPT P95 decreased from 45ms to 15ms — significant improvement"

#### Scenario 4: Server / Config Change Validation

**Steps:** Same as Scenario 3. Applies to server core upgrades, JVM parameter tuning, GC configuration optimization, or any other change.

**Example dialogue:**
- "I adjusted GC parameters yesterday, check the results"
- AI reads reports from before and after the configuration change, comparing §1 GC details
- AI output: "Before config change: G1 Young GC 4 times/min at 35ms avg; after: 2 times/min at 18ms avg. Total GC time reduced by ~60%"

### 6.3 Historical Data Deep Analysis

> The following scenarios leverage AI's report reading capability to deliver insights previously impossible.

#### Scenario 5: Periodic Lag Pattern Mining

**Example dialogue:**
- "The server has been lagging every day for the past three days, help me find the pattern"
- AI automatically: `health_report` to get alert distribution → discovers concentration between 20:00-22:00 → `list_reports` to list reports from that period → reads each via `read_report`
- AI output: "4 alerts over 3 days, all between 20:00-22:00. Common pattern: entity accumulation in a specific chunk (200+ dropped items), suspected mob farm output not being auto-cleaned"

#### Scenario 6: Long-Term Plugin Impact Tracking

**Example dialogue:**
- "Has MythicMobs been causing performance issues for the past month?"
- AI automatically: `health_report` filtered by plugin=MythicMobs → `read_report` for all related reports
- AI output: "8 MythicMobs-related alerts in the past 30 days, concentrated on May 10-15 (5-12% share). After upgrade, dropped to 1-3%. No related alerts in the last two weeks — issue resolved"

#### Scenario 7: Cross-Period Performance Trend Analysis

**Example dialogue:**
- "Compare last week's and this week's server performance trends"
- AI reads multiple reports in chronological order, extracting TPS/MSPT/CPU/memory/GC data
- AI output: "Last week's MSPT P95 average was 35ms, this week dropped to 22ms. GC frequency decreased from once every 2 minutes to once every 5 minutes — overall improving trend"

### 6.4 Player Management & Operations

#### Scenario 8: Player Activity Analysis

- "What's the player online trend over the past week?" — Understand overall activity
- "Show the most active players leaderboard" — Identify core players
- "How's the new player join trend?" — Evaluate new player influx

#### Scenario 9: Social Network Insights

- "Show player social network analysis" — Understand social network density and strength
- "Who are the isolated players?" — Identify players who may need attention

#### Scenario 10: Event Impact Assessment

Query baseline data before an event ("What's the player online trend over the past week"), then query the same time range after the event. Compare online trends and activity changes to evaluate event effectiveness.

### 6.5 Routine Operations

#### Scenario 11: Periodic Health Check

1. Query historical alerts: "Any recent performance alerts?"
2. Check active player status: "What's the recent player online trend?"
3. Monitor external notifications (if configured) to receive anomaly alerts even when offline

## 7. FAQ

### Q1: Why is the server health monitoring feature unavailable?

Check the following conditions (all must be met):
1. Is Spark available? (Paper 1.21+/Folia/Purpur etc. have it built-in; other cores need the Spark plugin installed manually)
2. Is the reasoning model API key configured in `admin.yml`? (cannot be the placeholder `your-api-key`)
3. Can the server access the internet?
4. Is `health_guardian.enabled` set to `true`?

### Q2: What if diagnostic report generation fails?

Possible causes: Invalid or insufficient API key quota, network issues, Spark Profiler data too large. Check server logs for detailed error messages; try increasing `timeout_seconds`.

### Q3: How to clean up historical diagnostic reports?

Reports are saved in `plugins/Kilacraft-AI/reports/`. You can manually delete them or filter by time for cleanup.

### Q4: Will the daemon thread affect server performance?

The daemon is designed to be lightweight: during normal operation it only does in-memory polling with zero storage overhead. It only triggers Profiler analysis when anomalies are detected, with cooldown and rate limiting in place.

### Q5: How to adjust monitoring sensitivity?

Adjust alert thresholds in `admin.yml`: lower thresholds for higher sensitivity, higher thresholds for more tolerance. See §4.1 for recommendations by server size.

### Q6: Not receiving external notifications?

1. Confirm `notification.enabled` is `true`
2. Check that each channel's `webhook_url` is complete
3. If using DingTalk signing, confirm the `secret` matches the bot's security settings
4. Run `/kilacraft notify test` to verify
5. Check console logs for error messages with the `[Notification]` prefix
6. Note: External notifications are only sent when **automatic diagnosis succeeds** — manual diagnostics don't trigger them

### Q7: Daemon not triggering alerts?

1. Check if alert thresholds in `admin.yml` are set too high
2. Confirm the reasoning model API key is configured
3. Verify Spark plugin is running normally
4. Note the cooldown mechanism: no re-trigger within 5 minutes after the last analysis
5. MSPT alerts require 3 consecutive threshold breaches — transient spikes won't trigger

### Q8: How to check system logs for troubleshooting?

Key log identifiers:
- `[Health Monitor]`: Daemon thread, manual profiling, diagnostic analysis logs
- `[Notification]`: External notification logs

Log levels: INFO (normal operations), WARN (attention needed), ERROR (action required).
