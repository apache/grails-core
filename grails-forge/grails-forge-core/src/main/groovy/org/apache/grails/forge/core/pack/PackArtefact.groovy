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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A command a feature pack gives the command line of an application that has its feature, which the
 * descriptor declares under {@code artefacts}: {@code create-<name>} writes a class, and its test when
 * the pack has a template for one, from a template of the pack.
 *
 * <p>The templates are text in which the tokens of {@link ArtefactTokens} are replaced; nothing of
 * them is run. When an application is generated they are written into it as they are, below
 * {@link #DIRECTORY}, so the command reads them from the project and the pack is not needed again.</p>
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true, includePackage = false)
final class PackArtefact {

    /** The most commands a pack can declare */
    public static final int MAX = 20

    /** The most characters the name of a command can have after {@code create-} */
    public static final int MAX_NAME = 40

    /** The most characters a path of a command can have: of a template in the pack, and of a file it writes */
    public static final int MAX_PATH = 200

    /** The most characters the description of a command can have */
    public static final int MAX_DESCRIPTION = 200

    /** The most bytes a template of a command can have, which is what the command line reads of a template of a project */
    public static final int MAX_TEMPLATE_BYTES = 256 * 1024

    /** What the name of every command of a pack starts with */
    public static final String COMMAND_PREFIX = 'create-'

    /** Where an application has the templates of the commands of its features, each feature in a directory of its name */
    public static final String DIRECTORY = 'src/main/templates/features'

    /** The name of a command after {@code create-}: lower case letters and digits in words joined by hyphens */
    public static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)

    /** What a kind of class adds to the name of a class: letters and digits that start with a capital */
    public static final Pattern SUFFIX = Pattern.compile(/[A-Z][A-Za-z0-9]{0,39}/)

    /** The placeholder the path of a file a command writes has to have: without it every class would be one file */
    public static final String CLASS_NAME = '{className}'

    /**
     * The names a pack cannot give a command: {@code create-} and one of them is a command of the
     * command line itself, or was one of the Grails shell, which is answered with what there is in
     * its place.
     */
    public static final Set<String> RESERVED = Collections.unmodifiableSet(new LinkedHashSet<String>([
            'app', 'webapp', 'web-app', 'restapi', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin',
            'controller', 'service', 'domain-class', 'taglib', 'tag-lib', 'interceptor', 'job', 'unit-test', 'integration-test',
            'functional-test', 'restful-controller', 'domain-resource', 'command',
            'script', 'profile', 'creator-command', 'generator-command', 'gradle-command', 'feature', 'template',
    ]))

    private static final Pattern PLACEHOLDER = Pattern.compile(/\{([^{}]*)\}/)

    /** The name of the command after {@code create-} */
    final String name

    /** What the command is for, one line */
    final String description

    /** What the kind of class adds to its name, such as {@code Listener}, or {@code null} */
    final String suffix

    /** The path of the template of the class in the templates of the pack, with {@code /} between its steps */
    final String template

    /** The path of the class in the application, with its placeholders */
    final String to

    /** The path of the template of the test in the templates of the pack, or {@code null} for a command that writes no test */
    final String testTemplate

    /** The path of the test in the application, with its placeholders, or {@code null} */
    final String testTo

    @SuppressWarnings('ParameterCount')
    PackArtefact(String name, String description, String suffix, String template, String to, String testTemplate, String testTo) {
        this.name = name
        this.description = description
        this.suffix = suffix
        this.template = template
        this.to = to
        this.testTemplate = testTemplate
        this.testTo = testTo
    }

    /**
     * @return the name of the command: {@code create-} and the name
     */
    String getCommandName() {
        return COMMAND_PREFIX + name
    }

    /**
     * @return the templates of the command in the pack: of the class, and of the test when there is one
     */
    List<String> getTemplates() {
        return testTemplate == null ? [template] : [template, testTemplate]
    }

    /**
     * @param feature the name of a feature
     * @param template the path of a template of a command in the pack of the feature
     * @return the path of the template in an application that has the feature, with {@code /} between its steps
     */
    static String projectPath(String feature, String template) {
        return "${DIRECTORY}/${feature}/${template}"
    }

    /**
     * @param path a path
     * @return whether it is the path of a template in a pack: a path a pack can read, of at most
     * {@link #MAX_PATH} characters
     */
    static boolean isTemplatePath(String path) {
        return path != null && path.length() <= MAX_PATH && PackDescriptorParser.isPathBelowRoot(path)
    }

    /**
     * @param path a path
     * @return whether it is the path of a file a command can write: at most {@link #MAX_PATH}
     * characters, with {@link #CLASS_NAME} in it, no placeholder but those of
     * {@link PackDescriptorParser#DESTINATION_TOKENS}, and a path a pack can write once they have values
     */
    static boolean isDestination(String path) {
        if (path == null || path.length() > MAX_PATH || !path.contains(CLASS_NAME)) {
            return false
        }
        Matcher used = PLACEHOLDER.matcher(path)
        while (used.find()) {
            if (!PackDescriptorParser.DESTINATION_TOKENS.contains(used.group(1))) {
                return false
            }
        }
        return PackDescriptorParser.isPathBelowRoot(PLACEHOLDER.matcher(path).replaceAll('x'))
    }
}
