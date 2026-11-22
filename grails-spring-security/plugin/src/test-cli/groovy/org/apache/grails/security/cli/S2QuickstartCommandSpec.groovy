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

import java.nio.file.Files

import org.yaml.snakeyaml.Yaml

import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLineParser
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Runs s2-quickstart on a project of its own, as runCommand runs it there.
 */
class S2QuickstartCommandSpec extends Specification {

    @TempDir
    File project

    void setup() {
        // the plugin's templates, which a project may also override in its src/main/templates
        File templates = new File('src/main/templates')
        assert templates.isDirectory()
        templates.eachFile { File template ->
            File copy = new File(project, "src/main/templates/${template.name}")
            copy.parentFile.mkdirs()
            Files.copy(template.toPath(), copy.toPath())
        }
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'grails-app/conf/application.yml').text = 'grails:\n    profile: web\n'
    }

    void 'with --uiOnly alone the plugin is configured and no domain class is created'() {
        when:
        boolean succeeded = run('s2-quickstart', '--uiOnly')

        then:
        succeeded
        !new File(project, 'grails-app/domain').exists()
        new File(project, 'grails-app/conf/application.yml').text.contains('springsecurity')
    }

    void 'with --uiOnly and the classes named no domain class is created either'() {
        when:
        boolean succeeded = run('s2-quickstart', 'com.example', 'User', 'Role', '--uiOnly')

        then:
        succeeded
        !new File(project, 'grails-app/domain').exists()
    }

    void 'the classes named are created, a request map class among them when one is named'() {
        when:
        boolean succeeded = run(['s2-quickstart', 'com.example', 'User', 'Role'] + requestmap as String[])

        then:
        succeeded
        new File(project, 'grails-app/domain/com/example/User.groovy').isFile()
        new File(project, 'grails-app/domain/com/example/Role.groovy').isFile()
        new File(project, 'grails-app/domain/com/example/Requestmap.groovy').isFile() == !requestmap.empty

        and: 'the settings are where the plugin reads them'
        Map security = securityConfig()
        security.userLookup == [userDomainClassName: 'com.example.User', authorityJoinClassName: 'com.example.UserRole']
        security.authority == [className: 'com.example.Role']
        security.requestMap == (requestmap ? [className: 'com.example.Requestmap'] : null)
        security.securityConfigType == (requestmap ? 'Requestmap' : null)
        security.controllerAnnotations.staticRules.size() > 0
        security.filterChain.chainMap.size() > 0

        and: 'the listener that encodes the passwords is registered, in a resources.groovy the application did not have'
        new File(project, 'grails-app/conf/spring/resources.groovy').text.contains('userPasswordEncoderListener(UserPasswordEncoderListener)')

        where:
        requestmap << [[], ['Requestmap']]
    }

    void 'with role groups the settings of the groups are where the plugin reads them'() {
        when:
        boolean succeeded = run('s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=RoleGroup')

        then:
        succeeded
        Map security = securityConfig()
        security.authority == [className: 'com.example.Role', groupAuthorityNameField: 'authorities']
        security.useRoleGroups == true
        security.controllerAnnotations.staticRules.size() > 0
    }

    void 'the listener is added to a resources.groovy the application has'() {
        given:
        File resources = new File(project, 'grails-app/conf/spring/resources.groovy')
        resources.parentFile.mkdirs()
        resources.text = 'beans = {\n}\n'

        when:
        run('s2-quickstart', 'com.example', 'User', 'Role')

        then:
        resources.text.contains('userPasswordEncoderListener(UserPasswordEncoderListener)')
        resources.text.contains('import com.example.UserPasswordEncoderListener')
    }

    void 'without the classes named the usage is reported, and nothing is written'() {
        when:
        boolean succeeded = run('s2-quickstart', 'com.example')

        then:
        !succeeded
        !new File(project, 'grails-app/domain').exists()
        !new File(project, 'grails-app/conf/application.yml').text.contains('springsecurity')
    }

    /** The settings of the plugin in the document quickstart adds to application.yml, parsed as YAML */
    private Map securityConfig() {
        List<Object> documents = new Yaml().loadAll(new File(project, 'grails-app/conf/application.yml').text).toList()
        ((Map) documents.last()).grails.plugin.springsecurity as Map
    }

    private boolean run(String... commandLine) {
        File baseDir = project
        ExecutionContext context = new ExecutionContext(new CommandLineParser().parse(commandLine)) {
            @Override
            File getBaseDir() {
                baseDir
            }
        }
        new S2QuickstartCommand().handle(context)
    }
}
