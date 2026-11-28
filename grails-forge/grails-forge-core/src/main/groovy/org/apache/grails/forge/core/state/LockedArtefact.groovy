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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import org.apache.grails.forge.core.pack.PackArtefact

/**
 * A command a feature of the application gives the command line, as the lock of the project records
 * it under the feature: what the feature pack declared under {@code artefacts}. The command line reads
 * the commands of a project from here and from nowhere else, so it has them without the pack and
 * without the generator, and has a command exactly when the record has its feature.
 *
 * <p>The lock is a file of the project, which can come from anywhere: what is read of it is held to
 * what a pack can declare, a name that is not one of the command line itself, a template below the
 * templates of the feature and files below the project.</p>
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
final class LockedArtefact {

    /** The name of the command after {@code create-} */
    final String name

    /** What the command is for, one line */
    final String description

    /** What the kind of class adds to its name, or {@code null} */
    final String suffix

    /** The path of the template of the class below the templates of the feature in the project */
    final String template

    /** The path of the class in the project, with its placeholders */
    final String to

    /** The path of the template of the test below the templates of the feature in the project, or {@code null} */
    final String testTemplate

    /** The path of the test in the project, with its placeholders, or {@code null} */
    final String testTo

    @SuppressWarnings('ParameterCount')
    LockedArtefact(String name, String description, String suffix, String template, String to, String testTemplate, String testTo) {
        this.name = name
        this.description = description
        this.suffix = suffix
        this.template = template
        this.to = to
        this.testTemplate = testTemplate
        this.testTo = testTo
    }

    /**
     * @param artefact a command a feature pack declares
     * @return the command as the lock records it
     */
    static LockedArtefact of(PackArtefact artefact) {
        return new LockedArtefact(artefact.name, artefact.description, artefact.suffix, artefact.template, artefact.to,
                artefact.testTemplate, artefact.testTo)
    }

    /**
     * @return the name of the command: {@code create-} and the name
     */
    String getCommandName() {
        return PackArtefact.COMMAND_PREFIX + name
    }

    @PackageScope
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('name', name)
        map.put('description', description)
        if (suffix != null) {
            map.put('suffix', suffix)
        }
        map.put('template', template)
        map.put('to', to)
        if (testTemplate != null) {
            map.put('testTemplate', testTemplate)
            map.put('testTo', testTo)
        }
        return map
    }

    @PackageScope
    static LockedArtefact read(StateSection section) {
        String name = section.matching('name', PackArtefact.NAME, 'the name of a command after create-, in lower case letters, digits and hyphens', PackArtefact.MAX_NAME)
        if (PackArtefact.RESERVED.contains(name)) {
            throw section.problem('name', "is ${name}, and ${PackArtefact.COMMAND_PREFIX}${name} is a command of the command line itself")
        }
        String description = section.text('description', PackArtefact.MAX_DESCRIPTION)
        String suffix = section.optionalMatching('suffix', PackArtefact.SUFFIX, 'what a kind of class adds to the name of a class')
        String template = templateOf(section, 'template')
        String to = destinationOf(section, 'to')
        String testTemplate = null
        String testTo = null
        if (section.has('testTemplate') || section.has('testTo')) {
            testTemplate = templateOf(section, 'testTemplate')
            testTo = destinationOf(section, 'testTo')
        }
        section.end()
        return new LockedArtefact(name, description, suffix, template, to, testTemplate, testTo)
    }

    private static String templateOf(StateSection section, String key) {
        String template = section.text(key, PackArtefact.MAX_PATH)
        if (!PackArtefact.isTemplatePath(template)) {
            throw section.problem(key, 'must be the path of a template below the templates of the feature')
        }
        return template
    }

    private static String destinationOf(StateSection section, String key) {
        String to = section.text(key, PackArtefact.MAX_PATH)
        if (!PackArtefact.isDestination(to)) {
            throw section.problem(key, "must be a path below the project that has ${PackArtefact.CLASS_NAME} in it and no placeholder a command does not have")
        }
        return to
    }
}
