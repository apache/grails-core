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
 * {@code grails-version}: the version of Grails the project is on, the {@code grailsVersion} of its
 * {@code gradle.properties} as its build reads it, against the version of the command line.
 *
 * @since 9.0
 */
@CompileStatic
final class GrailsVersionCheck extends HealthCheck {

    private static final List<String> FILES = ['gradle.properties'].asImmutable()

    @Override
    String getName() {
        return 'grails-version'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        String project = context.projectVersion
        String own = context.commandLineVersion
        if (project == null) {
            return [skipped('gradle.properties names no grailsVersion that could be read, so the version of Grails the project is on is not known', FILES)]
        }
        int major = context.commandLineMajor
        Integer projectMajor = Versions.major(project)
        if (projectMajor != null && projectMajor < major) {
            return [found('earlier', HealthLevel.WARNING, "The project is on Grails ${project} (gradle.properties), and the command line is of Grails ${own}: " +
                    "what is found is what Grails ${major} asks of the project",
                    "Work through the upgrade notes of Grails ${major}, set grailsVersion in gradle.properties to ${own}, and run ./grailsw update-wrapper",
                    null, FILES, NOTE_UPGRADE)]
        }
        if (projectMajor != null && projectMajor > major) {
            return [found('newer', HealthLevel.WARNING, "The project is on Grails ${project} (gradle.properties), which is later than this command line, of Grails ${own}: " +
                    "what is found is what Grails ${major} asks of the project",
                    './grailsw project health runs the command line of the version of the project', './grailsw project health', FILES, null)]
        }
        if (project != own) {
            Integer compared = Versions.compare(project, own)
            String fix = compared != null && compared > 0 ? './grailsw project health runs the command line of the version of the project' :
                    "Read the upgrade notes of the releases after ${project}; when the project moves to ${own}, set grailsVersion in gradle.properties and run ./grailsw update-wrapper"
            return [found('release', HealthLevel.ADVICE, "The project is on Grails ${project} (gradle.properties), and the command line is of Grails ${own}", fix,
                    compared != null && compared > 0 ? './grailsw project health' : null, FILES, null)]
        }
        return [ok("The project is on Grails ${project} (gradle.properties), the version of this command line", FILES)]
    }
}
