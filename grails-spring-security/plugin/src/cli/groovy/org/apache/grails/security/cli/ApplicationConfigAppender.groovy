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
 */
@CompileStatic
class ApplicationConfigAppender {

    static final String GROOVY_CONFIG = 'grails-app/conf/application.groovy'
    static final String YAML_CONFIG = 'grails-app/conf/application.yml'

    private static final String INDENT = '    '

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
}
