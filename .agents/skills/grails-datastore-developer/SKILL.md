---
name: grails-datastore-developer
description: Guide for changing the Grails Data layer (grails-datastore-core, grails-datamapping-core and the datastore modules) with the whole surface in view, covering module layering, database variance, stateful builders such as DetachedCriteria, internal API, cross-datastore and cross-version parity, and how to test and review such changes
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Make a change to the Data layer start from the whole surface it touches, not from the one case that was reported.
- Keep the layering: `grails-datastore-core` and `grails-datamapping-core` hold logic that every datastore needs, and each datastore module holds what only its engine knows.
- Keep the differences between databases and between Hibernate versions in one declaration per module.
- Guide changes to stateful builders and caches, where a fix to one way in leaves the others behind.
- Say how such a change is tested and how a review comment about it is answered.

## When to Use Me

Activate this skill for changes under:

- `grails-datastore-core` and `grails-datamapping-core`: criteria, queries, validation, the `Datastore` and `Session` contracts.
- `grails-data-hibernate5`, `grails-data-hibernate7`, `grails-data-mongodb`, `grails-data-neo4j`, `grails-data-simple` and any future datastore module.
- A review comment or a bug report that shows a symptom in one of them. Use the protocol below before the fix, not after it.

Use `hibernate-developer` as well for Hibernate 7 binding and SQM work, and `test-fixer` to run and read the results.

## Layering

- **Core is universal.** A type in `grails-datastore-core` or `grails-datamapping-core` must make sense to a datastore that is not SQL. It never names a database, a dialect, a driver or a JDBC URL. A fact that differs by store reaches core as a question on `Datastore` (for example `getUniqueNulls()`), and each module answers it.
- **A SQL module owns its databases.** A SQL module declares the supported databases once, keyed by its own Hibernate dialect classes, and everything that depends on the database asks that one declaration: the regular expression pattern, the native SQL syntax, how a unique index treats a `null`, how H2 modes change that. Hibernate 5 and 7 render different SQL, so each module has its own table. Each Hibernate module has that declaration in `SupportedDatabase`; extend it and do not add another list of databases, dialects or modes next to it.
- **A store that is not SQL owns its own.** MongoDB, Neo4j, the in-memory datastore and a future module answer the same questions in their own module and import nothing from a SQL module.
- **Public in core is API.** Java cannot narrow visibility between artifacts, so a type that two modules share but applications must not use is public and goes in an `internal` package, with Javadoc that says it is internal, that it carries no compatibility promise and that it can change in a patch release (the generic, database-free `ClassKeyed` lookup is the example). Never put it in a docs page.
- **The DSL is the contract.** Applications meet this layer through constraints, mapping, criteria, `where` and finders. A change that alters what the DSL returns is a behavior change: state it in the docs and the upgrade notes.

## The Whole-Surface Protocol

Run it before changing a type that holds state or hides a decision: a builder, a cache, a lazy value, a lookup table, a translator. The cost of skipping it is rounds of review, each fixing the case the last reviewer happened to try.

1. **Inventory the state.** List every field, including the ones a subclass or an inner class reaches, and the way each is copied (`clone`, `newInstance`, a shared reference).
2. **Inventory the ways in.** List every method that writes it, every method that reads it, and every caller in other modules. Do not trust a search for one name. `DetachedCriteria` is written by `add`, the criterion DSL, `order`, `sort`, `join`, `select`, `setAlias`, `createAlias`, the projection methods and the clone-and-modify methods, and read by `getCriteria`, `getProjections`, `getOrders` and by translators in the H5 builder, the H7 handlers, `JpaQueryBuilder`, `DynamicFinder`, MongoDB, Neo4j and the in-memory datastore.
3. **State the invariant in one sentence** and put it in the class Javadoc. For a deferred criteria it is: the pending closure runs once, under a lock, before any read, write or copy, and a query reads a copy.
4. **Enforce it at a chokepoint**, not at each call site: one method that every way in calls first. A rule that depends on each author remembering it fails at the next method someone adds.
5. **Test the invariant, not the report.** Write a table with one row for each way in, and assert the invariant for each (`DetachedCriteriaDeferredQuerySpec`, in review with the deferred-criteria fix, is the model). A new method then has an obvious place to go and an obvious failure if it is missed.
6. **Check what the type is shared with.** Mutable lists handed out by a getter, a shallow `clone`, a projection list shared between two objects and a reader that bypasses the accessor are the usual leaks.
7. **Remember Groovy does not enforce `private` or `protected`.** Dynamic code, `.@field` and in-class names reach the field directly, so a field being private proves nothing about who reads it.

## Before You Push

A reviewer who finds one bug has found a sample of a class. Find the class yourself, before the review does. The review of one criteria change took ten rounds, each with a different case of the same two questions: where does the property resolve, and does a branch of an `or` keep its rows. A table written first would have answered all ten.

1. **Name the dimensions of the change.** For a criteria translator, a `where` or finder rewrite, a validator or a constraint, write down each of these and pick the rows that are meaningful:
   - **The criterion**: comparison, range (`between`, `gt`), `in`/`inList`, `like`/`ilike`/`rlike`, `isNull`/`isNotNull`, `isEmpty`/`isNotEmpty`, size, property-to-property, subquery.
   - **The scope the property resolves in**: the root entity; a nested association block; a block on a relationship entity (Neo4j); an embedded component; a self-referential association; an inherited or abstract target; a composite or named id; an owner and a target that have a property of the same name.
   - **The context**: top level; inside `or`; inside `not`; an `or` nested in an `and` nested in an `or`; inside a subquery; with a projection, an order, `max`/`offset` or a join that is already there.
   - **The connection**: the default; a named one; several that all map the entity; each multi-tenancy mode.
   - **The data**: `null`; an empty collection; an owner with no row in the association; a row that matches only one branch; a row that matches none.
2. **Write the table first.** One data-driven spec, one row per meaningful combination, each asserting the rows a known fixture returns. The fixture must hold the row that only each branch matches and a row that matches nothing, because the bug in a disjunction is the row that is silently dropped. Pin it in the TCK or the shared module so every datastore and both Hibernate versions run it.
3. **Make the rule one decision.** If the table needs the same "where does this resolve" answer in several places, move it to one method and test that method with the table. A fix that patches one call site starts the next review round.
4. **Test what a fail-fast check must accept.** Before a startup validator rejects a configuration, list every valid layout it can see (single datasource, named, several that all map the same entity, a tenant owner that points at a non-tenant entity) and assert each starts.
5. **Check every sentence that describes behavior.** A line in the docs or an upgrade note is a claim: read the generated schema, the SQL or the Cypher and compare it to the sentence. Do not describe the mapping block when the behavior follows the declaration order.
6. **Say what you covered.** The PR description lists the rows of the table, and the ones left out with the reason, so a reviewer starts from the map instead of discovering it.

## Reviews

- A review comment that shows a bug is a sample of a class of bug. Name the class, add its whole row set to the table in [Before You Push](#before-you-push), then answer. Fixing the one example is how the same review comes back three times.
- When a reviewer's fix and an earlier reviewer's fix pull in different directions, say so and pick one in the reply. Do not satisfy each in turn.
- Reply on the thread with the commit that fixes it. Never resolve a thread.
- A decision about behavior that a reviewer can reasonably question goes in the Javadoc and in the PR description, not only in a reply.

## Parity

- **Across datastores.** A change to shared criteria, query or validation code must run the suites of the other datastores that use it: `grails-data-simple`, `grails-data-mongodb` (core and bson), `grails-data-neo4j` (core), and both Hibernate modules.
- **Across Hibernate versions.** Hibernate 5 is the reference for behavior. When Hibernate 7 differs, match Hibernate 5. When Hibernate 5 was wrong and was never documented, fix it and make the reference docs state the behavior, for both versions. When a difference is deliberate, document it where users look for it.
- **Across branches.** A fix lands on the oldest branch it applies to and merges up. A change that rewrites code already merged to an older branch will conflict on the merge-up: say so in the PR.

## Testing

- Test a dialect-dependent behavior against the real database with Testcontainers, not only with a table of pattern strings: PostgreSQL, MySQL, MariaDB and Oracle are available in the Hibernate 7 module, and Db2 where the machine allows. A string table is a cheap addition, not the proof.
- Pin user-visible behavior with integration scenarios in `grails-test-examples` for both `hibernate5` and `hibernate7`, and with unit specs in the owning module. Probe Hibernate 5 first, and read the reference docs, before deciding what the right answer is.
- Reproduce a bug as a failing unit spec in the module that owns it before fixing it, not through a downstream functional failure.
- Use `HibernateGormDatastoreSpec` for anything that needs real Hibernate wiring, and read the schema it produces when the bug is about the schema.
- Run Gradle one task at a time on a laptop. Use `cleanTest` and `--no-build-cache`, and judge the result from the XML under `build/test-results`, not from a piped exit code. A `--tests` filter on a module that has a suite class reports an `initializationError` for the suite, because the filter leaves the suite nothing to discover (the message is `NoTestsDiscoveredException: Suite [...] did not discover any tests`). The same message also comes from a suite whose package selector is stale, so the message does not tell the two apart. Run the module once without the filter: if the suite reports the same failure there, it is not the filter, it is a failure of the suite to report, and no spec result excuses it. Set the entry aside only when the unfiltered run has no such failure and the XML of every requested spec shows the tests you expect, none skipped and none missing. Any other initialization failure is a failure, and a run in which a requested spec has no XML did not run it.
- Run `codeStyle` and CodeNarc before committing. PMD and SpotBugs run only where a module enables them.

## Pitfalls to Avoid

- Do not fix the reported example and stop. Ask which other methods reach the same state.
- Do not add a second list of databases, dialects or modes. Extend the one declaration.
- Do not put database names, SQL, or JDBC knowledge in a core type.
- Do not hand out a mutable list that a translator may change. Hand out a copy, or an unmodifiable view.
- Do not make a copy shallow when the thing it holds is a node that can be changed later: copy junctions, associations and projections too.
- Do not cache a derived value next to the state it derives from. The two will diverge the first time something changes the state directly.
- Do not publish an internal type without the internal-API Javadoc, and do not document it for users.
