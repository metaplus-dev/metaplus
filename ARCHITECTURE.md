# Metaplus Architecture

This document defines the system structure, module boundaries, dependency rules, and major interaction flows.

It describes both:

- the **current repository state**, and
- the **target architecture** that future modules should follow

Normative data rules belong in `SPEC.md`, not here.

---

## 1. Architecture Goals

Metaplus should provide a clear structure for:

- schema-defined metadata contracts
- stable cross-system identity
- source-aligned synchronization
- derived enrichment and computation
- client and AI-agent access
- incremental evolution without boundary erosion

---

## 2. High-Level Structure

Metaplus is organized into four layers:

### 2.1 Contract Layer

Defines shared models and cross-module semantics.

Primary module:

- `metaplus-core`

### 2.2 Backend Infrastructure Layer

Provides shared backend storage and runtime capabilities.

Primary module:

- `metaplus-backend-lib`

### 2.3 Capability and Access Layer

Provides external integration, orchestration, and protocol adaptation.

Modules:

- `metaplus-client`
- `metaplus-calculator`
- `metaplus-mcp-gateway`
- `metaplus-syncer-xxx`

### 2.4 Application Assembly Layer

Composes deployable services such as APIs, schedulers, or protocol endpoints.

These server modules are not yet formalized in the current repository, but they are expected to assemble the lower layers rather than redefine them.

---

## 3. Module Boundaries

## 3.1 `metaplus-core`

**Role**

- Canonical platform models
- JSON Schema resources
- Shared query, patch, metric, search, and exception types
- Common utilities and validation helpers

**Boundary**

- Must remain storage-agnostic
- Must not contain protocol adapters or business orchestration
- Must be the most stable module in the system

**Status**

- Implemented

---

## 3.2 `metaplus-backend-lib`

**Role**

- Shared backend support for server-side modules
- Elasticsearch access and response handling
- Runtime sidecar storage, locking, and metrics

**Boundary**

- Depends on `metaplus-core`
- Encapsulates backend storage details
- Must not become a client SDK or source adapter layer

**Design decision**

- The current backend storage is **Elasticsearch only**
- The architecture should avoid premature multi-storage abstraction until there is a validated need

**Status**

- Implemented

---

## 3.3 `metaplus-client`

**Role**

- Stable client-facing wrapper around platform APIs
- Request construction, response parsing, error normalization, and future authentication hooks

**Boundary**

- Should depend on `metaplus-core`
- Should target platform APIs, not Elasticsearch internals
- Should not depend on `metaplus-backend-lib`

**Status**

- Planned

---

## 3.4 `metaplus-calculator`

**Role**

- Create, schedule, manage, and orchestrate calculation tasks
- Dispatch work to one or more compute engines
- Track execution state and write results back to the platform

**Boundary**

- Owns calculation orchestration, not source synchronization
- May use runtime state and metrics
- Should not expand into a generic workflow platform without a proven need

**Status**

- Planned

---

## 3.5 `metaplus-mcp-gateway`

**Role**

- Expose Metaplus capabilities through the MCP protocol for AI agents
- Map platform operations into MCP resources, tools, and prompts

**Boundary**

- Should prefer `metaplus-client` as its integration path
- Should not access Elasticsearch directly
- Acts as a protocol adapter, not as a source of business truth

**Status**

- Planned

---

## 3.6 `metaplus-syncer-xxx`

**Role**

- Integrate with a specific metadata source
- Extract source metadata
- Normalize it into `MetaplusDoc`
- Synchronize source-aligned `meta` into the platform

**Boundary**

- One source integration per module, such as `metaplus-syncer-mysql`
- Should prefer platform APIs via `metaplus-client`
- Should not access Elasticsearch directly
- Should not take on calculation orchestration responsibilities

**Status**

- Planned

---

## 4. Dependency Rules

Recommended dependency direction:

```text
metaplus-core
    ↑
    ├── metaplus-backend-lib
    ├── metaplus-client
    ├── metaplus-calculator
    ├── metaplus-mcp-gateway
    └── metaplus-syncer-xxx

server modules
    ├── metaplus-core
    ├── metaplus-backend-lib
    └── selected capability modules
```

Additional guidance:

- `metaplus-mcp-gateway` should preferably call platform APIs through `metaplus-client`
- `metaplus-syncer-xxx` should preferably write through `metaplus-client`
- server-side implementations of `metaplus-calculator` may depend on `metaplus-backend-lib`

### Prohibited directions

- `metaplus-core` must not depend on higher-level modules
- `metaplus-client` must not depend on `metaplus-backend-lib`
- `metaplus-syncer-xxx` must not access Elasticsearch directly
- `metaplus-mcp-gateway` must not access Elasticsearch directly

---

## 5. Data Placement

### 5.1 Canonical Metadata

Canonical metadata is stored as `MetaplusDoc`.

This document is the shared platform unit for identity, source facts, enrichments, and metadata bookkeeping.

Its stable identifier is the FQMN, using the format:

`<domain>:<system>:<instance>:<entity>`

The `domain` part may use dot-separated taxonomy, for example `data.table`.

### 5.2 Runtime Sidecar Data

Operational state should be kept outside canonical metadata when it represents execution or control flow, for example:

- sync timestamps
- delete markers
- job completion state
- locks
- metrics

This sidecar state should be keyed by FQMN and managed by backend infrastructure.

Rename implications:

- sidecar data is not automatically migrated when FQMN changes
- a renamed object may start with new sidecar state under the new FQMN
- old sidecar entries may be retained temporarily or garbage-collected
- backend infrastructure must not infer cross-domain rename intent

### 5.3 Storage Decision

The current backend storage is Elasticsearch.

That decision is part of the current architecture, not a universal platform rule.

---

## 6. Major Interaction Flows

### 6.1 Metadata Synchronization

```text
Metadata Source
  -> metaplus-syncer-xxx
  -> normalize and validate into MetaplusDoc
  -> platform API / client
  -> backend infrastructure
  -> Elasticsearch
  -> runtime sidecar update
```

If a source-side rename affects multiple objects, the syncer or orchestration layer should issue explicit changes for each affected object. Backend and sidecar layers should not perform hidden cross-domain rename cascade.

### 6.2 Calculation Flow

```text
Changed or pending documents
  -> metaplus-calculator
  -> scheduling and dispatch
  -> compute engine
  -> write-back to platform
  -> runtime state and metric update
```

### 6.3 AI Agent Access

```text
AI Agent
  -> metaplus-mcp-gateway
  -> metaplus-client
  -> platform API
  -> query, search, patch, or operation result
```

---

## 7. Current State and Evolution

### 7.1 Modules clearly present today

- `metaplus-core`
- `metaplus-backend-lib`

### 7.2 Modules planned for incremental delivery

- `metaplus-client`
- `metaplus-calculator`
- `metaplus-mcp-gateway`
- `metaplus-syncer-xxx`

This staged approach is reasonable.

The key requirement is not that every module must exist now, but that every new module must preserve the documented boundaries and dependency directions.

---

## 8. Recommended Evolution Order

1. Stabilize `metaplus-core` and JSON Schemas
2. Continue strengthening `metaplus-backend-lib`
3. Define platform APIs and implement `metaplus-client`
4. Add high-value `metaplus-syncer-xxx` modules
5. Introduce `metaplus-calculator`
6. Expose mature capabilities through `metaplus-mcp-gateway`
