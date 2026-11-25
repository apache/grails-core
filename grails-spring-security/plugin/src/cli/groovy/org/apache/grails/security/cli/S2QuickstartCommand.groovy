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
 * <p>Everything the command writes is worked out before any of it is written: the class files, the place of the
 * bean of the password encoder listener, and the configuration file. When one of them cannot be written, the
 * command says so and writes nothing, so a run that failed leaves nothing in the way of the next one.</p>
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
        // everything the command writes is worked out first, so that it writes all of it or nothing
        Map<String, File> classes = uiOnly ? [:] as Map<String, File> : classFiles()
        BeanDefinitionWriter.Placement listener = null
        if (!uiOnly) {
            if (!canCreate(classes)) {
                return FAILURE
            }
            listener = placeListener()
            if (listener == null) {
                return FAILURE
            }
        }
        if (!canAppendConfig()) {
            return FAILURE
        }

        addStatus(uiOnly ? '\nConfiguring Spring Security; not generating domain classes' : creating())
        List<String> written = []
        String path
        try {
            classes.each { String templateName, File destination ->
                boolean present = destination.exists()
                render(template(templateName + '.groovy.template'), destination, templateAttributes, false)
                if (!present && destination.exists()) {
                    written.add(projectPath(destination))
                }
            }
            if (listener?.change) {
                listener.write()
                written.add(projectPath(listener.file))
            }
            path = appendConfig(settings)
        } catch (Exception e) {
            error("s2-quickstart stopped before it had written everything: ${e.message?.trim() ?: e.class.name}. " +
                    (written ? "It wrote ${written.join(', ')} before that, and nothing else." : 'It wrote nothing.'))
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
     * @return the files of the classes the command creates, in the order they are written, by the name of the
     * template each is rendered from
     */
    private Map<String, File> classFiles() {
        Map<String, File> files = [:]
        files.PersonWithoutInjection = classFile(userModel.simpleName)
        files.PersonPasswordEncoderListener = classFile("${userModel.simpleName}PasswordEncoderListener", 'src/main/groovy')
        files.Authority = classFile(roleModel.simpleName)
        files.PersonAuthority = classFile(userModel.simpleName + roleModel.simpleName)
        if (requestmapModel) {
            files.Requestmap = classFile(requestmapModel.simpleName)
        }
        if (groupModel) {
            files.AuthorityGroup = classFile(groupModel.simpleName)
            files.PersonAuthorityGroup = classFile(userModel.simpleName + groupModel.simpleName)
            files.AuthorityGroupAuthority = classFile(groupModel.simpleName + roleModel.simpleName)
        }
        files
    }

    private File classFile(String className, String folder = 'grails-app/domain') {
        file("${folder}/${userModel.packagePath}/${className}.groovy")
    }

    /**
     * Says which of the class files cannot be created, when there are any: a file that is there already, which
     * {@code --force} keeps as it is, a template that is not found, or a folder that cannot be made.
     *
     * @param classes the files by the names of their templates
     * @return whether every file can be created, or is kept with {@code --force}
     */
    private boolean canCreate(Map<String, File> classes) {
        String newLine = System.lineSeparator()
        List<File> present = classes.values().findAll { File each -> each.exists() }.toList()
        if (present && !isFlagPresent('force')) {
            error("These files are there already, so the command wrote nothing:${newLine}" +
                    present.collect { File each -> "    ${projectPath(each)}${newLine}" }.join('') +
                    'To keep them as they are and write the rest, run the command again with --force.')
            return false
        }
        List<String> missing = classes.keySet().findAll { String name -> !template(name + '.groovy.template')?.exists() }.toList()
        if (missing) {
            error("The command wrote nothing: it has no template ${missing.collect { String name -> name + '.groovy.template' }.join(', ')}.")
            return false
        }
        File blocked = classes.values().find { File each -> !each.exists() && !canBeCreated(each) }
        if (blocked != null) {
            error("${projectPath(blocked)} cannot be created, so the command wrote nothing.")
            return false
        }
        true
    }

    // whether the folder of the file is there to write to, or the nearest folder above it that is
    private static boolean canBeCreated(File target) {
        File parent = target.absoluteFile.parentFile
        while (parent != null && !parent.exists()) {
            parent = parent.parentFile
        }
        parent != null && parent.isDirectory() && parent.canWrite()
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

    /**
     * Works out where the password encoder listener becomes a bean: in {@code resources.groovy} of an
     * application, in the plugin class of a plugin that has no {@code resources.groovy}, and otherwise in a new
     * {@code resources.groovy}. The bean is written once, at the start of the closure the project is started
     * with: the last {@code beans = { }} of {@code resources.groovy}, or the closure {@code doWithSpring()}
     * returns. A file that leaves it open which closure that is gets no bean, see {@link BeanDefinitionWriter};
     * that is said here, before the command has written anything.
     *
     * @return the file with the bean, to be written, or {@code null} when the bean cannot be added, which was said
     */
    private BeanDefinitionWriter.Placement placeListener() {
        String listener = "${userModel.simpleName}PasswordEncoderListener"
        BeanDefinitionWriter writer = new BeanDefinitionWriter("${userModel.propertyName}PasswordEncoderListener",
                userModel.packageName, listener)

        File resources = file(RESOURCES)
        File pluginClass = resources.isFile() ? null : findPluginClass()
        BeanDefinitionWriter.Placement placement = pluginClass ?
                writer.placeInPluginClass(pluginClass, '            ') : writer.placeInResources(resources, '    ')
        String refused = placement.refused
        if (refused == null && placement.change && !(placement.file.exists() ? placement.file.canWrite() : canBeCreated(placement.file))) {
            refused = 'cannot be written to'
        }
        if (refused == null) {
            return placement
        }
        String closure = pluginClass ? "the closure that 'doWithSpring()' returns" : "the closure the application reads from 'beans'"
        error("The password encoder listener is not registered: ${projectPath(placement.file)} ${refused}. " +
                "Without the bean '${writer.definition}', with '${writer.beanImport}', in ${closure} " +
                'passwords are stored as they are given, so the command wrote nothing. ' +
                'Change the file so that the command can add the bean, or add the bean by hand, and run the command again.')
        null
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
