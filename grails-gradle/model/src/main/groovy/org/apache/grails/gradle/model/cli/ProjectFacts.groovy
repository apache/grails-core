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
package org.apache.grails.gradle.model.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/**
 * What the record of the {@code grails} command line says a project is: the type of the application
 * and the features it has. The command line writes the record when it generates or migrates a
 * project, {@link #RECORD} in the directory of the project; a tool of the project is told what the
 * project is from it, whatever started the tool.
 *
 * <p>Nothing but the type of the application and the names of the features is read, from no more
 * than {@link #MAX_BYTES} bytes and for no more than {@link #MAX_FEATURES} features, and reading
 * never fails: a file that is not there, is not a regular file, is larger, is not YAML of plain
 * mappings, lists and texts, names a type of application that does not exist or a feature whose name
 * is not lower-case letters, digits and hyphens is no record, and so is one of another format than
 * {@link #FORMAT}. Whether the record is the one the command line wrote, untouched, is not checked:
 * the command line does that.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectFacts {

    /** The record of a project, in the directory of the project. */
    public static final String RECORD = '.grails/forge.lock.yml'

    /** The format of the record that is read. */
    public static final int FORMAT = 1

    /** The most bytes of a record that are read; a larger file is no record. */
    public static final int MAX_BYTES = 1024 * 1024

    /** The most features a record names; one that names more is no record. */
    public static final int MAX_FEATURES = 1024

    /** The types of application a record can name. */
    public static final List<String> APPLICATION_TYPES = ['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin'].asImmutable()

    /** What is known of a project without a record. */
    public static final ProjectFacts NONE = new ProjectFacts(false, null, Collections.<String> emptyList())

    private static final Pattern FEATURE_NAME = Pattern.compile('[a-z0-9][a-z0-9-]{0,63}')

    private static final int MAX_DEPTH = 8

    /** Whether the project has a record. */
    final boolean recorded

    /** The type of the application, one of {@link #APPLICATION_TYPES}; {@code null} without a record. */
    final String applicationType

    /** The names of the features the application has, in the order of the record; empty without a record, and not to be changed. */
    final List<String> features

    private ProjectFacts(boolean recorded, String applicationType, List<String> features) {
        this.recorded = recorded
        this.applicationType = applicationType
        this.features = Collections.unmodifiableList(new ArrayList<String>(features))
    }

    /**
     * @param projectDirectory the directory of a project
     * @return what its record says it is, or {@link #NONE} when it has no record that can be read
     */
    static ProjectFacts read(File projectDirectory) {
        if (projectDirectory == null) {
            return NONE
        }
        try {
            File record = new File(projectDirectory, RECORD)
            if (!Files.isRegularFile(record.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return NONE
            }
            byte[] bytes
            InputStream input = new FileInputStream(record)
            try {
                bytes = input.readNBytes(MAX_BYTES + 1)
            } finally {
                input.close()
            }
            return bytes.length > MAX_BYTES ? NONE : parse(bytes)
        } catch (Exception | StackOverflowError ignored) {
            return NONE
        }
    }

    /**
     * @param record the content of a record
     * @return what it says, or {@link #NONE} when it is not a record that can be read
     */
    static ProjectFacts parse(byte[] record) {
        if (record == null || record.length > MAX_BYTES) {
            return NONE
        }
        Object loaded
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(0)
            options.setAllowRecursiveKeys(false)
            options.setNestingDepthLimit(MAX_DEPTH)
            options.setCodePointLimit(MAX_BYTES + 1)
            DumperOptions dumperOptions = new DumperOptions()
            // the safe loader, which makes mappings, lists and texts and no object of another class
            Yaml reader = new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options, new TextResolver())
            loaded = reader.load(new String(record, StandardCharsets.UTF_8))
        } catch (Exception | StackOverflowError ignored) {
            return NONE
        }
        if (!(loaded instanceof Map)) {
            return NONE
        }
        Map root = (Map) loaded
        if (root.get('format') != String.valueOf(FORMAT)) {
            return NONE
        }
        Object applicationType = root.get('applicationType')
        if (!(applicationType instanceof String) || !APPLICATION_TYPES.contains(applicationType)) {
            return NONE
        }
        Object listed = root.get('features')
        if (!(listed instanceof List) || ((List) listed).size() > MAX_FEATURES) {
            return NONE
        }
        List<String> features = new ArrayList<String>()
        for (Object each : (List) listed) {
            Object name = each instanceof Map ? ((Map) each).get('name') : null
            if (!(name instanceof String) || !FEATURE_NAME.matcher((String) name).matches()) {
                return NONE
            }
            if (!features.contains(name)) {
                features << (String) name
            }
        }
        new ProjectFacts(true, (String) applicationType, features)
    }

    /** Reads every single value as text: what a value has to be is for the reader of the record to say. */
    private static class TextResolver extends Resolver {

        @Override
        protected void addImplicitResolvers() {
        }
    }
}
