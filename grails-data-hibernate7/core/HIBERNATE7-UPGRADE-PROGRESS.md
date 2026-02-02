# Hibernate 7 Migration Progress Report - GORM Core

## Overview
This document summarizes the approaches taken, challenges encountered, and future steps for upgrading the GORM Hibernate implementation to Hibernate 7.

## Resolved Challenges



## New Regressions After Rollback (Feb 1, 2026)







### 4. Functional Regressions (Attach/Associations) [PENDING]



*   **Symptom**: `IllegalArgumentException: Given entity is not associated with the persistence context` in `AttachMethodSpec`. `OneToManySpec` failures where collections are empty.



*   **Status**: On hold per user request.

---

## Strategy for GrailsDomainBinder Refactoring
- **Refactoring Approach**: When modifications to `GrailsDomainBinder` are required, follow this pattern:
    - Identify the specific methods/logic requiring changes.
    - Refactor the code to move logic into dedicated classes or helper methods where collaborators can be easily injected.
    - Provide a **public constructor** that accepts all collaborators needed by the methods.
    - Provide a **protected constructor** specifically for use by mocks in tests.
    - Ensure a corresponding **Spec** is written for the class.
    - New binding-related classes and their specs should be placed in the `domainbinding` subpackage.

## Current State of UpdateWithProxyPresentSpec
- **Status**: Failing.
- **Issue**: The test `Test update unidirectional oneToMany with proxy` fails because the retrieved child entity is already initialized, failing the `assert !proxyHandler.isInitialized(child)` check.
- **Attempts**: Tried `withNewSession`, `evict`, `clear`, `hibernateSession.load`, and `hibernateSession.getReference`.
- **Observation**: Even with a new session, Hibernate 7 seems to return an initialized instance if the entity was persisted earlier in the same test run, possibly due to session factory level caching or improper session disposal in the TCK manager.

## Future Steps
- Fix `UpdateWithProxyPresentSpec` by ensuring a clean state for proxy loading.
- Address remaining TCK failures (approx. 16) in the `hibernate 7` module.
# Important
- Never make changes in production code without consulting human, even in YOLO mode