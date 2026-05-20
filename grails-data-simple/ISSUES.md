<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
  
      https://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# SimpleMap Datastore O(M+N) Scaling and Performance

## Context
The SimpleMap datastore, primarily used for testing and in-memory scenarios, has been updated to align with the core O(M+N) performance patterns.

## Implemented and Validated
- Large query, persister, and session updates to keep core behavior consistent with the new registry flow.
- Optimized internal lookups to avoid redundant tenant resolution where the datastore or session context is already known.

## Identified Issues
- [RESOLVED] `SimpleMapQuerySpec` discriminator tests were failing due to runtime overload ambiguity in default datastore selection (`getDatastore(null, qualifier)` path).

## Fix Strategy
1. Align remaining internal entry points with the context-propagation pattern used in core.
2. Verify with core scalability tests.

## Latest Fix Applied
- Updated `DefaultDatastoreSelector` in `grails-datamapping-core` (`GormApiResolver.groovy`) to call `registry.getDatastoreByString(className, ConnectionSource.DEFAULT)` instead of the overloaded `getDatastore(...)` call.
- This removes Groovy runtime ambiguity when `className` is `null` and preserves expected default datastore selection semantics.

## Validation
- `./gradlew :grails-data-simple:test -PmaxTestParallel=1 --no-daemon --console=plain` is now green.
- Previously failing specs now pass:
  - `SimpleMapQuerySpec > test getBackingMap in DISCRIMINATOR mode`
  - `SimpleMapQuerySpec > test query isolation in DISCRIMINATOR mode`
