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
# Implementation Plan: Aggregate Style Violations

This plan outlines the steps to aggregate CodeNarc, PMD, SpotBugs, and Checkstyle violations from all submodules into a single, categorized Markdown report in the root directory.

## Objective
Enable XML reporting for all static analysis tools, redirect their output to a consolidated directory, and provide a root-level task to parse these reports and generate a unified `STYLE_VIOLATIONS.md` summary.

## Key Files
- `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/GrailsCodeStylePlugin.groovy`: Main plugin logic to update tool configurations and add the aggregator task.

## Implementation Steps

### 1. Update Submodule Tool Configurations
Modify `GrailsCodeStylePlugin.groovy` to ensure all tools produce XML reports in a shared location:

- **Checkstyle**: Update output location to include the task name: `reports/codestyle/checkstyle/${project.name}-${it.name}.xml`.
- **CodeNarc**: Update output location to include the task name: `reports/codestyle/codenarc/${project.name}-${it.name}.xml`.
- **PMD**: 
    - Enable XML reporting: `it.reports.xml.required.set(true)`.
    - Set output location: `reports/codestyle/pmd/${project.name}-${it.name}.xml`.
- **SpotBugs**:
    - Enable XML reporting: `it.reports.create('xml') { it.required = true }`.
    - Set output location: `reports/codestyle/spotbugs/${project.name}-${it.name}.xml`.

### 2. Implement the Aggregator Task
Add a new method `configureAggregation(Project project)` in `GrailsCodeStylePlugin` that is called only when applying to the root project.

- **Task Name**: `aggregateStyleViolations`
- **Group**: `verification`
- **Logic**:
    - Depend on all static analysis tasks in subprojects.
    - Crawl the `build/reports/codestyle/` directory for XML files.
    - Parse XML using `XmlSlurper`.
    - Map each violation to a common model: `Module`, `Class`, `Tool`, `Type`, `Line`, `Message`.
    - Generate `STYLE_VIOLATIONS.md` in the root directory.

#### Parsing Logic Specifics:
- **CodeNarc**: Combine `Package/@name` and `File/@name`.
- **PMD**: Use `violation/@class` and `violation/@package`.
- **SpotBugs**: Use `BugInstance/Class/@classname`.
- **Checkstyle**: Extract class name from `file/@name` (requires mapping absolute path back to package structure).

### 3. Maintain Local HTML Reports
Ensure that HTML reports remain enabled and default to their local module directories so developers can still use them for local development.

## Verification & Testing
1. Run checks on a representative module: `./gradlew :grails-core:codeStyle`.
2. Run the aggregator: `./gradlew aggregateStyleViolations`.
3. Verify `STYLE_VIOLATIONS.md` exists and contains correctly categorized violations.
4. Verify local HTML reports still exist in `grails-core/build/reports/`.
