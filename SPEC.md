# Metaplus Specification

This document defines the normative rules and invariants of the Metaplus platform.

It describes **what must remain true** across implementations.
Module structure and packaging decisions belong in `ARCHITECTURE.md`.

---

## 1. Scope and Authority

The following sources are authoritative, in this order:

1. JSON Schemas for field-level contracts
2. `SPEC.md` for platform invariants and semantic rules
3. `ARCHITECTURE.md` for module boundaries and dependency design
4. `AGENTS.md` for project mission and contribution workflow

If a rule conflicts with implementation, the implementation should be treated as incomplete until the contract is clarified or updated explicitly.

---

## 2. Core Concepts

### 2.1 MetaplusDoc

All canonical metadata is represented as a `MetaplusDoc`.

### 2.2 FQMN

Every canonical object is identified by a fully qualified metadata name:

`<domain>:<system>:<instance>:<entity>`

FQMN always has exactly four colon-separated parts.

The `domain` part may itself use dot-separated taxonomy, such as `data.table`.

The `domain` part must not use `/` as an internal separator.

### 2.3 Source Truth and Derived Meaning

Metaplus separates synchronized source facts from derived interpretation.

---

## 3. Canonical Document Contract

A `MetaplusDoc` has four top-level sections:

- `idea`
- `meta`
- `plus`
- `info`

### 3.1 `idea`

`idea` contains stable identity information.

It must include the FQMN and its decomposed parts as defined by schema.

### 3.2 `meta`

`meta` contains source-aligned metadata synchronized from authoritative systems.

Rules:

- it represents facts from the source system
- it must not be silently rewritten by derived workflows
- it must remain consistent with the source system contract

### 3.3 `plus`

`plus` contains derived, human-authored, or AI-generated enrichments.

Rules:

- it may extend understanding of `meta`
- it must not replace or blur the meaning of `meta`
- it must remain schema-defined when persisted

### 3.4 `info`

`info` contains operational bookkeeping associated with the document itself, such as internal identifiers and version tracking.

It is not the place for general workflow state or orchestration state.

---

## 4. Identity Rules

FQMN is the platform identity contract.

Rules:

1. An FQMN must be globally unique within Metaplus.
2. An FQMN should remain aligned with the external source identity.
3. An FQMN must not be reused for a different source object.
4. Cross-system linking, synchronization, runtime tracking, and orchestration should all use FQMN as the primary logical key.

### 4.1 Rename Rules

Rules:

1. Metaplus must not perform implicit cross-domain rename cascade.
2. If a rename affects multiple domains, the caller must issue the related explicit changes.
3. Runtime sidecar data keyed by FQMN is not required to migrate during rename.
4. A new FQMN may start with fresh sidecar state.
5. Old sidecar state may be retained temporarily, but it is not the live state of the new FQMN.

Example:

`data.table:mysql:main:warehouse.sales.orders`

---

## 5. Ownership Rules

### 5.1 `meta` ownership

`meta` is owned by authoritative-source synchronization flows.

That usually means:

- external systems define the source facts
- syncers and source-aligned backend flows ingest those facts

### 5.2 `plus` ownership

`plus` is owned by platform enrichment flows, human authors, and AI-assisted processes.

### 5.3 Runtime ownership

Execution state, scheduling state, lock state, metric state, and similar operational concerns should be treated as runtime data, not as canonical metadata semantics.

---

## 6. Schema Rules

JSON Schema is the source of truth for persisted structure.

Rules:

1. No persisted field may exist without schema.
2. No silent contract change is allowed.
3. Additive evolution is the default path.
4. Breaking changes require an explicit migration or versioning strategy.
5. Validation should happen as close to system boundaries as practical.

---

## 7. Synchronization Rules

Metaplus must support both:

- scheduled synchronization
- near-real-time synchronization

Rules:

1. Source metadata must be normalized into `MetaplusDoc`.
2. Source facts must land in `meta`.
3. Deletions or removals must be represented explicitly, not inferred silently.
4. Synchronization behavior must remain source-aligned and explainable.

---

## 8. Runtime Sidecar Rules

Runtime state should be separated from canonical metadata when it represents execution rather than meaning.

Examples include:

- last upsert time
- delete markers
- job completion state
- distributed locks
- metrics

Rules:

1. Runtime sidecar data should be keyed by FQMN when it relates to a canonical object.
2. Runtime state must not redefine canonical source truth.
3. Canonical metadata and runtime control state must remain conceptually separate.
4. Runtime sidecar continuity across rename is not guaranteed.
5. Sidecar state may be recreated under the new FQMN rather than migrated.

---

## 9. API and Integration Rules

Rules:

1. APIs should expose explicit contracts.
2. Platform integrations should prefer stable APIs over storage-specific access.
3. External adopters should not need bespoke coupling to internal backend storage.
4. Query, patch, metric, and related shared models should remain consistent across modules.

---

## 10. Change Rules

Any change to the platform should answer these questions explicitly:

1. Does it change schema?
2. Does it affect FQMN stability?
3. Does it move data across the `meta` / `plus` boundary?
4. Does it introduce runtime state into canonical metadata?
5. Does it require migration, reindexing, or compatibility handling?

If the answer to any of these is yes, the change must be documented before or together with implementation.

---

## 11. Conformance

An implementation is conformant when it:

- respects the `MetaplusDoc` contract
- respects FQMN identity rules
- preserves the `meta` / `plus` separation
- avoids schema drift
- keeps runtime state separate from canonical meaning
- updates schemas, docs, and tests together when contracts change
