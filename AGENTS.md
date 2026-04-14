# Metaplus Agents
Metaplus -- A Plus Replica of Metadata Truth

This document defines how Metaplus is built, evolved, and maintained.

---
# 1. Mission

Metaplus aims to build an open and unified metadata platform for the AI era.

Any system can define, connect, and synchronize metadata through Metaplus, in real time or on schedule.

At this stage, Metaplus focuses on the data domain so that people and agents can manage and use data with clarity, confidence, and control.

---
# 2. Core Principles

## Open Integration

Metaplus is open by design.

- Any third-party system should be able to integrate
- Integration should support real-time or scheduled synchronization
- Interfaces should be stable, explicit, and easy to adopt

## Schema First

Schemas define the contract.

- JSON Schema is the source of truth
- Definitions come before implementation
- No field exists without schema
- Schema evolution is additive

## Stable Identity (FQMN)

FQMN = `<domain>:<system>:<instance>:<entity>`

- Identity must be globally unique
- Identity should stay source-aligned and evolvable
- All cross-system interoperability depends on stable identity

Example: `data/table:mysql:main:warehouse.sales.orders`


## Document-Based Model

All metadata is represented as a `MetaplusDoc`.

- Structure is fixed: idea / meta / plus / info
- APIs operate on documents
- Domain-specific meaning lives inside `meta` and `plus`


## Meta vs Plus Separation

Metaplus separates facts from interpretation.

- `meta` stores synchronized, source-of-truth metadata
- `plus` stores derived, human-authored, or AI-generated extensions
- `plus` may enrich `meta`, but must not replace or blur source facts
- This boundary is fundamental and must remain clear

## General Platform, Data Focus

Metaplus is a general metadata platform, currently focused on the data domain.

- The platform model should work beyond the data domain
- The current priority is to help people and agents manage and use data with clarity, confidence, and control
- Designs should favor generality without weakening data-domain depth

## AI Native

Metaplus is designed for AI from the beginning.

- Metadata should be ready for MCP, skills, and AI clients
- Human and agent collaboration should be a first-class workflow
- The platform should enable understanding, automation, and coordinated action

---
# 3. Constraints

These are hard constraints, not suggestions.

## Design Constraints

- Correctness over performance
- Explicitness over hidden behavior
- Simplicity over unnecessary abstraction
- Openness over lock-in
- Avoid unverified assumptions

## Product Constraints

- Onboarding should be simple: define schema, expose API, implement sync
- External systems should integrate without bespoke coupling
- Sync should support both scheduled and near-real-time models

## No Schema Drift

- No field without schema
- No silent contract changes
- No breaking changes without explicit migration strategy

---
# 4. How We Work

- Start from schema and document contracts
- Keep `meta` aligned with authoritative sources
- Keep `plus` extensible without polluting source truth
- Design APIs and sync flows for external adoption
- Prefer small, additive, explainable changes
- Read existing tests and docs before implementation; if they do not exist, state that clearly
- Maintain branch coverage at no less than 70%
- Provide clear rationale for design decisions and trade-offs

---
# 5. Reference Documents

- `AGENTS.md` -> project mission and working rules
- `SPEC.md` -> system rules
- `ARCHITECTURE.md` -> module design
- JSON Schemas -> data model contracts

When present, these documents are authoritative.
