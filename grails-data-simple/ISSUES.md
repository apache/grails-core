# SimpleMap Datastore O(M+N) Scaling and Performance

## Context
The SimpleMap datastore, primarily used for testing and in-memory scenarios, has been updated to align with the core O(M+N) performance patterns.

## Implemented and Validated
- Large query, persister, and session updates to keep core behavior consistent with the new registry flow.
- Optimized internal lookups to avoid redundant tenant resolution where the datastore or session context is already known.

## Identified Issues
- Some legacy tests in `grails-data-simple` still rely on patterns that may trigger unnecessary registry lookups.

## Fix Strategy
1. Align remaining internal entry points with the context-propagation pattern used in core.
2. Verify with core scalability tests.
