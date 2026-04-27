# Metaplus Agents

Metaplus — A Plus Replica of Metadata Truth

This document defines the project mission and the working rules for contributors, including AI agents.
It does not define the normative system contract or module structure.

- For system rules: see `SPEC.md`
- For module boundaries and dependencies: see `ARCHITECTURE.md`
- For field-level contracts: see built-in JSON Schemas in `metaplus-core` and schemas referenced or embedded by domain definitions

---

## 1. Mission

Metaplus is an open and unified metadata platform for the AI era.

Any external system should be able to define, connect, and synchronize metadata through Metaplus, either in real time or on a schedule.

The current product focus is the data domain, but the platform model should remain general enough to evolve beyond it.

---

## 2. Project Principles

- **Correctness over performance**
- **Explicitness over hidden behavior**
- **Simplicity over unnecessary abstraction**
- **Openness over lock-in**
- **Validated assumptions over guesswork**

---

## 3. Contribution Rules

When changing Metaplus, contributors should follow these rules:

1. Start from schema and document contracts before implementation.
2. Keep `meta` aligned with authoritative sources.
3. Keep `plus` extensible, but separate from source truth.
4. Prefer small, additive, explainable changes.
5. Do not introduce undocumented fields or silent contract changes.
6. Read existing docs and tests before implementation; if they do not exist, state that clearly.
7. Maintain branch coverage at **70% or higher**.
8. Make design decisions and trade-offs explicit.

---

## 4. Document Ownership

Each top-level document has a different responsibility:

- `AGENTS.md`: mission, principles, and contributor workflow
- `SPEC.md`: normative rules and invariants of the platform
- `ARCHITECTURE.md`: module structure, boundaries, and dependency model
- JSON Schemas and domain-carried schemas: canonical field-level contracts

If documents overlap, prefer the more specific one for that concern.

---

## 5. Current Delivery Posture

Metaplus may evolve module by module.

At the current stage, some modules already exist and others are planned. This is acceptable as long as new work respects:

- the invariants in `SPEC.md`
- the boundaries in `ARCHITECTURE.md`
- the contracts in JSON Schema and domain definitions
