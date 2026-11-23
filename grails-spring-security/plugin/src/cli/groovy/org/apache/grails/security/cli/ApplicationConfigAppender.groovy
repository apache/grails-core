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

package org.apache.grails.security.cli

import groovy.transform.CompileStatic

import org.grails.config.CodeGenConfig

/**
 * Appends settings to the configuration of a project, in the form of the file the project keeps it in.
 *
 * <p>The settings go to {@code grails-app/conf/application.groovy} when the project has that file, as
 * assignments. A project without it that has {@code grails-app/conf/application.yml} gets them there, as one
 * more YAML document. A project with neither gets {@code application.groovy}.</p>
 *
 * <p>A setting is named by its full key, {@code grails.plugin.springsecurity.useRoleGroups}. Its value is a
 * string, a boolean, a number, a list of those, or a list of maps of those, which is written one map to a
 * line.</p>
 *
 * <p>It also finds which of the settings the configuration has already, outside the environment blocks and in the
 * block of each environment, see {@link #findPresent}: appended settings come after them and apply in every
 * environment.</p>
 */
@CompileStatic
class ApplicationConfigAppender {

    static final String GROOVY_CONFIG = 'grails-app/conf/application.groovy'
    static final String YAML_CONFIG = 'grails-app/conf/application.yml'

    private static final String INDENT = '    '
    private static final String ENVIRONMENTS = 'environments'

    private final File projectDirectory

    /**
     * @param projectDirectory the directory of the project
     */
    ApplicationConfigAppender(File projectDirectory) {
        this.projectDirectory = projectDirectory
    }

    /**
     * @return the path in the project of the file the settings are appended to
     */
    String getPath() {
        if (new File(projectDirectory, GROOVY_CONFIG).isFile()) {
            return GROOVY_CONFIG
        }
        new File(projectDirectory, YAML_CONFIG).isFile() ? YAML_CONFIG : GROOVY_CONFIG
    }

    /**
     * Finds which of the settings the configuration of the project has already, in any environment. Both
     * configuration files are read, {@code application.groovy} and {@code application.yml}, whichever of them the
     * project has, each as it is written, and in whatever form the file gives a key: the full key, nested blocks or
     * maps, or a mix of the two.
     *
     * <p>Appended settings apply in every environment, so the settings of every environment block count, whichever
     * environment is current: {@code environments.<name>} of a YAML document, and an {@code environments} block of
     * {@code application.groovy}, also one inside another block. An environment that sets a key decides its value
     * there together with the settings that are appended: in {@code application.yml} the appended document replaces
     * it, while the top-level {@code environments} block of {@code application.groovy} is applied after the whole
     * file and keeps its own value. The blocks of {@code application.groovy} are read as blocks, so the code in the
     * block of each environment runs.</p>
     *
     * <p>A file has a setting when it gives the key a value: a string, a boolean, a number, a list, or a map with
     * entries. A key that {@code application.groovy} reads without setting it, which reading makes an empty block,
     * is not a setting.</p>
     *
     * @param keys the full keys of the settings
     * @return where the configuration has any of them: {@code application.groovy} first, and in each file the
     * settings outside the environment blocks first, then those of each environment in the order the file first
     * names it. The list is empty when neither file has one.
     * @throws IOException when a file cannot be read, as Groovy or as YAML; the message names it and says why
     */
    List<PresentSettings> findPresent(Collection<String> keys) throws IOException {
        List<PresentSettings> present = []
        for (String path : [GROOVY_CONFIG, YAML_CONFIG]) {
            File config = new File(projectDirectory, path)
            if (config.isFile()) {
                CodeGenConfig read = read(path, config)
                List<String> set = keys.findAll { String key -> isSetting(read.get(key)) }.toList()
                if (set) {
                    present.add(new PresentSettings(path, null, set))
                }
                environmentSettings(read, keys, path == GROOVY_CONFIG).each { String environment, List<String> keysOf ->
                    present.add(new PresentSettings(path, environment, keysOf))
                }
            }
        }
        present
    }

    /**
     * Reads a configuration file without choosing an environment: the YAML documents are merged as they are,
     * {@code environments} included, and {@code application.groovy} is read with {@code environments} as an
     * ordinary block, which keeps the settings of each environment under {@code environments.<name>}.
     */
    private static CodeGenConfig read(String path, File config) throws IOException {
        CodeGenConfig read = new CodeGenConfig()
        try {
            if (path == YAML_CONFIG) {
                config.withInputStream { InputStream input -> read.loadYml(input) }
            } else {
                ConfigSlurper slurper = new ConfigSlurper()
                slurper.registerConditionalBlock(ENVIRONMENTS, null)
                slurper.classLoader = read.groovyClassLoader
                read.mergeMap(slurper.parse(config.toURI().toURL()), false)
            }
        } catch (Exception e) {
            String how = path == YAML_CONFIG ? '' : ' with the blocks of all its environments'
            throw new IOException("${path} could not be read${how}: ${e.message?.trim() ?: e.class.name}", e)
        }
        read
    }

    /**
     * Finds the keys that the block of an environment sets. The configuration that was read holds every value also
     * under its full key, {@code environments.production.grails.plugin.springsecurity.useRoleGroups}; the key without
     * the environment is the one the block sets in its environment.
     *
     * @param read the file, as {@link #read} read it
     * @param keys the full keys of the settings
     * @param nestedBlocks whether an {@code environments} block inside another block counts, as it does in
     * {@code application.groovy}; the runtime reads {@code environments} only at the top of a YAML document
     * @return the keys each environment sets, in the order they are given, by the name of the environment
     */
    private static Map<String, List<String>> environmentSettings(CodeGenConfig read, Collection<String> keys, boolean nestedBlocks) {
        Map<String, Set<String>> found = [:]
        for (String name : read.keySet()) {
            List<String> parts = name.split(/\./).toList()
            // where environments can be: followed by the name of an environment, and in YAML only the first part
            int last = parts.size() - 2
            if (!nestedBlocks) {
                last = Math.min(last, 0)
            }
            for (int index = 0; index <= last; index++) {
                if (parts[index] == ENVIRONMENTS) {
                    String key = (parts.take(index) + parts.drop(index + 2)).join('.')
                    if (keys.contains(key) && isSetting(read.get(name))) {
                        found.computeIfAbsent(parts[index + 1]) { String environment -> new LinkedHashSet<String>() }.add(key)
                    }
                }
            }
        }
        found.collectEntries { String environment, Set<String> set ->
            [(environment): keys.findAll { String key -> set.contains(key) }.toList()]
        } as Map<String, List<String>>
    }

    private static boolean isSetting(Object value) {
        value != null && !(value instanceof Map && ((Map) value).isEmpty())
    }

    /**
     * Appends the settings to the configuration of the project.
     *
     * @param comment a line that says where the settings come from
     * @param settings the settings by their full keys, in the order they are written
     * @return the path in the project of the file they were appended to
     */
    String append(String comment, Map<String, Object> settings) {
        String path = getPath()
        File config = new File(projectDirectory, path)
        String present = config.isFile() ? config.getText('UTF-8') : ''
        StringBuilder text = new StringBuilder()
        if (present && !present.endsWith('\n')) {
            text.append(System.lineSeparator())
        }
        text.append(path == YAML_CONFIG ? yaml(comment, settings) : groovy(comment, settings))
        config.parentFile.mkdirs()
        config.append(text.toString(), 'UTF-8')
        path
    }

    private static String groovy(String comment, Map<String, Object> settings) {
        String newLine = System.lineSeparator()
        StringBuilder text = new StringBuilder(newLine)
        text.append('// ').append(comment).append(newLine)
        settings.each { String key, Object value ->
            if (isRows(value)) {
                List<List<String>> rows = ((List<Map<String, Object>>) value).collect { Map<String, Object> row ->
                    row.collect { String name, Object cell -> "${name}: ${groovyLiteral(cell)}".toString() }
                }
                int width = rows.collect { List<String> row -> row.size() > 1 ? row[0].length() + 1 : 0 }.max()
                text.append(key).append(' = [').append(newLine)
                rows.eachWithIndex { List<String> row, int index ->
                    String first = row.size() > 1 ? "${row[0]},".padRight(width) + ' ' : row[0]
                    text.append('\t[').append(first).append(row.drop(1).join(', ')).append(']')
                    text.append(index < rows.size() - 1 ? ',' : '').append(newLine)
                }
                text.append(']').append(newLine)
            } else {
                text.append(key).append(' = ').append(groovyLiteral(value)).append(newLine)
            }
        }
        text.toString()
    }

    private static String groovyLiteral(Object value) {
        if (value instanceof CharSequence) {
            return "'" + value.toString().replace('\\', '\\\\').replace("'", "\\'") + "'"
        }
        if (value instanceof Collection) {
            return '[' + ((Collection) value).collect { Object each -> groovyLiteral(each) }.join(', ') + ']'
        }
        String.valueOf(value)
    }

    private static String yaml(String comment, Map<String, Object> settings) {
        Map<String, Object> tree = [:]
        settings.each { String key, Object value ->
            List<String> names = key.tokenize('.')
            Map<String, Object> node = tree
            names.init().each { String name ->
                if (!(node.get(name) instanceof Map)) {
                    node.put(name, [:])
                }
                node = (Map<String, Object>) node.get(name)
            }
            node.put(names.last(), value)
        }
        String newLine = System.lineSeparator()
        StringBuilder text = new StringBuilder()
        text.append('---').append(newLine)
        text.append('# ').append(comment).append(newLine)
        yamlNode(text, tree, '')
        text.toString()
    }

    private static void yamlNode(StringBuilder text, Map<String, Object> node, String indent) {
        String newLine = System.lineSeparator()
        node.each { String name, Object value ->
            if (value instanceof Map) {
                text.append(indent).append(name).append(':').append(newLine)
                yamlNode(text, (Map<String, Object>) value, indent + INDENT)
            } else if (isRows(value)) {
                text.append(indent).append(name).append(':').append(newLine)
                ((List<Map<String, Object>>) value).each { Map<String, Object> row ->
                    String lead = '- '
                    row.each { String key, Object cell ->
                        text.append(indent).append(INDENT).append(lead).append(key).append(': ').append(yamlScalar(cell)).append(newLine)
                        lead = '  '
                    }
                }
            } else {
                text.append(indent).append(name).append(': ').append(yamlScalar(value)).append(newLine)
            }
        }
    }

    private static String yamlScalar(Object value) {
        if (value instanceof CharSequence) {
            return "'" + value.toString().replace("'", "''") + "'"
        }
        if (value instanceof Collection) {
            return '[' + ((Collection) value).collect { Object each -> yamlScalar(each) }.join(', ') + ']'
        }
        String.valueOf(value)
    }

    private static boolean isRows(Object value) {
        value instanceof List && !((List) value).isEmpty() && ((List) value).every { Object each -> each instanceof Map }
    }

    /**
     * Settings that a configuration file has in one place: outside the environment blocks, or in the block of one
     * environment.
     */
    @CompileStatic
    static final class PresentSettings {

        /** The path of the file in the project. */
        final String path

        /** The environment whose block has the settings, or {@code null} for the settings outside the blocks. */
        final String environment

        /** The full keys of the settings, in the order they were asked for. */
        final List<String> keys

        PresentSettings(String path, String environment, List<String> keys) {
            this.path = path
            this.environment = environment
            this.keys = Collections.unmodifiableList(new ArrayList<String>(keys))
        }

        /**
         * @return the file, and the environment when the settings are those of one, as in
         * {@code grails-app/conf/application.yml, environment production}
         */
        String getLocation() {
            environment == null ? path : "${path}, environment ${environment}".toString()
        }
    }
}
