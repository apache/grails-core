/*
 * Copyright 2023 Puneet Behl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import groovy.transform.CompileStatic

/**
 * Creates domain classes and updates config settings for the Spring Security plugin.
 * Usage: <code>./grailsw s2-quickstart [DOMAIN_CLASS_PACKAGE] [USER_CLASS_NAME] [ROLE_CLASS_NAME] [REQUEST_MAP_CLASS_NAME] --groupClassName=[GROUP_CLASS_NAME]</code> or
 * <code>s2-quickstart --ui-only</code>
 *
 * For Example:
 * 1. <code>./grailsw s2-quickstart com.yourapp User Role --groupClassName=RoleGroup</code>
 * 2. <code>./grailsw s2-quickstart com.yourapp Person Authority Requestmap</code>
 * 3. <code>./grailsw s2-quickstart --uiOnly</code>
 * 4. <code>./grailsw s2-quickstart com.yourapp User Role</code>
 *
 * @author Puneet Behl
 * @since 6.0.0
 */
@CompileStatic
class S2QuickstartCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    public static final String GORM_VERSION_THRESHOLD = '6.0.10'
    private Map<String, String> templateAttributes
    private boolean uiOnly
    private boolean salt
    private String packageName
    private Model userModel
    private Model roleModel
    private Model requestmapModel
    private Model roleGroupModel

    String description = 'Creates domain classes and updates config settings for the Spring Security plugin.'

    private final static String USAGE_MESSAGE = '''
   ./grailsw s2-quickstart [DOMAIN-CLASS-PACKAGE] [USER-CLASS-NAME] [ROLE-CLASS-NAME] [REQUESTMAP-CLASS-NAME] --groupClassName=GROUP-CLASS-NAME
or ./grailsw s2-quickstart --uiOnly

Example: ./grailsw s2-quickstart com.yourapp User Role
Example: ./grailsw s2-quickstart com.yourapp User Role --groupClassName=RoleGroup
Example: ./grailsw s2-quickstart com.yourapp Person Authority Requestmap
Example: ./grailsw s2-quickstart --uiOnly
'''

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {

        if (uiOnly) {
            consoleLogger.addStatus('\nConfiguring Spring Security; not generating domain classes')
        } else {
            if (args.size() < 3) {
                error('Usage:' + USAGE_MESSAGE)
                return FAILURE
            }
            initialize()
            initializeTemplateAttributes()
            createDomains(userModel, roleModel, requestmapModel, roleGroupModel)
        }
        if (new File('grails-app/conf/application.yml').exists()) {
            consoleLogger.addStatus('\nConfiguring Spring Security; Updating YAML Application Config')
            updateYmlConfig(userModel?.simpleName, roleModel?.simpleName, requestmapModel?.simpleName, userModel?.packageName, roleGroupModel != null)
        } else {
            updateConfig(userModel?.simpleName, roleModel?.simpleName, requestmapModel?.simpleName, userModel?.packageName, roleGroupModel != null)
        }
        logStatus()
        return SUCCESS
    }

    private void logStatus() {
        if (uiOnly) {
            consoleLogger.addStatus '''
************************************************************
* Your grails-app/conf/application.groovy has been updated *
* with security settings; please verify that the           *
* values are correct.                                      *
************************************************************
'''
        } else {
            consoleLogger.addStatus '''
************************************************************
* Created security-related domain classes. Your            *
* grails-app/conf/application.groovy has been updated with *
* the class names of the configured domain classes;        *
* please verify that the values are correct.               *
************************************************************
'''
        }
    }

    private void initializeTemplateAttributes() {
        templateAttributes = Collections.unmodifiableMap([
                packageName        : userModel.packageName,
                userClassName      : userModel.simpleName,
                userClassProperty  : userModel.modelName,
                roleClassName      : roleModel.simpleName,
                roleClassProperty  : roleModel.modelName,
                requestmapClassName: requestmapModel?.simpleName,
                groupClassName     : roleGroupModel?.simpleName,
                groupClassProperty : roleGroupModel?.modelName])
    }

    private void initialize() {
        uiOnly = isFlagPresent('uiOnly')
        salt = flagValue('salt')

        packageName = args[0]
        userModel = model(packageName + '.' + args[1])
        if (userModel) {
            consoleLogger.addStatus('\nCreating User class ' + userModel.simpleName + ' in package ' + packageName)
        }
        roleModel = model(packageName + '.' + args[2])
        if (roleModel) {
            consoleLogger.addStatus('\nCreating Role class ' + roleModel.simpleName + ' in package ' + packageName)
        }
        final String groupClassName = flagValue('groupClassName')
        roleGroupModel = groupClassName ? model(packageName + '.' + groupClassName) : null
        if (roleGroupModel) {
            consoleLogger.addStatus('\nCreating Role/Group classes ' + roleGroupModel.simpleName + ' in package ' + packageName)
        }
    }

    private Map<String, Integer> extractVersion(String versionString) {
        String[] arr = versionString.split('\\.')
        Map<String, Integer> v = new HashMap<>([mayor: 0, minor: 0, bug: 0])
        try {
            if (arr.size() >= 1) {
                v.mayor = arr[0].toInteger()
            }
            if (arr.size() >= 2) {
                v.minor = arr[1].toInteger()
            }
            if (arr.size() >= 3) {
                v.bug = arr[2].toInteger()
            }
        } catch (Exception e) {
            v = [mayor: 0, minor: 0, bug: 0]
        }
        v
    }

    private boolean versionAfterOrEqualsToThreshold(String threshold, String value) {
        if (value == null) {
            return false
        }
        if (value.startsWith(threshold)) {
            return true
        }

        Map<String, Integer> va = extractVersion(value)
        Map<String, Integer> vb = extractVersion(threshold)
        List<Map<String, Integer>> l = [va, vb]
        l.sort { a, b ->
            def compare = a.mayor <=> b.mayor
            if (compare != 0) {
                return compare
            }
            compare = a.minor <=> b.minor
            if (compare != 0) {
                return compare
            }
            a.bug <=> b.bug
        }
        String sortedValue = l[0].collect { k, v -> v }.join('.')
        threshold.startsWith(sortedValue)
    }

    private void createDomains(Model userModel,
                               Model roleModel,
                               Model requestmapModel,
                               Model groupModel) {

        final Properties props = new Properties()
        file("gradle.properties")?.withInputStream { props.load(it) }
        
        generateFile('PersonWithoutInjection', userModel.packagePath, userModel.simpleName)
        if (salt) {
            generateFile('PersonPasswordEncoderListenerWithSalt',
                    userModel.packagePath,
                    userModel.simpleName,
                    "${userModel.simpleName}PasswordEncoderListener", 'src/main/groovy')
        } else {
            generateFile('PersonPasswordEncoderListener',
                    userModel.packagePath,
                    userModel.simpleName,
                    "${userModel.simpleName}PasswordEncoderListener",
                    'src/main/groovy')
        }
        List<Map<String, String>> beans = []
        beans.add([import    : "import ${userModel.packageName}.${userModel.simpleName}PasswordEncoderListener".toString(),
                   definition: "${userModel.propertyName}PasswordEncoderListener(${userModel.simpleName}PasswordEncoderListener)".toString()])

        if(new File('grails-app/conf/spring/resources.groovy').exists()) {
            addBeans(beans, 'grails-app/conf/spring/resources.groovy')
        } else {
            //we could be generating this in a plugin... we should look for a Plugin class
            File pluginClassFile = findPluginClass()
            if(pluginClassFile  != null) {
                 addBeansToPlugin(beans, pluginClassFile)
            }
          }


        generateFile('Authority', roleModel.packagePath, roleModel.simpleName)
        generateFile('PersonAuthority', roleModel.packagePath, userModel.simpleName + roleModel.simpleName)

        if (requestmapModel) {
            generateFile('Requestmap', requestmapModel.packagePath, requestmapModel.simpleName)
        }

        if (groupModel) {
            generateFile('AuthorityGroup', groupModel.packagePath, groupModel.simpleName)
            generateFile('PersonAuthorityGroup', groupModel.packagePath, userModel.simpleName + groupModel.simpleName)
            generateFile('AuthorityGroupAuthority', groupModel.packagePath, groupModel.simpleName + roleModel.simpleName)
        }
    }

    private void updateConfig(String userClassName, String roleClassName, String requestmapClassName, String packageName, boolean useRoleGroups) {

        file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
            writer.newLine()
            writer.newLine()
            writer.writeLine('// Added by the Spring Security Core plugin:')
            if (!uiOnly) {
                writer.writeLine("grails.plugin.springsecurity.userLookup.userDomainClassName = '${packageName}.$userClassName'")
                writer.writeLine("grails.plugin.springsecurity.userLookup.authorityJoinClassName = '${packageName}.$userClassName$roleClassName'")
                writer.writeLine("grails.plugin.springsecurity.authority.className = '${packageName}.$roleClassName'")
            }
            if (useRoleGroups) {
                writer.writeLine("grails.plugin.springsecurity.authority.groupAuthorityNameField = 'authorities'")
                writer.writeLine('grails.plugin.springsecurity.useRoleGroups = true')
            }
            if (requestmapClassName) {
                writer.writeLine("grails.plugin.springsecurity.requestMap.className = '${packageName}.$requestmapClassName'")
                writer.writeLine("grails.plugin.springsecurity.securityConfigType = 'Requestmap'")
            }
            writer.writeLine('grails.plugin.springsecurity.controllerAnnotations.staticRules = [')
            writer.writeLine("\t[pattern: '/',               access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/error',          access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/index',          access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/index.gsp',      access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/shutdown',       access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/assets/**',      access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/**/js/**',       access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/**/css/**',      access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/**/images/**',   access: ['permitAll']],")
            writer.writeLine("\t[pattern: '/**/favicon.ico', access: ['permitAll']]")
            writer.writeLine(']')
            writer.newLine()

            writer.writeLine('grails.plugin.springsecurity.filterChain.chainMap = [')
            writer.writeLine("\t[pattern: '/assets/**',      filters: 'none'],")
            writer.writeLine("\t[pattern: '/**/js/**',       filters: 'none'],")
            writer.writeLine("\t[pattern: '/**/css/**',      filters: 'none'],")
            writer.writeLine("\t[pattern: '/**/images/**',   filters: 'none'],")
            writer.writeLine("\t[pattern: '/**/favicon.ico', filters: 'none'],")
            writer.writeLine("\t[pattern: '/**',             filters: 'JOINED_FILTERS']")
            writer.writeLine(']')
            writer.newLine()
        }
    }

    private void updateYmlConfig(String userClassName, String roleClassName, String requestmapClassName, String packageName, boolean useRoleGroups) {
        //I mean, we could use SNAKE YAML TO GENERATE THIS BUT I DIGRESS
        file('grails-app/conf/application.yml').withWriterAppend { BufferedWriter writer ->
            writer.newLine()
            writer.writeLine('---')
            writer.writeLine('# Added by the Spring Security Core plugin:')
            writer.writeLine('grails:')
            writer.writeLine('    plugin:')
            writer.writeLine('        springsecurity:')
            if (!uiOnly) {
                writer.writeLine('            userLookup:')
                writer.writeLine("                userDomainClassName: '${packageName}.$userClassName'")
                writer.writeLine("                authorityJoinClassName: '${packageName}.$userClassName$roleClassName'")
            }
            if(!uiOnly || useRoleGroups) {
                writer.writeLine('        authority:')
                if(!uiOnly) {
                    writer.writeLine("            className: '${packageName}.$roleClassName'")
                }
                if(useRoleGroups) {
                    writer.writeLine("            groupAuthorityNameField: authorities")
                }
            }

            if (useRoleGroups) {
                writer.writeLine('        useRoleGroups: true')
            }
            if (requestmapClassName) {
                writer.writeLine("            requestMap.className: '${packageName}.$requestmapClassName'")
                writer.writeLine("            securityConfigType: Requestmap")
            }
            writer.writeLine('            controllerAnnotations:')
            writer.writeLine('                staticRules:')
            writer.writeLine('                    - pattern: /')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /error')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /index')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /index.gsp')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /shutdown')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /assets/**')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /**/js/**')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /**/css/**')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /**/images/**')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.writeLine('                    - pattern: /**/favicon.ico')
            writer.writeLine('                      access: [\'permitAll\']')
            writer.newLine()

            writer.writeLine('            filterChain:')
            writer.writeLine('                chainMap:')
            writer.writeLine('                    - pattern: /assets/**')
            writer.writeLine('                      filters: none')
            writer.writeLine('                    - pattern: /**/js/**')
            writer.writeLine('                      filters: none')
            writer.writeLine('                    - pattern: /**/css/**')
            writer.writeLine('                      filters: none')
            writer.writeLine('                    - pattern: /**/images/**')
            writer.writeLine('                      filters: none')
            writer.writeLine('                    - pattern: /**/favicon.ico')
            writer.writeLine('                      filters: none')
            writer.writeLine('                    - pattern: /**')
            writer.writeLine('                      filters: JOINED_FILTERS')
            writer.newLine()
        }
    }

    private void generateFile(String templateName, String packagePath, String className, String fileName = null, String folder = 'grails-app/domain') {
        render template(templateName + '.groovy.template'),
                file("${folder}/$packagePath/${fileName ?: className}.groovy"),
                templateAttributes, false
    }

    private void addBeans(List<Map<String, String>> beans, String resourceConfigFilePath) {
        final File resourceConfig = new File(resourceConfigFilePath)
        List<String> lines = []
        beans.forEach(bean -> lines.add(bean.import))
        if (resourceConfig.exists()) {
            resourceConfig.eachLine { line, nb ->
                lines << line
                if (line.contains('beans = {')) {
                    beans.each { Map bean ->
                        lines << '    ' + bean.definition
                    }
                }
            }
        } else {
            lines << 'beans = {'
            beans.each { Map bean ->
                lines << '    ' + bean.definition
            }
            lines << '}'
        }

        resourceConfig.withWriter('UTF-8') { writer ->
            lines.each { String line ->
                writer.write "${line}${System.lineSeparator()}"
            }
        }
    }

    private void addBeansToPlugin(List<Map<String, String>> beans, File pluginClassFile) {
        List<String> lines = []
        if (pluginClassFile.exists()) {
            pluginClassFile.eachLine { line, nb ->
                lines << line
                if(line.trim().startsWith('package ')) {
                     beans.forEach(bean -> lines.add(bean.import))
                }
                if (line.contains('doWithSpring()')) {
                    beans.each { Map bean ->
                        lines << '        ' + bean.definition
                    }
                }
            }
        } 

        pluginClassFile.withWriter('UTF-8') { writer ->
            lines.each { String line ->
                writer.write "${line}${System.lineSeparator()}"
            }
        }
    }


    private File findPluginClass() {
        File pluginClass = null
        new File("src/main/groovy").eachFileRecurse { fl ->
            if (fl.isFile() && fl.name.endsWith("GrailsPlugin.groovy")) {
                pluginClass = fl
            }
        }
        return pluginClass
    }

}

