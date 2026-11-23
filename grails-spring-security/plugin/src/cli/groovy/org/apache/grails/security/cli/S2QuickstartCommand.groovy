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

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneCommand

/**
 * Creates domain classes and updates config settings for the Spring Security plugin.
 * Usage: <code>s2-quickstart DOMAIN_CLASS_PACKAGE USER_CLASS_NAME ROLE_CLASS_NAME [REQUESTMAP_CLASS_NAME] [--groupClassName=GROUP_CLASS_NAME] [--force]</code> or
 * <code>s2-quickstart --uiOnly [--force]</code>
 *
 * <p>The command writes nothing when the configuration of the project has a setting it would write already, in
 * {@code application.groovy} or in {@code application.yml}: the settings it appends would replace it. With
 * {@code --force} it writes all the same.</p>
 *
 * For example:
 * 1. <code>s2-quickstart com.yourapp User Role</code>
 * 2. <code>s2-quickstart com.yourapp User Role --groupClassName=RoleGroup</code>
 * 3. <code>s2-quickstart com.yourapp Person Authority Requestmap</code>
 * 4. <code>s2-quickstart --uiOnly</code>
 *
 * @since 6.0.0
 */
@CompileStatic
class S2QuickstartCommand implements GrailsApplicationCommand, StandaloneCommand, CommandLineHelper, SkipBootstrap {

    private static final String SETTINGS = 'grails.plugin.springsecurity'
    private static final String RESOURCES = 'grails-app/conf/spring/resources.groovy'
    private static final List<String> FLAGS = ['groupClassName', 'uiOnly', 'salt', 'force']
    private static final List<String> OPEN_PATTERNS = ['/assets/**', '/**/js/**', '/**/css/**', '/**/images/**', '/**/favicon.ico']

    private static final String USAGE_MESSAGE = '''
   s2-quickstart <domain-class-package> <user-class-name> <role-class-name> [requestmap-class-name] [--groupClassName=group-class-name] [--force]
or s2-quickstart --uiOnly [--force]

Example: s2-quickstart com.yourapp User Role
Example: s2-quickstart com.yourapp User Role --groupClassName=RoleGroup
Example: s2-quickstart com.yourapp Person Authority Requestmap
Example: s2-quickstart --uiOnly
'''

    String description = 'Creates domain classes and updates config settings for the Spring Security plugin'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    private Map<String, String> templateAttributes
    private Model userModel
    private Model roleModel
    private Model requestmapModel
    private Model groupModel

    @Override
    boolean handle() {
        List<String> unknown = unknownFlags(FLAGS)
        if (unknown) {
            return refuse("Unknown option ${unknown.join(', ')}.")
        }
        if (flag('salt')) {
            error('The option --salt is no longer supported: the password encoders of the plugin salt a password ' +
                    'themselves, and SpringSecurityService.encodePassword takes no salt. Run the command without it.')
            return FAILURE
        }
        boolean uiOnly = isFlagPresent('uiOnly')
        if (!uiOnly && !readNames()) {
            return FAILURE
        }
        Map<String, Object> settings = settings(uiOnly)
        if (!isFlagPresent('force') && !hasNoneOf(settings.keySet(), uiOnly ? 'its settings' : 'its classes and settings')) {
            return FAILURE
        }
        boolean registered = true
        if (uiOnly) {
            addStatus('\nConfiguring Spring Security; not generating domain classes')
        } else {
            addStatus(creating())
            registered = createDomains()
        }
        String path = appendConfig(settings)
        if (!registered) {
            return FAILURE
        }
        addStatus(uiOnly ? """
Your ${path} has been updated with security settings;
please verify that the values are correct.
""" : """
Created security-related domain classes. Your ${path}
has been updated with the class names of the configured domain classes;
please verify that the values are correct.
""")
        return SUCCESS
    }

    private boolean refuse(String reason) {
        error("${reason} Usage:${USAGE_MESSAGE}")
        FAILURE
    }

    private boolean readNames() {
        if (args.size() < 3 || args.size() > 4) {
            return refuse(args.size() < 3 ? 'The package and the names of the user and the role class are required.' : 'Too many arguments.')
        }
        String packageName = args[0]
        if (!isQualifiedName(packageName)) {
            return refuse("'${packageName}' is no package name.")
        }
        String groupClassName = flag('groupClassName')
        List<String> classNames = args.drop(1) + (groupClassName ? [groupClassName] : [] as List<String>)
        String invalid = classNames.find { String name -> !isSimpleName(name) }
        if (invalid != null) {
            return refuse("'${invalid}' is no class name; the classes are created in the package '${packageName}'.")
        }
        if (classNames.unique(false).size() < classNames.size()) {
            return refuse('The classes need names that differ.')
        }

        userModel = model(packageName + '.' + args[1])
        roleModel = model(packageName + '.' + args[2])
        requestmapModel = args.size() == 4 ? model(packageName + '.' + args[3]) : null
        groupModel = groupClassName ? model(packageName + '.' + groupClassName) : null

        templateAttributes = Collections.unmodifiableMap([
                packageName: userModel.packageName,
                userClassName: userModel.simpleName,
                userClassProperty: userModel.modelName,
                roleClassName: roleModel.simpleName,
                roleClassProperty: roleModel.modelName,
                requestmapClassName: requestmapModel?.simpleName,
                groupClassName: groupModel?.simpleName,
                groupClassProperty: groupModel?.modelName
        ])
        true
    }

    /**
     * Says what the configuration of the project has of the settings the command writes, unless it has none of
     * them, outside the environment blocks or in the block of any environment. Settings that are appended come
     * after the ones that are there and apply in every environment, so the command writes nothing when there are
     * any, unless it is given {@code --force}.
     *
     * @param keys the full keys of the settings the command writes
     * @param written what the command writes, as {@code --force} writes it
     * @return whether the configuration has none of the settings, and could be read
     */
    private boolean hasNoneOf(Collection<String> keys, String written) {
        String newLine = System.lineSeparator()
        String force = "To write ${written} all the same, run the command again with --force"
        List<ApplicationConfigAppender.PresentSettings> present
        try {
            present = new ApplicationConfigAppender(file('')).findPresent(keys)
        } catch (IOException e) {
            error("${e.message}${newLine}Whether it has settings that s2-quickstart writes is not known, so the command " +
                    "wrote nothing. Correct the file. ${force}.")
            return false
        }
        if (!present) {
            return true
        }
        StringBuilder message = new StringBuilder('The configuration of the application already has settings that ')
        message.append('s2-quickstart writes, so the command wrote nothing:').append(newLine)
        present.each { ApplicationConfigAppender.PresentSettings settings ->
            message.append('    ').append(settings.location).append(': ').append(settings.keys.join(', ')).append(newLine)
        }
        message.append('The settings it appends would replace them')
        if (present.any { ApplicationConfigAppender.PresentSettings settings ->
            settings.environment != null && settings.path == ApplicationConfigAppender.GROOVY_CONFIG
        }) {
            message.append(', except in an environment of the top-level environments block of application.groovy, ')
            message.append('which is applied after them and keeps its own value')
        }
        message.append('. ').append(force)
        message.append(', then merge the settings that were there into the ones it appends.')
        error(message.toString())
        false
    }

    private String creating() {
        String message = "\nCreating User class '${userModel.simpleName}'"
        message += requestmapModel ?
                ", Role class '${roleModel.simpleName}', and Requestmap class '${requestmapModel.simpleName}'" :
                " and Role class '${roleModel.simpleName}'"
        if (groupModel) {
            message += ", and role/group classes for '${groupModel.simpleName}'"
        }
        "${message} in package '${userModel.packageName}'"
    }

    /**
     * @return whether the password encoder listener of the user class is a bean of the project
     */
    private boolean createDomains() {
        generateFile('PersonWithoutInjection', userModel.simpleName)
        generateFile('PersonPasswordEncoderListener', "${userModel.simpleName}PasswordEncoderListener", 'src/main/groovy')
        generateFile('Authority', roleModel.simpleName)
        generateFile('PersonAuthority', userModel.simpleName + roleModel.simpleName)

        if (requestmapModel) {
            generateFile('Requestmap', requestmapModel.simpleName)
        }

        if (groupModel) {
            generateFile('AuthorityGroup', groupModel.simpleName)
            generateFile('PersonAuthorityGroup', userModel.simpleName + groupModel.simpleName)
            generateFile('AuthorityGroupAuthority', groupModel.simpleName + roleModel.simpleName)
        }
        registerListener()
    }

    private Map<String, Object> settings(boolean uiOnly) {
        Map<String, Object> settings = [:]
        if (!uiOnly) {
            String packageName = userModel.packageName
            settings["${SETTINGS}.userLookup.userDomainClassName".toString()] = "${packageName}.${userModel.simpleName}".toString()
            settings["${SETTINGS}.userLookup.authorityJoinClassName".toString()] = "${packageName}.${userModel.simpleName}${roleModel.simpleName}".toString()
            settings["${SETTINGS}.authority.className".toString()] = "${packageName}.${roleModel.simpleName}".toString()
            if (groupModel) {
                settings["${SETTINGS}.authority.groupAuthorityNameField".toString()] = 'authorities'
                settings["${SETTINGS}.useRoleGroups".toString()] = true
            }
            if (requestmapModel) {
                settings["${SETTINGS}.requestMap.className".toString()] = "${packageName}.${requestmapModel.simpleName}".toString()
                settings["${SETTINGS}.securityConfigType".toString()] = 'Requestmap'
            }
        }
        List<String> permitted = ['/', '/error', '/index', '/index.gsp', '/shutdown'] + OPEN_PATTERNS
        settings["${SETTINGS}.controllerAnnotations.staticRules".toString()] = permitted.collect { String pattern ->
            [pattern: pattern, access: ['permitAll']] as Map<String, Object>
        }
        List<Map<String, Object>> chains = OPEN_PATTERNS.collect { String pattern ->
            [pattern: pattern, filters: 'none'] as Map<String, Object>
        }
        chains.add([pattern: '/**', filters: 'JOINED_FILTERS'] as Map<String, Object>)
        settings["${SETTINGS}.filterChain.chainMap".toString()] = chains
        settings
    }

    private void generateFile(String templateName, String className, String folder = 'grails-app/domain') {
        render(template(templateName + '.groovy.template'),
                file("${folder}/${userModel.packagePath}/${className}.groovy"),
                templateAttributes, false)
    }

    /**
     * Makes the password encoder listener a bean: in {@code resources.groovy} of an application, in the plugin
     * class of a plugin that has no {@code resources.groovy}, and otherwise in a new {@code resources.groovy}.
     *
     * @return whether the listener is a bean now
     */
    private boolean registerListener() {
        String listener = "${userModel.simpleName}PasswordEncoderListener"
        String beanImport = "import ${userModel.packageName}.${listener}"
        String definition = "${userModel.propertyName}PasswordEncoderListener(${listener})"

        File resources = file(RESOURCES)
        File pluginClass = resources.isFile() ? null : findPluginClass()
        File target = pluginClass ?: resources
        String marker = pluginClass ? 'doWithSpring()' : 'beans = {'
        String indent = pluginClass ? '            ' : '    '
        if (!target.isFile()) {
            target.parentFile.mkdirs()
            write(target, [beanImport, '', 'beans = {', indent + definition, '}'])
            return true
        }

        List<String> present = target.readLines('UTF-8')
        if (present.any { String line -> line.trim() == definition }) {
            return true
        }
        if (!present.any { String line -> line.contains(marker) }) {
            error("The password encoder listener is not registered: ${projectPath(target)} has no line with '${marker}'. " +
                    "Until it has the bean '${definition}', with '${beanImport}', passwords are stored as they are given.")
            return false
        }
        int packageLine = present.findIndexOf { String line -> line.trim().startsWith('package ') }
        List<String> lines = []
        if (packageLine < 0) {
            lines.add(beanImport)
        }
        present.eachWithIndex { String line, int index ->
            lines.add(line)
            if (index == packageLine) {
                lines.add('')
                lines.add(beanImport)
            }
            if (line.contains(marker)) {
                lines.add(indent + definition)
            }
        }
        write(target, lines)
        true
    }

    private static void write(File target, List<String> lines) {
        target.withWriter('UTF-8') { Writer writer ->
            lines.each { String line ->
                writer.write(line)
                writer.write(System.lineSeparator())
            }
        }
    }

    private File findPluginClass() {
        File pluginClass = null
        File sources = file('src/main/groovy')
        if (sources.isDirectory()) {
            sources.eachFileRecurse { File each ->
                if (each.isFile() && each.name.endsWith('GrailsPlugin.groovy')) {
                    pluginClass = each
                }
            }
        }
        pluginClass
    }

}
