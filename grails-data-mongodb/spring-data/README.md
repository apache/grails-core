<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# GORM for MongoDB — Spring Data Integration

Optional module that lets GORM for MongoDB and [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb)
run side by side over the **same** `MongoClient`, database and codecs, and — within a single
`@Transactional` method — the **same** MongoDB transaction (`ClientSession`).

When this module and `spring-data-mongodb` are on the classpath of a Spring Boot application with a
GORM `MongoDatastore`, `SpringDataMongoGormAutoConfiguration` registers, over GORM's connection:

- a `MongoDatabaseFactory` bound to GORM's `MongoClient` + default database (it does **not** close
  GORM's client),
- a `MongoTemplate` (named `mongoTemplate`) + `MappingMongoConverter` sharing the driver codec registry,
- a primary `transactionManager` (`GormSharedSessionMongoTransactionManager`) that binds GORM's
  `ClientSession` into Spring Data so both stacks share one transaction.

Unified transactions require GORM server-side transactions (`grails.mongodb.transactional = true`).
Enable Spring Data repositories with `@EnableMongoRepositories` on a package separate from your GORM
`@Entity` classes — the mapping models stay distinct; only the connection, codecs and session are shared.

See the *Spring Data MongoDB Interoperability* section of the GORM for MongoDB guide for details.

## Compatibility notes

- Sharing the `ClientSession` requires reaching Spring Data's package-private `MongoResourceHolder`, so a small helper lives in package `org.springframework.data.mongodb`. This is verified against the Spring Data MongoDB **5.x** line shipped with Spring Boot 4; an incompatible change to that internal type fails the build (and the coupling smoke test). Because of the deliberate split package, this module is supported on the **class path only** and is not compatible with the JPMS module path.
- GORM owns the `ClientSession` lifecycle; the bound holder is read-only for `MongoTemplate`, so there is no double-close.
- The unified transaction manager supports a single flat transaction (`PROPAGATION_REQUIRED`); `REQUIRES_NEW`/`NESTED` are not supported and join the surrounding transaction.
