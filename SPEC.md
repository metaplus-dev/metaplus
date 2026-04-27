# Metaplus Specification

This document defines the normative rules and invariants of the Metaplus platform.

It describes **what must remain true** across implementations.
Module structure and packaging decisions belong in `ARCHITECTURE.md`.

---

## 1. Scope and Authority

The following sources are authoritative, in this order:

1. JSON Schemas for field-level contracts, including built-in schema resources and schemas carried by domain definitions
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
- `edit`

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

### 3.4 `edit`

`edit` contains document-level edit bookkeeping for `meta` and `plus`, such as section versions, creation timestamps, update timestamps, and actors.

It is not the place for general workflow state, orchestration state, or backend-specific storage metadata.

---

## 4. Identity Rules

FQMN is the platform identity contract.

Rules:

1. An FQMN must be globally unique within Metaplus.
2. An FQMN should remain aligned with the external source identity.
3. An FQMN must not be reused for a different source object.
4. Cross-system linking, synchronization, runtime tracking, and orchestration should all use FQMN as the primary logical key.

### 4.1 Identity Derivation Rules

Rules:

1. `idea` is the identity root and must be derivable without reading persisted `meta` or `plus`.
2. `meta` and `plus` may reference `idea`, but `idea` must not depend on `meta` or `plus`.
3. Identity generators may use only declared source-identity inputs and constants.
4. Identity generation must be deterministic for the same declared inputs.
5. Rename behavior must remain explicit and explainable from the identity contract.

### 4.2 Rename Rules

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
2. Domain-specific schema may be supplied either by a built-in `schemaRef` or by an inline schema embedded in the domain definition.
3. Inline schema must be stored as JSON object content, not as an opaque string payload.
4. Built-in shared envelopes such as `MetaplusDoc` should remain stable reusable schemas.
5. No silent contract change is allowed.
6. Additive evolution is the default path.
7. Breaking changes require an explicit migration or versioning strategy.
8. Validation should happen as close to system boundaries as practical.

### 6.1 Domain Contract Validation

Rules:

1. A domain definition must declare the identity contract, storage contract, and schema entry used by that domain.
2. Storage may be drafted before schema, but a domain must not become active until schema validity and schema-storage cross-validation both pass.
3. Schema is authoritative for logical field existence and basic type.
4. Storage contracts must not redefine logical field existence or basic types already defined by schema.
5. Storage references to fields must resolve against the effective schema of the domain.

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
5. Metaplus must not assume full Elasticsearch and OpenSearch feature parity.
6. Backend storage integrations should use only the documented ES-compatible subset adopted by Metaplus.
7. Any newly introduced engine feature must be verified against both supported engines before it becomes part of the platform contract.
8. If a feature is not verified on both engines, it should be treated as unsupported by default.

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
- preserves the responsibilities of `idea`, `meta`, `plus`, and `edit`
- avoids schema drift
- keeps runtime state separate from canonical meaning
- updates schemas, docs, and tests together when contracts change
