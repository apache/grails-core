---
name: codebase-memory-setup
description: Helps the developer set up codebase-memory-mcp locally
license: Apache-2.0
---
<!-- @formatter:off -->
<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements. See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership. The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License. You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied. See the License for the
  specific language governing permissions and limitations
  under the License.
-->

---
name: codebase-memory-setup
description: >
Verify and set up the codebase-memory MCP server for Grails-Core architectural navigation.
Use this skill at the start of any Grails-Core session to detect whether the MCP server is
active, prompt the user to install it if missing, and provide step-by-step configuration
instructions. Trigger whenever working with grails-core and MCP tool availability is unknown,
or when the user asks about faster codebase navigation, architectural mode, or MCP setup.
---

# Codebase Memory MCP: Environment Verification & Setup

This skill governs how to verify, onboard, and configure the `codebase-memory` MCP server when
working in the Grails-Core repository. It enables "Architectural Mode" — a significantly faster
mode of codebase navigation using graph-based memory tools.

---

## Step 1: Detect Operating Mode (Mandatory on Load)

Before doing anything else, check your available tool definitions for the presence of any of
these tools:

- `index_repository`
- `search_graph`
- `trace_call_path`
- `query_graph`

| Condition | Mode |
|---|---|
| Tools **present** | **Architectural Mode** — proceed normally |
| Tools **absent** | **Standard Mode** — proceed to Step 2 |

---

## Step 2: Proactive Onboarding (Standard Mode Only)

If the MCP server is **not** detected, interrupt the current flow (unless addressing a critical
fix) and present the following message to the user:

> "I've detected that this repository is optimized for **Codebase Memory MCP**, which allows me
> to navigate the Grails-Core architecture 90% faster. However, the server is not currently
> running in your environment. Would you like me to help you install it now?"

Wait for the user's response before continuing.

---

## Step 3: Installation (If User Approves)

The package ships as a **static binary** (not npm). Use the appropriate one-liner for the user's
platform, or run it yourself if you have shell access.

**macOS / Linux (bash):**

```bash
curl -fsSL https://raw.githubusercontent.com/DeusData/codebase-memory-mcp/main/install.sh | bash
```

**Windows (PowerShell):**

```powershell
irm https://raw.githubusercontent.com/DeusData/codebase-memory-mcp/main/scripts/setup-windows.ps1 | iex
```

Both scripts download the correct binary for the platform, place it on the PATH, and run
`codebase-memory-mcp install` to auto-configure all detected agents (Claude Code, Zed, Cursor,
etc.) in one step.

> **Note:** `npm install -g codebase-memory-mcp` will not work — ignore any documentation
> suggesting this.

After the script completes, restart the agent environment.

### Index the Repository

Once the server is active, index grails-core by telling the agent:

```
"Index this project"
```

The agent will call `index_repository` and build the knowledge graph automatically.

---

## Step 4: Verify Activation

Confirm that `index_repository`, `search_graph`, `trace_call_path`, and `query_graph` are now
available. If they are, **Architectural Mode** is active — proceed with the original task.

If tools are still missing, ask the user to confirm the install script completed without errors
and that the agent environment was fully restarted.