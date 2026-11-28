/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.forge.cli.health

import groovy.transform.CompileStatic

/**
 * The checks of {@code project health}, in the order they run, and what they found together.
 *
 * <p>Each check reads the project as text, within bounds, and runs nothing: no Gradle, no generator,
 * no Git and nothing of the network. A check that fails in a way it did not foresee is reported as
 * one that could not decide, and the others run all the same.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectHealth {

    /** What the findings are: every finding of every check, in the order of the levels and then of the checks. */
    final List<HealthFinding> findings

    private ProjectHealth(List<HealthFinding> findings) {
        this.findings = Collections.unmodifiableList(findings)
    }

    /**
     * @return the checks, in the order they run: a later one may go by what the earlier ones found
     */
    static List<HealthCheck> checks() {
        return [new GrailsVersionCheck(), new GradleWrapperCheck(), new JavaTargetCheck(), new SdkmanrcCheck(), new RemovedDependencyCheck(),
                new DeprecatedDependencyCheck(), new RecordCheck(), new GrailsWrapperCheck(), new GitFilesCheck(), new ToolCatalogCheck(), new ToolsCheck(),
                new LeftoverCheck(), new AgentSkillsCheck()] as List<HealthCheck>
    }

    /**
     * Runs every check on the project.
     */
    static ProjectHealth of(HealthContext context) {
        for (HealthCheck check : checks()) {
            List<HealthFinding> found
            try {
                found = check.check(context)
            } catch (IOException | RuntimeException e) {
                found = [new HealthFinding(check.name, HealthFinding.SKIPPED, HealthLevel.SKIPPED, 'The check could not read the project: ' +
                        "${e.getClass().getSimpleName()}${e.getMessage() == null ? '' : ': ' + e.getMessage()}", null, null, [], null)]
            }
            context.found.addAll(found)
        }
        List<HealthFinding> ordered = new ArrayList<HealthFinding>(context.found)
        // a stable sort: within a level, the order of the checks
        ordered.sort { HealthFinding one, HealthFinding other -> one.level.ordinal() <=> other.level.ordinal() }
        return new ProjectHealth(ordered)
    }

    /**
     * @return how many findings there are of the level
     */
    int count(HealthLevel level) {
        return findings.count { HealthFinding each -> each.level == level } as int
    }

    /**
     * @return the findings of the level, in their order
     */
    List<HealthFinding> of(HealthLevel level) {
        return findings.findAll { HealthFinding each -> each.level == level }
    }

    /**
     * @param strict whether a warning fails the command as an error does
     * @return the findings that fail the command: every error, and with {@code strict} every warning
     */
    List<HealthFinding> failing(boolean strict) {
        return findings.findAll { HealthFinding each -> each.level == HealthLevel.ERROR || (strict && each.level == HealthLevel.WARNING) }
    }
}
