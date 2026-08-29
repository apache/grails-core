# Grails Profiles Tests

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

This module contains comprehensive tests for Grails profiles functionality, replacing the old shell script-based tests that were removed in commit 9b8fa17d40.

## Purpose

These tests verify that Grails profiles work correctly by:
- Testing application generation with different profiles (web, plugin, rest-api, etc.)
- Verifying profile-specific commands and features
- Ensuring generated applications can be built and run successfully
- Performing functional testing of generated applications

## Test Structure

The tests are organized into several categories:

### Unit/Integration Tests
- `BasicProfileTests.groovy` - Basic profile functionality tests
- `ProfileIntegrationTests.groovy` - Integration tests for profile functionality
- `ProfileEndToEndTests.groovy` - End-to-end tests for profile functionality

## Running the Tests

### Run all profile tests:
```bash
./gradlew :grails-profiles-tests:test
```

## Test Coverage

The tests cover:

### Application Generation
- Creating applications with different profiles
- Verifying correct directory structure
- Checking build files are generated properly

### Profile Commands
- `create-domain-class` - Domain class creation
- `create-controller` - Controller creation  
- `create-service` - Service creation
- `create-taglib` - Tag library creation
- `create-unit-test` - Unit test creation

### Build Verification
- Gradle compilation
- Application packaging
- Plugin JAR creation

### Integration Testing
- Application startup
- Configuration validation
- Basic functionality verification

## Dependencies

The tests depend on:
- Grails Core Framework
- Spock testing framework
- TestContainers for containerized testing (when needed)

## Configuration

The tests use containerized browsers through TestContainers integration via Geb's container support.
Geb configurations are located in `src/test/resources/GebConfig.groovy`.

## Migration from Old Tests

This replaces the shell script-based testing approach that was:
- Platform-dependent (Unix/Linux only)
- Hard to maintain and debug
- Not integrated with Gradle build system
- Difficult to run in CI environments

The new Gradle-based approach provides:
- Cross-platform compatibility
- Better integration with existing test infrastructure
- Easier maintenance and extension
- Seamless CI/CD integration