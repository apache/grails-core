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

import groovy.io.FileType

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.io.FileSystemResource

import grails.build.logging.GrailsConsole
import grails.util.Environment
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser
import org.grails.config.CodeGenConfig
import org.grails.config.PropertySourcesConfig
import org.grails.config.yaml.YamlPropertySourceLoader
import org.grails.core.cfg.GroovyConfigPropertySourceLoader

/**
 * What the commands of the Spring Security Core plugin write into a project: the domain classes, the password
 * encoder listener and its bean, and the settings, in the configuration file the project has.
 */
class SecurityCommandsSpec extends Specification {

    private static final String SETTINGS = 'grails.plugin.springsecurity'
    private static final String YAML = 'grails-app/conf/application.yml'
    private static final String GROOVY = 'grails-app/conf/application.groovy'
    private static final String RESOURCES = 'grails-app/conf/spring/resources.groovy'
    private static final String PRESENT_YAML = 'grails:\n    profile: web\n---\ninfo:\n    app:\n        name: demo\n'

    /** What {@code grails feature add grails-spring-security} writes to application.groovy. */
    private static final String FEATURE_GROOVY = '''\
// The plugin denies every URL without a rule (rejectIfNoRule): these rules open
// the public pages and restrict the user administration to ROLE_ADMIN.
grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'com.example.UserRole'
grails.plugin.springsecurity.authority.className = 'com.example.Role'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/',                    access: ['permitAll']],
    [pattern: '/error',               access: ['permitAll']],
    [pattern: '/assets/**',           access: ['permitAll']],
    [pattern: '/user/**',             access: ['ROLE_ADMIN']],
]
'''
    private static final String NESTED_GROOVY = '''\
grails {
    plugin {
        springsecurity {
            userLookup {
                userDomainClassName = 'com.example.Person'
            }
        }
    }
}
'''
    private static final String USER_YAML = '''\
---
grails:
    plugin:
        springsecurity:
            userLookup:
                userDomainClassName: com.example.User
            controllerAnnotations:
                staticRules:
                    - pattern: /user/**
                      access: [ROLE_ADMIN]
'''
    private static final String DOTTED_YAML = '---\ngrails.plugin.springsecurity.userLookup.userDomainClassName: com.example.User\n'
    private static final String CHAIN_YAML = '''\
---
grails:
    plugin:
        springsecurity:
            filterChain:
                chainMap:
                    - pattern: /api/**
                      filters: JOINED_FILTERS,-exceptionTranslationFilter
'''
    /** A rule that production alone has: there, the home page is for administrators. */
    private static final String PRODUCTION_RULE_YAML = '''\
environments:
    production:
        grails:
            plugin:
                springsecurity:
                    controllerAnnotations:
                        staticRules:
                            - pattern: /
                              access: [ROLE_ADMIN]
'''
    private static final String PRODUCTION_RULE_FIRST_YAML = 'grails:\n    profile: web\n' + PRODUCTION_RULE_YAML + '---\ninfo:\n    app:\n        name: demo\n'
    private static final String PRODUCTION_RULE_GROOVY = '''\
environments {
    production {
        grails.plugin.springsecurity.controllerAnnotations.staticRules = [
            [pattern: '/', access: ['ROLE_ADMIN']]
        ]
    }
}
'''
    private static final String PRODUCTION_CHAIN_YAML = '''\
---
environments.production.grails.plugin.springsecurity.filterChain.chainMap:
    - pattern: /api/**
      filters: JOINED_FILTERS
'''
    private static final String DEVELOPMENT_USER_YAML = '''\
---
environments:
    development:
        grails.plugin.springsecurity.userLookup.userDomainClassName: com.example.Person
'''
    private static final String DEVELOPMENT_USER_GROOVY = '''\
environments {
    development {
        grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.Person'
    }
}
'''
    private static final String STAGING_GROUPS_YAML = '''\
---
environments:
    staging:
        grails:
            plugin:
                springsecurity:
                    useRoleGroups: false
'''
    private static final String TEST_CHAIN_YAML = '''\
---
environments:
    test:
        grails:
            plugin:
                springsecurity:
                    filterChain:
                        chainMap:
                            - pattern: /**
                              filters: JOINED_FILTERS
'''
    private static final String NESTED_PRODUCTION_GROOVY = '''\
grails {
    plugin {
        springsecurity {
            environments {
                production {
                    securityConfigType = 'Annotation'
                }
            }
        }
    }
}
'''
    /** Settings of production that s2-quickstart --uiOnly does not write. */
    private static final String PRODUCTION_OTHER_YAML = '''\
---
environments:
    production:
        grails:
            plugin:
                springsecurity:
                    rejectIfNoRule: true
                    userLookup:
                        userDomainClassName: com.example.Person
'''
    private static final String PRODUCTION_OTHER_GROOVY = '''\
environments {
    production {
        grails.plugin.springsecurity.rejectIfNoRule = true
        grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.Person'
    }
}
'''
    /** Code that only the environment it is written for can run, as when it reads the secrets of that environment. */
    private static final String PRODUCTION_ONLY_GROOVY = '''\
grails.gorm.failOnError = true
environments {
    production {
        throw new IllegalStateException('the secrets of production are not here')
    }
}
'''
    private static final List<String> OPEN =['/assets/**', '/**/js/**', '/**/css/**', '/**/images/**', '/**/favicon.ico']
    private static final List<Map<String, Object>> QUICKSTART_RULES = (['/', '/error', '/index', '/index.gsp', '/shutdown'] + OPEN).collect { String pattern ->
        [pattern: pattern, access: ['permitAll']] as Map<String, Object>
    }
    private static final List<Map<String, Object>> QUICKSTART_CHAINS = OPEN.collect { String pattern ->
        [pattern: pattern, filters: 'none'] as Map<String, Object>
    } + [[pattern: '/**', filters: 'JOINED_FILTERS'] as Map<String, Object>]
    /** The settings of the plugin that the specs read as an application reads them. */
    private static final List<String> EFFECTIVE = ['userLookup.userDomainClassName', 'userLookup.authorityJoinClassName',
            'authority.className', 'authority.groupAuthorityNameField', 'useRoleGroups', 'requestMap.className',
            'securityConfigType', 'controllerAnnotations.staticRules', 'filterChain.chainMap', 'rejectIfNoRule'].collect { String key ->
        "${SETTINGS}.${key}".toString()
    }

    @TempDir
    File project

    ByteArrayOutputStream output = new ByteArrayOutputStream()
    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        // the commands that are made in a feature say their errors where the feature reads them
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(output, true), new PrintStream(errors, true)))
        write(YAML, PRESENT_YAML)
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "s2-quickstart creates the user, role and join classes, the password encoder listener and its bean"() {
        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        errors.toString() == ''
        generated() == [
                RESOURCES,
                'grails-app/domain/com/example/Role.groovy',
                'grails-app/domain/com/example/User.groovy',
                'grails-app/domain/com/example/UserRole.groovy',
                'src/main/groovy/com/example/UserPasswordEncoderListener.groovy'
        ]

        and: 'a user class that leaves the hashing of its password to the listener'
        written('grails-app/domain/com/example/User.groovy').contains('package com.example\n')
        written('grails-app/domain/com/example/User.groovy').contains('class User implements Serializable {')
        written('grails-app/domain/com/example/User.groovy').contains('Set<Role> getAuthorities() {')
        written('grails-app/domain/com/example/User.groovy').contains('UserRole.findAllByUser(this)')
        written('grails-app/domain/com/example/User.groovy').contains("table name: '`user`'")
        !written('grails-app/domain/com/example/User.groovy').contains('springSecurityService')
        written('grails-app/domain/com/example/Role.groovy').contains('class Role implements Serializable {')
        written('grails-app/domain/com/example/Role.groovy').contains('String authority')
        written('grails-app/domain/com/example/UserRole.groovy').contains('class UserRole implements Serializable {')
        written('grails-app/domain/com/example/UserRole.groovy').contains('User user')
        written('grails-app/domain/com/example/UserRole.groovy').contains('Role role')

        and:
        written('src/main/groovy/com/example/UserPasswordEncoderListener.groovy').contains('package com.example\n')
        written('src/main/groovy/com/example/UserPasswordEncoderListener.groovy').contains('class UserPasswordEncoderListener {')
        written('src/main/groovy/com/example/UserPasswordEncoderListener.groovy').contains('@Listener(User)')
        written('src/main/groovy/com/example/UserPasswordEncoderListener.groovy').contains('springSecurityService.encodePassword(password)')
        written(RESOURCES).readLines() == [
                'import com.example.UserPasswordEncoderListener',
                '',
                'beans = {',
                '    userPasswordEncoderListener(UserPasswordEncoderListener)',
                '}'
        ]
    }

    def "s2-quickstart adds the settings to application.yml of a project that has no application.groovy"() {
        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')
        CodeGenConfig config = config()

        then:
        result
        !new File(project, GROOVY).exists()
        written(YAML).startsWith(PRESENT_YAML + '---\n# Added by the Spring Security Core plugin:\ngrails:\n    plugin:\n        springsecurity:\n')
        output.toString().contains("Your ${YAML}")

        and: 'what was there is read as before'
        config.getProperty('grails.profile', String) == 'web'
        config.getProperty('info.app.name', String) == 'demo'

        and: 'every setting is under the key the plugin reads'
        config.getProperty("${SETTINGS}.userLookup.userDomainClassName", String) == 'com.example.User'
        config.getProperty("${SETTINGS}.userLookup.authorityJoinClassName", String) == 'com.example.UserRole'
        config.getProperty("${SETTINGS}.authority.className", String) == 'com.example.Role'
        config.getProperty("${SETTINGS}.controllerAnnotations.staticRules", List) == [
                [pattern: '/', access: ['permitAll']],
                [pattern: '/error', access: ['permitAll']],
                [pattern: '/index', access: ['permitAll']],
                [pattern: '/index.gsp', access: ['permitAll']],
                [pattern: '/shutdown', access: ['permitAll']],
                [pattern: '/assets/**', access: ['permitAll']],
                [pattern: '/**/js/**', access: ['permitAll']],
                [pattern: '/**/css/**', access: ['permitAll']],
                [pattern: '/**/images/**', access: ['permitAll']],
                [pattern: '/**/favicon.ico', access: ['permitAll']]
        ]
        config.getProperty("${SETTINGS}.filterChain.chainMap", List) == [
                [pattern: '/assets/**', filters: 'none'],
                [pattern: '/**/js/**', filters: 'none'],
                [pattern: '/**/css/**', filters: 'none'],
                [pattern: '/**/images/**', filters: 'none'],
                [pattern: '/**/favicon.ico', filters: 'none'],
                [pattern: '/**', filters: 'JOINED_FILTERS']
        ]

        and: 'and nothing that was not asked for'
        config.getProperty("${SETTINGS}.requestMap.className", String) == null
        config.getProperty("${SETTINGS}.securityConfigType", String) == null
        config.getProperty("${SETTINGS}.useRoleGroups", Boolean) == null
        config.getProperty('grails.plugin', Map).keySet() == ['springsecurity'] as Set
    }

    @Issue('https://github.com/apache/grails-core/issues/15850')
    def "s2-quickstart with a fourth argument creates the requestmap class and configures it in #file"() {
        given:
        if (file == GROOVY) {
            write(GROOVY, '')
        }

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example Person Authority Requestmap')
        CodeGenConfig config = config()

        then:
        result
        errors.toString() == ''
        output.toString().contains("Creating User class 'Person', Role class 'Authority', and Requestmap class 'Requestmap' in package 'com.example'")
        generated() == [
                RESOURCES,
                'grails-app/domain/com/example/Authority.groovy',
                'grails-app/domain/com/example/Person.groovy',
                'grails-app/domain/com/example/PersonAuthority.groovy',
                'grails-app/domain/com/example/Requestmap.groovy',
                'src/main/groovy/com/example/PersonPasswordEncoderListener.groovy'
        ]
        written('grails-app/domain/com/example/Requestmap.groovy').contains('package com.example\n')
        written('grails-app/domain/com/example/Requestmap.groovy').contains('class Requestmap implements Serializable {')
        written('grails-app/domain/com/example/Requestmap.groovy').contains('String configAttribute')
        written('grails-app/domain/com/example/Requestmap.groovy').contains('HttpMethod httpMethod')
        !written('grails-app/domain/com/example/Person.groovy').contains('table name')

        and:
        config.getProperty("${SETTINGS}.requestMap.className", String) == 'com.example.Requestmap'
        config.getProperty("${SETTINGS}.securityConfigType", String) == 'Requestmap'
        config.getProperty("${SETTINGS}.userLookup.userDomainClassName", String) == 'com.example.Person'
        config.getProperty("${SETTINGS}.userLookup.authorityJoinClassName", String) == 'com.example.PersonAuthority'
        config.getProperty("${SETTINGS}.authority.className", String) == 'com.example.Authority'

        where:
        file << [YAML, GROOVY]
    }

    def "s2-quickstart --groupClassName creates the role group classes and configures them in #file"() {
        given:
        if (file == GROOVY) {
            write(GROOVY, '')
        }

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role --groupClassName=RoleGroup')
        CodeGenConfig config = config()

        then:
        result
        output.toString().contains("Creating User class 'User' and Role class 'Role', and role/group classes for 'RoleGroup' in package 'com.example'")
        generated() == [
                RESOURCES,
                'grails-app/domain/com/example/Role.groovy',
                'grails-app/domain/com/example/RoleGroup.groovy',
                'grails-app/domain/com/example/RoleGroupRole.groovy',
                'grails-app/domain/com/example/User.groovy',
                'grails-app/domain/com/example/UserRole.groovy',
                'grails-app/domain/com/example/UserRoleGroup.groovy',
                'src/main/groovy/com/example/UserPasswordEncoderListener.groovy'
        ]
        written('grails-app/domain/com/example/User.groovy').contains('Set<RoleGroup> getAuthorities() {')
        written('grails-app/domain/com/example/User.groovy').contains('UserRoleGroup.findAllByUser(this)')
        written('grails-app/domain/com/example/RoleGroup.groovy').contains('class RoleGroup implements Serializable {')
        written('grails-app/domain/com/example/RoleGroup.groovy').contains('Set<Role> getAuthorities() {')
        written('grails-app/domain/com/example/RoleGroupRole.groovy').contains('class RoleGroupRole implements Serializable {')
        written('grails-app/domain/com/example/UserRoleGroup.groovy').contains('class UserRoleGroup implements Serializable {')

        and:
        config.getProperty("${SETTINGS}.authority.groupAuthorityNameField", String) == 'authorities'
        config.getProperty("${SETTINGS}.authority.className", String) == 'com.example.Role'
        config.getProperty("${SETTINGS}.useRoleGroups", Boolean)
        config.getProperty("${SETTINGS}.requestMap.className", String) == null

        where:
        file << [YAML, GROOVY]
    }

    def "s2-quickstart --uiOnly creates no class and adds the rules alone to #file"() {
        given:
        if (file == GROOVY) {
            write(GROOVY, '')
        }

        when:
        boolean result = run(new S2QuickstartCommand(), line)
        CodeGenConfig config = config()

        then:
        result
        errors.toString() == ''
        generated() == []
        output.toString().contains('Configuring Spring Security; not generating domain classes')
        output.toString().contains("Your ${file} has been updated with security settings")
        config.getProperty("${SETTINGS}.controllerAnnotations.staticRules", List).size() == 10
        config.getProperty("${SETTINGS}.filterChain.chainMap", List).last() == [pattern: '/**', filters: 'JOINED_FILTERS']
        config.getProperty(SETTINGS, Map).keySet() == ['controllerAnnotations', 'filterChain'] as Set

        where:
        line                                     | file
        's2-quickstart --uiOnly'                 | YAML
        's2-quickstart --uiOnly'                 | GROOVY
        's2-quickstart com.example User --uiOnly' | YAML
    }

    def "s2-quickstart appends the settings to application.groovy of a project that has it, as it is written by hand"() {
        given:
        write(GROOVY, "grails.gorm.failOnError = true")

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role Requestmap --groupClassName=RoleGroup')

        then:
        result
        written(YAML) == PRESENT_YAML
        output.toString().contains("Your ${GROOVY}")
        written(GROOVY).readLines() == [
                'grails.gorm.failOnError = true',
                '',
                '// Added by the Spring Security Core plugin:',
                "grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'",
                "grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'com.example.UserRole'",
                "grails.plugin.springsecurity.authority.className = 'com.example.Role'",
                "grails.plugin.springsecurity.authority.groupAuthorityNameField = 'authorities'",
                'grails.plugin.springsecurity.useRoleGroups = true',
                "grails.plugin.springsecurity.requestMap.className = 'com.example.Requestmap'",
                "grails.plugin.springsecurity.securityConfigType = 'Requestmap'",
                'grails.plugin.springsecurity.controllerAnnotations.staticRules = [',
                "\t[pattern: '/',               access: ['permitAll']],",
                "\t[pattern: '/error',          access: ['permitAll']],",
                "\t[pattern: '/index',          access: ['permitAll']],",
                "\t[pattern: '/index.gsp',      access: ['permitAll']],",
                "\t[pattern: '/shutdown',       access: ['permitAll']],",
                "\t[pattern: '/assets/**',      access: ['permitAll']],",
                "\t[pattern: '/**/js/**',       access: ['permitAll']],",
                "\t[pattern: '/**/css/**',      access: ['permitAll']],",
                "\t[pattern: '/**/images/**',   access: ['permitAll']],",
                "\t[pattern: '/**/favicon.ico', access: ['permitAll']]",
                ']',
                'grails.plugin.springsecurity.filterChain.chainMap = [',
                "\t[pattern: '/assets/**',      filters: 'none'],",
                "\t[pattern: '/**/js/**',       filters: 'none'],",
                "\t[pattern: '/**/css/**',      filters: 'none'],",
                "\t[pattern: '/**/images/**',   filters: 'none'],",
                "\t[pattern: '/**/favicon.ico', filters: 'none'],",
                "\t[pattern: '/**',             filters: 'JOINED_FILTERS']",
                ']'
        ]
        config().getProperty('grails.gorm.failOnError', Boolean)
    }

    def "a project without a configuration file gets application.groovy"() {
        given:
        new File(project, YAML).delete()

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        !new File(project, YAML).exists()
        config().getProperty("${SETTINGS}.authority.className", String) == 'com.example.Role'
    }

    def "s2-quickstart --force keeps the classes that are there, and registers the listener once"() {
        given: 'what the command created, edited since'
        assert run(new S2QuickstartCommand(), 's2-quickstart com.example User Role Requestmap')
        List<String> files = generated() - RESOURCES
        files.each { String path -> write(path, 'edited') }
        String resources = written(RESOURCES)

        when: 'it is run again, with the option that writes over the settings it wrote the first time'
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role Requestmap --force')

        then: 'a file that is there is passed over, which is no failure'
        result
        files.size() == 5
        files.every { String path -> written(path) == 'edited' }
        written(RESOURCES) == resources
        generated() == ([RESOURCES] + files).sort()

        and: 'the settings are added again, and read as they were'
        written(YAML).count('# Added by the Spring Security Core plugin:') == 2
        config().getProperty("${SETTINGS}.requestMap.className", String) == 'com.example.Requestmap'
    }

    def "s2-quickstart adds the bean of the listener to the resources.groovy of the project"() {
        given:
        write(RESOURCES, '// Place your Spring DSL code here\nbeans = {\n    other(Object)\n}\n')

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        written(RESOURCES).readLines() == [
                'import com.example.UserPasswordEncoderListener',
                '// Place your Spring DSL code here',
                'beans = {',
                '    userPasswordEncoderListener(UserPasswordEncoderListener)',
                '    other(Object)',
                '}'
        ]
    }

    def "s2-quickstart fails, says so and writes nothing when it cannot add the bean of the listener to resources.groovy"() {
        given: 'a resources.groovy that is written another way'
        write(RESOURCES, 'beans {\n}\n')
        Map<String, String> before = snapshot()

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        !result
        written(RESOURCES) == 'beans {\n}\n'
        errors.toString().contains("The password encoder listener is not registered: ${RESOURCES} sets or reads 'beans' in line 1, in another way than with 'beans = { ... }' as a statement of the script")
        errors.toString().contains("'userPasswordEncoderListener(UserPasswordEncoderListener)'")
        errors.toString().contains("'import com.example.UserPasswordEncoderListener'")

        and: 'no class and no setting is written either: a user class without the listener would keep passwords as they are given'
        snapshot() == before
        generated() == [RESOURCES]
        config().getProperty("${SETTINGS}.authority.className", String) == null
    }

    def "s2-quickstart in a plugin without resources.groovy adds the bean of the listener to the plugin class"() {
        given:
        String pluginClass = 'src/main/groovy/demo/DemoGrailsPlugin.groovy'
        write(pluginClass, '''package demo

import grails.plugins.Plugin

class DemoGrailsPlugin extends Plugin {

    Closure doWithSpring() { {->
            // TODO Implement runtime spring config (optional)
        }
    }
}
''')

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        !new File(project, RESOURCES).exists()
        written(pluginClass) == '''package demo

import com.example.UserPasswordEncoderListener

import grails.plugins.Plugin

class DemoGrailsPlugin extends Plugin {

    Closure doWithSpring() { {->
            userPasswordEncoderListener(UserPasswordEncoderListener)
            // TODO Implement runtime spring config (optional)
        }
    }
}
'''
    }

    def "s2-quickstart adds the bean of the listener to the closure doWithSpring() returns when the closure is opened on a line of its own"() {
        given: 'a plugin class that names doWithSpring() in a comment first'
        String pluginClass = 'src/main/groovy/demo/DemoGrailsPlugin.groovy'
        write(pluginClass, '''package demo

import grails.plugins.Plugin

class DemoGrailsPlugin extends Plugin {

    // doWithSpring() { -> } returns the beans of the plugin
    Closure doWithSpring() {
        { ->
            other(Object)
        }
    }
}
''')

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        written(pluginClass) == '''package demo

import com.example.UserPasswordEncoderListener

import grails.plugins.Plugin

class DemoGrailsPlugin extends Plugin {

    // doWithSpring() { -> } returns the beans of the plugin
    Closure doWithSpring() {
        { ->
            userPasswordEncoderListener(UserPasswordEncoderListener)
            other(Object)
        }
    }
}
'''
    }

    def "s2-quickstart fails and says so when the doWithSpring() of the plugin class returns no closure it can add the bean of the listener to"() {
        given:
        String pluginClass = 'src/main/groovy/demo/DemoGrailsPlugin.groovy'
        String text = 'package demo\n\nimport grails.plugins.Plugin\n\nclass DemoGrailsPlugin extends Plugin {\n    Closure doWithSpring() { null }\n}\n'
        write(pluginClass, text)

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        !result
        written(pluginClass) == text
        errors.toString().contains("The password encoder listener is not registered: ${pluginClass} has no closure that 'doWithSpring()' returns as its only statement.")
        generated() == []
    }

    def "s2-quickstart adds the bean of the listener once, to the last beans = { that is not in a comment"() {
        given: 'a resources.groovy that names the closure in comments, and has it twice'
        write(RESOURCES, '''// beans = { is where the beans go
/*
beans = {
    old(Object)
}
*/
beans = { // the beans of the application
    other(Object)
}
beans = {
}
''')

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        written(RESOURCES).readLines() == [
                'import com.example.UserPasswordEncoderListener',
                '// beans = { is where the beans go',
                '/*',
                'beans = {',
                '    old(Object)',
                '}',
                '*/',
                'beans = { // the beans of the application',
                '    other(Object)',
                '}',
                'beans = {',
                '    userPasswordEncoderListener(UserPasswordEncoderListener)',
                '}'
        ]
    }

    def "a template of the project is rendered in place of the one of the plugin"() {
        given:
        write('src/main/templates/Authority.groovy.template', 'package ${packageName}\n\nclass ${roleClassName} { /* of the project */ }\n')

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        result
        written('grails-app/domain/com/example/Role.groovy') == 'package com.example\n\nclass Role { /* of the project */ }\n'
        written('grails-app/domain/com/example/UserRole.groovy').contains('class UserRole implements Serializable {')
    }

    @Unroll
    def "#line fails, says #said and writes nothing"() {
        when:
        boolean result = run(new S2QuickstartCommand(), line)

        then:
        !result
        errors.toString().contains(said)
        errors.toString().contains('Example: s2-quickstart com.yourapp Person Authority Requestmap') == usage
        generated() == []
        written(YAML) == PRESENT_YAML
        !new File(project, GROOVY).exists()

        where:
        line                                                   | said                                                                 | usage
        's2-quickstart'                                        | 'The package and the names of the user and the role class are required.' | true
        's2-quickstart com.example User'                       | 'The package and the names of the user and the role class are required.' | true
        's2-quickstart com.example User Role Requestmap More'  | 'Too many arguments.'                                                | true
        's2-quickstart s2-quickstart User Role'                | "'s2-quickstart' is no package name."                                | true
        's2-quickstart com.example User com.example.Role'      | "'com.example.Role' is no class name"                                | true
        's2-quickstart com.example User Role Request-map'      | "'Request-map' is no class name"                                     | true
        's2-quickstart com.example User Role --groupClassName=role-group' | "'role-group' is no class name"                           | true
        's2-quickstart com.example User User'                  | 'The classes need names that differ.'                                | true
        's2-quickstart --ui-only'                              | 'Unknown option --ui-only.'                                          | true
        's2-quickstart com.example User Role --group=RoleGroup' | 'Unknown option --group.'                                           | true
        's2-quickstart com.example User Role --salt'           | 'The option --salt is no longer supported'                           | false
    }

    @Unroll
    def "#name creates the domain class and names it in #file"() {
        given:
        if (file == GROOVY) {
            write(GROOVY, '')
        }

        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), "${name} com.example.${className}")
        CodeGenConfig config = config()

        then:
        result
        errors.toString() == ''
        output.toString().contains("Creating ${what} class com.example.${className}")
        output.toString().contains("Your ${file} has been updated")
        generated() == ["grails-app/domain/com/example/${className}.groovy".toString()]
        written("grails-app/domain/com/example/${className}.groovy").contains('package com.example\n')
        expected.every { String each -> written("grails-app/domain/com/example/${className}.groovy").contains(each) }
        settings.every { String key, Object value -> config.getProperty("${SETTINGS}.${key}", Object) == value }
        config.getProperty(SETTINGS, Map).keySet() == settings.keySet()*.tokenize('.')*.first() as Set
        (file == GROOVY) == (written(YAML) == PRESENT_YAML)
        (file == GROOVY) || !new File(project, GROOVY).exists()

        where:
        name                             | command                           | className            | file   | what                   | expected                                                                              | settings
        's2-create-persistent-token'     | S2CreatePersistentTokenCommand    | 'PersistentLogin'    | YAML   | 'persistent token'     | ['class PersistentLogin implements Serializable {', "table 'persistent_login'"]       | ['rememberMe.persistent': true, 'rememberMe.persistentToken.domainClassName': 'com.example.PersistentLogin']
        's2-create-persistent-token'     | S2CreatePersistentTokenCommand    | 'PersistentLogin'    | GROOVY | 'persistent token'     | ['class PersistentLogin implements Serializable {', 'String series']                  | ['rememberMe.persistent': true, 'rememberMe.persistentToken.domainClassName': 'com.example.PersistentLogin']
        's2-create-role-hierarchy-entry' | S2CreateRoleHierarchyEntryCommand | 'RoleHierarchyEntry' | YAML   | 'role hierarchy entry' | ['class RoleHierarchyEntry implements Serializable {', 'String entry']                | ['roleHierarchyEntryClassName': 'com.example.RoleHierarchyEntry']
        's2-create-role-hierarchy-entry' | S2CreateRoleHierarchyEntryCommand | 'RoleHierarchyEntry' | GROOVY | 'role hierarchy entry' | ['class RoleHierarchyEntry implements Serializable {', "entry nullable: false"]       | ['roleHierarchyEntryClassName': 'com.example.RoleHierarchyEntry']
    }

    def "the settings of a domain class are appended to application.groovy as assignments"() {
        given:
        write(GROOVY, '// of the project\n')

        expect:
        run(new S2CreatePersistentTokenCommand(), 's2-create-persistent-token com.example.PersistentLogin')
        run(new S2CreateRoleHierarchyEntryCommand(), 's2-create-role-hierarchy-entry com.example.RoleHierarchyEntry')
        written(GROOVY).readLines() == [
                '// of the project',
                '',
                '// Added by the Spring Security Core plugin:',
                'grails.plugin.springsecurity.rememberMe.persistent = true',
                "grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = 'com.example.PersistentLogin'",
                '',
                '// Added by the Spring Security Core plugin:',
                "grails.plugin.springsecurity.roleHierarchyEntryClassName = 'com.example.RoleHierarchyEntry'"
        ]
    }

    @Unroll
    def "#name keeps a domain class that is there"() {
        given:
        write("grails-app/domain/com/example/${className}.groovy", 'edited')

        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), "${name} com.example.${className}")

        then:
        result
        written("grails-app/domain/com/example/${className}.groovy") == 'edited'
        config().getProperty("${SETTINGS}.${key}", String) == "com.example.${className}"

        where:
        name                             | command                           | className            | key
        's2-create-persistent-token'     | S2CreatePersistentTokenCommand    | 'PersistentLogin'    | 'rememberMe.persistentToken.domainClassName'
        's2-create-role-hierarchy-entry' | S2CreateRoleHierarchyEntryCommand | 'RoleHierarchyEntry' | 'roleHierarchyEntryClassName'
    }

    @Unroll
    def "#line fails, says #said with the usage and writes nothing"() {
        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), line)

        then:
        !result
        errors.toString().contains(said)
        errors.toString().contains("Example: ${line.tokenize(' ').first()} com.yourapp.")
        generated() == []
        written(YAML) == PRESENT_YAML
        !new File(project, GROOVY).exists()

        where:
        command                           | line                                                    | said
        S2CreatePersistentTokenCommand    | 's2-create-persistent-token'                            | 'The name of the domain class is required.'
        S2CreatePersistentTokenCommand    | 's2-create-persistent-token com.example.A com.example.B' | 'Too many arguments.'
        S2CreatePersistentTokenCommand    | 's2-create-persistent-token PersistentLogin'            | "'PersistentLogin' is no full name of a class"
        S2CreatePersistentTokenCommand    | 's2-create-persistent-token com.example.Persistent-Login' | "'com.example.Persistent-Login' is no full name of a class"
        S2CreatePersistentTokenCommand    | 's2-create-persistent-token com.example.PersistentLogin --force' | 'Unknown option --force.'
        S2CreateRoleHierarchyEntryCommand | 's2-create-role-hierarchy-entry'                        | 'The name of the domain class is required.'
        S2CreateRoleHierarchyEntryCommand | 's2-create-role-hierarchy-entry com.example.A com.example.B' | 'Too many arguments.'
        S2CreateRoleHierarchyEntryCommand | 's2-create-role-hierarchy-entry RoleHierarchyEntry'     | "'RoleHierarchyEntry' is no full name of a class"
        S2CreateRoleHierarchyEntryCommand | 's2-create-role-hierarchy-entry com.example.RoleHierarchyEntry --force' | 'Unknown option --force.'
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart writes nothing when the configuration already has settings it writes: #what"() {
        given:
        if (groovy != null) {
            write(GROOVY, groovy)
        }
        write(YAML, yaml)

        when:
        boolean result = run(new S2QuickstartCommand(), line)

        then: 'the command fails as for a wrong argument'
        !result

        and: 'it names each file with the keys that it sets, and the option that writes all the same'
        String said = errors.toString()
        said.contains('The configuration of the application already has settings that s2-quickstart writes')
        present.every { String path, List<String> keys ->
            said.contains("    ${path}: ${keys.collect { String key -> "${SETTINGS}.${key}" }.join(', ')}")
        }
        [GROOVY, YAML].findAll { String path -> !present.containsKey(path) }.every { String path -> !said.contains("    ${path}: ") }
        said.contains('--force')
        !output.toString().contains('Creating User class')

        and: 'no class, no listener, no bean and no setting is written'
        generated() == []
        written(YAML) == yaml
        groovy == null ? !new File(project, GROOVY).exists() : written(GROOVY) == groovy

        where:
        what                                                  | line                                             | groovy             | yaml                         || present
        'the application.groovy of the feature'               | 's2-quickstart com.example.auth Person Authority' | FEATURE_GROOVY     | PRESENT_YAML                 || [(GROOVY): ['userLookup.userDomainClassName', 'userLookup.authorityJoinClassName', 'authority.className', 'controllerAnnotations.staticRules']]
        'a block of application.groovy'                       | 's2-quickstart com.example User Role'           | NESTED_GROOVY      | PRESENT_YAML                 || [(GROOVY): ['userLookup.userDomainClassName']]
        'a document of application.yml'                       | 's2-quickstart com.example User Role'           | null               | PRESENT_YAML + USER_YAML     || [(YAML): ['userLookup.userDomainClassName', 'controllerAnnotations.staticRules']]
        'a dotted key of application.yml'                     | 's2-quickstart com.example User Role'           | null               | PRESENT_YAML + DOTTED_YAML   || [(YAML): ['userLookup.userDomainClassName']]
        'application.yml beside the application.groovy written' | 's2-quickstart com.example User Role'         | 'grails.gorm.failOnError = true' | PRESENT_YAML + USER_YAML || [(YAML): ['userLookup.userDomainClassName', 'controllerAnnotations.staticRules']]
        'both files'                                          | 's2-quickstart com.example User Role'           | FEATURE_GROOVY     | PRESENT_YAML + USER_YAML     || [(GROOVY): ['userLookup.userDomainClassName', 'userLookup.authorityJoinClassName', 'authority.className', 'controllerAnnotations.staticRules'], (YAML): ['userLookup.userDomainClassName', 'controllerAnnotations.staticRules']]
        'the type of the security configuration'              | 's2-quickstart com.example User Role Requestmap' | "grails.plugin.springsecurity.securityConfigType = 'Annotation'" | PRESENT_YAML || [(GROOVY): ['securityConfigType']]
        'role groups'                                         | 's2-quickstart com.example User Role --groupClassName=RoleGroup' | 'grails.plugin.springsecurity.useRoleGroups = false' | PRESENT_YAML || [(GROOVY): ['useRoleGroups']]
        'the static rules, with --uiOnly'                     | 's2-quickstart --uiOnly'                         | FEATURE_GROOVY     | PRESENT_YAML                 || [(GROOVY): ['controllerAnnotations.staticRules']]
        'the filter chain, with --uiOnly'                     | 's2-quickstart --uiOnly'                         | null               | PRESENT_YAML + CHAIN_YAML    || [(YAML): ['filterChain.chainMap']]
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    def "s2-quickstart run a second time writes nothing, and says what the first run wrote"() {
        given:
        assert run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')
        Map<String, String> before = (generated() + [YAML]).collectEntries { String path -> [(path): written(path)] }
        errors.reset()

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example Person Authority')

        then:
        !result
        errors.toString().contains("    ${YAML}: ${SETTINGS}.userLookup.userDomainClassName, ${SETTINGS}.userLookup.authorityJoinClassName, " +
                "${SETTINGS}.authority.className, ${SETTINGS}.controllerAnnotations.staticRules, ${SETTINGS}.filterChain.chainMap")
        generated() + [YAML] == before.keySet() as List
        before.every { String path, String text -> written(path) == text }
        config().getProperty("${SETTINGS}.userLookup.userDomainClassName", String) == 'com.example.User'
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    def "s2-quickstart --force creates the classes and appends the settings after the ones #file has"() {
        given:
        String present = file == GROOVY ? FEATURE_GROOVY : PRESENT_YAML + USER_YAML
        write(file, present)

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example.auth Person Authority --force')
        CodeGenConfig config = config()

        then: 'it does what it does in an application without the settings'
        result
        errors.toString() == ''
        output.toString().contains("Creating User class 'Person' and Role class 'Authority' in package 'com.example.auth'")
        output.toString().contains("Your ${file}")
        generated() == [
                RESOURCES,
                'grails-app/domain/com/example/auth/Authority.groovy',
                'grails-app/domain/com/example/auth/Person.groovy',
                'grails-app/domain/com/example/auth/PersonAuthority.groovy',
                'src/main/groovy/com/example/auth/PersonPasswordEncoderListener.groovy'
        ]

        and: 'the settings follow what was there, which is kept, and are the ones read'
        written(file).startsWith(present)
        written(file).count('Added by the Spring Security Core plugin:') == 1
        config.getProperty("${SETTINGS}.userLookup.userDomainClassName", String) == 'com.example.auth.Person'
        config.getProperty("${SETTINGS}.authority.className", String) == 'com.example.auth.Authority'
        !config.getProperty("${SETTINGS}.controllerAnnotations.staticRules", List).any { Map rule -> rule.pattern == '/user/**' }

        where:
        file << [GROOVY, YAML]
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart writes as before when the configuration has #what"() {
        given:
        write(GROOVY, groovy)

        when:
        boolean result = run(new S2QuickstartCommand(), line)

        then:
        result
        errors.toString() == ''
        written(GROOVY).startsWith(groovy)
        written(GROOVY).contains('// Added by the Spring Security Core plugin:')
        config().getProperty("${SETTINGS}.filterChain.chainMap", List).last() == [pattern: '/**', filters: 'JOINED_FILTERS']

        where:
        what                                                       | line                                  | groovy
        'other settings of the plugin'                             | 's2-quickstart com.example User Role' | "grails.plugin.springsecurity.logout.postOnly = false\ngrails.plugin.springsecurity.rejectIfNoRule = true\n"
        'a key that it reads and does not set'                     | 's2-quickstart com.example User Role' | "String name = grails.plugin.springsecurity.userLookup.userDomainClassName\n"
        'the names of the domain classes, which --uiOnly does not write' | 's2-quickstart --uiOnly'        | "grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'\n"
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart writes nothing when it cannot read #file#how, unless it is given --force"() {
        given:
        write(file, text)
        String yaml = written(YAML)

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role')

        then:
        !result
        errors.toString().contains(said)
        errors.toString().contains('Whether it has settings that s2-quickstart writes is not known, so the command wrote nothing.')
        errors.toString().contains('--force')
        generated() == []
        written(file) == text
        written(YAML) == yaml

        when:
        errors.reset()
        result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role --force')

        then:
        result
        generated().size() == 5

        where:
        file   | how                                        | text                                           || said
        GROOVY | ''                                         | 'grails.plugin.springsecurity.userLookup = [\n' || "${GROOVY} could not be read with the blocks of all its environments: "
        GROOVY | ' when the block of production fails here' | PRODUCTION_ONLY_GROOVY                         || "${GROOVY} could not be read with the blocks of all its environments: the secrets of production are not here"
        YAML   | ''                                         | PRESENT_YAML + '---\ngrails: [\n'              || "${YAML} could not be read: "
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart writes nothing when an environment has a setting it writes: #what"() {
        given:
        if (groovy != null) {
            write(GROOVY, groovy)
        }
        write(YAML, yaml)
        Map<String, String> files = snapshot()
        Map<String, Object> production = effective(Environment.PRODUCTION.name)
        Map<String, Object> development = effective(Environment.DEVELOPMENT.name)

        when:
        boolean result = run(new S2QuickstartCommand(), line)

        then: 'the command fails, and names the environment of each setting with its file'
        !result
        String said = errors.toString()
        said.contains('The configuration of the application already has settings that s2-quickstart writes')
        said.readLines().findAll { String each -> each.startsWith('    grails-app/') } == present.collect { String each -> "    ${each}".toString() }
        said.contains('The settings it appends would replace them')
        said.contains('top-level environments block of application.groovy, which is applied after them and keeps its own value') ==
                present.any { String each -> each.startsWith("${GROOVY}, environment ") }
        said.contains('--force')
        !output.toString().contains('Creating User class')

        and: 'every file is as it was, byte for byte, and no other file is written'
        snapshot() == files

        and: 'an application reads in every environment what it read before'
        effective(Environment.PRODUCTION.name) == production
        effective(Environment.DEVELOPMENT.name) == development

        where:
        what                                                     | line                                                              | groovy                 | yaml                                                                 || present
        'production, in the document of the other settings'      | 's2-quickstart --uiOnly'                                          | null                   | PRODUCTION_RULE_FIRST_YAML                                           || ["${YAML}, environment production: ${SETTINGS}.controllerAnnotations.staticRules"]
        'production, in a document of its own'                   | 's2-quickstart --uiOnly'                                          | null                   | PRESENT_YAML + '---\n' + PRODUCTION_RULE_YAML                        || ["${YAML}, environment production: ${SETTINGS}.controllerAnnotations.staticRules"]
        'production, by a key written in full'                   | 's2-quickstart --uiOnly'                                          | null                   | PRESENT_YAML + PRODUCTION_CHAIN_YAML                                 || ["${YAML}, environment production: ${SETTINGS}.filterChain.chainMap"]
        'the environment the command runs in'                    | 's2-quickstart com.example User Role'                             | null                   | PRESENT_YAML + DEVELOPMENT_USER_YAML                                 || ["${YAML}, environment development: ${SETTINGS}.userLookup.userDomainClassName"]
        'an environment of the application'                      | 's2-quickstart com.example User Role --groupClassName=RoleGroup'  | null                   | PRESENT_YAML + STAGING_GROUPS_YAML                                   || ["${YAML}, environment staging: ${SETTINGS}.useRoleGroups"]
        'two environments, and outside them'                     | 's2-quickstart com.example User Role'                             | null                   | PRESENT_YAML + USER_YAML + '---\n' + PRODUCTION_RULE_YAML + TEST_CHAIN_YAML || ["${YAML}: ${SETTINGS}.userLookup.userDomainClassName, ${SETTINGS}.controllerAnnotations.staticRules", "${YAML}, environment production: ${SETTINGS}.controllerAnnotations.staticRules", "${YAML}, environment test: ${SETTINGS}.filterChain.chainMap"]
        'production, in application.groovy'                      | 's2-quickstart --uiOnly'                                          | PRODUCTION_RULE_GROOVY | PRESENT_YAML                                                         || ["${GROOVY}, environment production: ${SETTINGS}.controllerAnnotations.staticRules"]
        'production, in a block of application.groovy'           | 's2-quickstart com.example User Role Requestmap'                  | NESTED_PRODUCTION_GROOVY | PRESENT_YAML                                                       || ["${GROOVY}, environment production: ${SETTINGS}.securityConfigType"]
        'the environment the command runs in, in application.groovy' | 's2-quickstart com.example User Role'                         | DEVELOPMENT_USER_GROOVY | PRESENT_YAML                                                        || ["${GROOVY}, environment development: ${SETTINGS}.userLookup.userDomainClassName"]
        'production in application.groovy, test in application.yml' | 's2-quickstart --uiOnly'                                      | PRODUCTION_RULE_GROOVY | PRESENT_YAML + TEST_CHAIN_YAML                                       || ["${GROOVY}, environment production: ${SETTINGS}.controllerAnnotations.staticRules", "${YAML}, environment test: ${SETTINGS}.filterChain.chainMap"]
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart --uiOnly run in #environment keeps the rule that production alone has, when it is #what"() {
        given:
        if (groovy != null) {
            write(GROOVY, groovy)
        }
        write(YAML, yaml)

        expect: 'production restricts the home page, development has no rule'
        effective(Environment.PRODUCTION.name)["${SETTINGS}.controllerAnnotations.staticRules"] == [[pattern: '/', access: ['ROLE_ADMIN']]]
        effective(Environment.DEVELOPMENT.name)["${SETTINGS}.controllerAnnotations.staticRules"] == null

        when:
        boolean result = inEnvironment(environment) { run(new S2QuickstartCommand(), 's2-quickstart --uiOnly') }

        then: 'the command fails without --force, says so as the guide shows, and both environments read what they read'
        !result
        List<String> said = errors.toString().readLines()
        said.first().endsWith('The configuration of the application already has settings that s2-quickstart writes, so the command wrote nothing:')
        said.drop(1) == [
                "    ${file}, environment production: ${SETTINGS}.controllerAnnotations.staticRules".toString(),
                "The settings it appends would replace them${except}. To write its settings all the same, run the command again with --force, then merge the settings that were there into the ones it appends.".toString()
        ]
        effective(Environment.PRODUCTION.name)["${SETTINGS}.controllerAnnotations.staticRules"] == [[pattern: '/', access: ['ROLE_ADMIN']]]
        effective(Environment.DEVELOPMENT.name)["${SETTINGS}.controllerAnnotations.staticRules"] == null

        where:
        what                                              | file   | groovy                 | yaml                                          | except
        'in the document of the other settings'           | YAML   | null                   | PRODUCTION_RULE_FIRST_YAML                    | ''
        'in a document of its own'                        | YAML   | null                   | PRESENT_YAML + '---\n' + PRODUCTION_RULE_YAML | ''
        'in the environments block of application.groovy' | GROOVY | PRODUCTION_RULE_GROOVY | PRESENT_YAML                                  | ', except in an environment of the top-level environments block of application.groovy, which is applied after them and keeps its own value'

        combined:
        environment << [Environment.DEVELOPMENT.name, Environment.TEST.name, Environment.PRODUCTION.name]
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart writes when the environments have other settings, and every environment reads what it wrote: #file"() {
        given:
        write(file, text)

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart --uiOnly')

        then:
        result
        errors.toString() == ''
        written(file).startsWith(text)

        and: 'the rules it wrote are read in every environment, beside the settings production has'
        [Environment.PRODUCTION.name, Environment.DEVELOPMENT.name, 'staging'].every { String environment ->
            Map<String, Object> read = effective(environment)
            read["${SETTINGS}.controllerAnnotations.staticRules"] == QUICKSTART_RULES &&
                    read["${SETTINGS}.filterChain.chainMap"] == QUICKSTART_CHAINS
        }
        effective(Environment.PRODUCTION.name)["${SETTINGS}.rejectIfNoRule"] == true
        effective(Environment.PRODUCTION.name)["${SETTINGS}.userLookup.userDomainClassName"] == 'com.example.Person'
        effective(Environment.DEVELOPMENT.name)["${SETTINGS}.userLookup.userDomainClassName"] == null

        where:
        file   | text
        YAML   | PRESENT_YAML + PRODUCTION_OTHER_YAML
        GROOVY | PRODUCTION_OTHER_GROOVY
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    def "s2-quickstart reads environments only at the top of a YAML document, as an application does"() {
        given: 'a key under grails that only looks like an environment block'
        write(YAML, PRESENT_YAML + '---\ngrails:\n    environments:\n        production:\n            plugin:\n                springsecurity:\n                    securityConfigType: Annotation\n')

        expect:
        effective(Environment.PRODUCTION.name)["${SETTINGS}.securityConfigType"] == null

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart com.example User Role Requestmap')

        then:
        result
        errors.toString() == ''
        effective(Environment.PRODUCTION.name)["${SETTINGS}.securityConfigType"] == 'Requestmap'
    }

    @Issue('https://github.com/apache/grails-core/issues/16348')
    @Unroll
    def "s2-quickstart --force over the rule of production: production reads #read"() {
        given:
        if (groovy != null) {
            write(GROOVY, groovy)
        }
        write(YAML, yaml)

        when:
        boolean result = run(new S2QuickstartCommand(), 's2-quickstart --uiOnly --force')

        then:
        result
        errors.toString() == ''
        effective(Environment.DEVELOPMENT.name)["${SETTINGS}.controllerAnnotations.staticRules"] == QUICKSTART_RULES
        effective(Environment.PRODUCTION.name)["${SETTINGS}.controllerAnnotations.staticRules"] == rules

        where:
        read                                                            | groovy                 | yaml                                          || rules
        'the rules appended to application.yml, which replace its own'  | null                   | PRESENT_YAML + '---\n' + PRODUCTION_RULE_YAML || QUICKSTART_RULES
        'its own rule, which application.groovy applies after the rules appended' | PRODUCTION_RULE_GROOVY | PRESENT_YAML                   || [[pattern: '/', access: ['ROLE_ADMIN']]]
    }

    /**
     * Runs a command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set. The templates are the ones of
     * the plugin, read from the class path as an application reads them from the jar of the plugin.
     */
    private boolean run(GrailsApplicationCommand command, String line) {
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * @return the configuration of the project as its files have it
     */
    private CodeGenConfig config() {
        CodeGenConfig config = new CodeGenConfig()
        File yaml = new File(project, YAML)
        if (yaml.isFile()) {
            config.loadYml(yaml)
        }
        config.loadGroovy(new File(project, GROOVY))
        config
    }

    /**
     * Reads the configuration files of the project as an application reads them when it starts in the environment:
     * with the property source loaders of Grails, and the configuration that Grails makes of their property sources.
     * The specs give each setting in one of the two files, so which of them comes first does not matter.
     *
     * @param environment the name of the environment
     * @return the settings of the plugin that the specs compare, by their full keys
     */
    private Map<String, Object> effective(String environment) {
        inEnvironment(environment) {
            MutablePropertySources sources = new MutablePropertySources()
            File groovy = new File(project, GROOVY)
            if (groovy.isFile()) {
                new GroovyConfigPropertySourceLoader().load(GROOVY, new FileSystemResource(groovy)).each { PropertySource<?> source ->
                    sources.addLast(source)
                }
            }
            File yaml = new File(project, YAML)
            if (yaml.isFile()) {
                new YamlPropertySourceLoader().load(YAML, new FileSystemResource(yaml)).each { PropertySource<?> source ->
                    sources.addLast(source)
                }
            }
            PropertySourcesConfig config = new PropertySourcesConfig(sources)
            EFFECTIVE.collectEntries { String key -> [(key): nested(config.getProperty(key, Object))] }
        }
    }

    /**
     * Does the work with the environment of Grails set to the one that is given, as {@code -Dgrails.env} sets it,
     * and then sets it back.
     */
    private static <T> T inEnvironment(String environment, Closure<T> work) {
        String previous = System.getProperty(Environment.KEY)
        System.setProperty(Environment.KEY, environment)
        try {
            work.call()
        } finally {
            if (previous == null) {
                System.clearProperty(Environment.KEY)
            } else {
                System.setProperty(Environment.KEY, previous)
            }
        }
    }

    /**
     * @return the value without the flat keys that the configuration of Grails keeps beside a nested entry, such as
     * {@code access[0]} beside {@code access} in a map of a list
     */
    private static Object nested(Object value) {
        if (value instanceof Map) {
            return ((Map<String, Object>) value).findAll { String key, Object each -> !key.contains('.') && !key.contains('[') }
                    .collectEntries { String key, Object each -> [(key): nested(each)] }
        }
        value instanceof List ? ((List) value).collect { Object each -> nested(each) } : value
    }

    /**
     * @return every file of the project, by its path in the project, with its bytes in Base64
     */
    private Map<String, String> snapshot() {
        Map<String, String> files = [:]
        project.eachFileRecurse(FileType.FILES) { File file ->
            files.put(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'), file.bytes.encodeBase64().toString())
        }
        files
    }

    /**
     * @return the files the commands create, as paths in the project, in order; the configuration files and
     * the templates of the project are not among them
     */
    private List<String> generated() {
        List<String> paths = []
        ['grails-app/domain', 'grails-app/conf/spring', 'src/main/groovy/com'].each { String directory ->
            File at = new File(project, directory)
            if (at.isDirectory()) {
                at.eachFileRecurse { File file ->
                    if (file.isFile()) {
                        paths.add(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'))
                    }
                }
            }
        }
        paths.sort()
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }
}
