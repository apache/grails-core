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
package org.apache.grails.core.cli

import groovy.transform.CompileStatic

import org.apache.grails.gradle.model.cli.ProjectFacts

/**
 * What the project a tool runs in is, as the record of the {@code grails} command line says it: the
 * type of the application and the features it has. A tool has it from
 * {@link ExecutionContext#getProject()}, whatever started the tool, and never reads the record
 * itself. A project that was not generated or migrated by the command line has no record.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectInfo {

    private final boolean recorded
    private final String applicationType
    private final List<String> features

    /**
     * @param recorded whether the project has a record
     * @param applicationType the type of the application, or {@code null} without a record
     * @param features the names of the features the application has
     */
    ProjectInfo(boolean recorded, String applicationType, List<String> features) {
        this.recorded = recorded
        this.applicationType = applicationType
        this.features = Collections.unmodifiableList(new ArrayList<String>(features ?: Collections.<String> emptyList()))
    }

    /**
     * @param projectDirectory the directory of a project
     * @return what the record of the project says it is; without a record that can be read, a
     * project that is not recorded
     */
    static ProjectInfo of(File projectDirectory) {
        ProjectFacts facts = ProjectFacts.read(projectDirectory)
        new ProjectInfo(facts.recorded, facts.applicationType, facts.features)
    }

    /**
     * @return whether the project has a record of the command line, which it has when the command
     * line generated or migrated it
     */
    boolean isRecorded() {
        recorded
    }

    /**
     * @return the type of the application: {@code web}, {@code rest-api}, {@code plugin},
     * {@code web-plugin} or {@code rest-api-plugin}; {@code null} without a record
     */
    String getApplicationType() {
        applicationType
    }

    /**
     * @return the names of the features the application has, those it has by default included;
     * empty without a record, and not to be changed
     */
    List<String> getFeatures() {
        features
    }
}
