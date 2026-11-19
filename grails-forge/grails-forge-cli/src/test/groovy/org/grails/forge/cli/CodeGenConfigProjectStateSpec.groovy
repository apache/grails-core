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
package org.grails.forge.cli

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class CodeGenConfigProjectStateSpec extends Specification {

    static final String OLD_RECORD = 'applicationType: plugin\ndefaultPackage: org.older\nreloading: devtools\n' +
            'sourceLanguage: groovy\nfeatures: [gorm-hibernate5]\n'

    @TempDir
    Path tempDir

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext beanContext = Application.builder().run()

    List<String> warnings = []

    ConsoleOutput console = new ConsoleOutput() {
        @Override
        void out(String message) { }
        @Override
        void err(String message) { }
        @Override
        void warning(String message) { warnings << message }
        @Override
        boolean showStacktrace() { false }
        @Override
        boolean verbose() { false }
    }

    void "the state of the project is read before grails-forge-cli.yml"() {
        given:
        generate()
        Files.writeString(tempDir.resolve('grails-forge-cli.yml'), OLD_RECORD)

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), console)

        then:
        config.applicationType == ApplicationType.REST_API
        config.defaultPackage == 'com.example'
        config.reloading == DevelopmentReloading.JREBEL
        config.sourceLanguage == Language.DEFAULT_OPTION
        config.features.containsAll(['gorm-mongodb', 'jrebel', 'spock'])
        !config.features.contains('gorm-hibernate5')
        !config.legacy
        warnings.empty
    }

    void "state that cannot be used is said to be, and grails-forge-cli.yml is read"() {
        given:
        generate()
        Files.writeString(tempDir.resolve('grails-forge-cli.yml'), OLD_RECORD)
        Path manifest = tempDir.resolve('.grails/project.yml')
        Files.writeString(manifest, manifest.toFile().getText('UTF-8').replace('defaultPackage: com.example', 'defaultPackage: com.other'))

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), console)

        then:
        warnings == ['.grails/forge.lock.yml does not belong to .grails/project.yml: one of them was changed without the other']
        config.applicationType == ApplicationType.PLUGIN
        config.defaultPackage == 'org.older'
        config.features == ['gorm-hibernate5']
    }

    void "state that cannot be used and no grails-forge-cli.yml give no configuration"() {
        given:
        generate()
        Files.delete(tempDir.resolve('grails-forge-cli.yml'))
        Files.delete(tempDir.resolve('.grails/baseline/index.yml'))

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), console)

        then:
        config == null
        warnings == ['The state of the project is not complete: .grails/baseline/index.yml is missing']
    }

    void "a project without state is read from grails-forge-cli.yml without a word"() {
        given:
        Files.writeString(tempDir.resolve('grails-forge-cli.yml'), OLD_RECORD)

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), console)

        then:
        config.applicationType == ApplicationType.PLUGIN
        config.defaultPackage == 'org.older'
        warnings.empty
    }

    private void generate() {
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.REST_API,
                NameUtils.parse('com.example.shop'),
                new Options(DevelopmentReloading.JREBEL, GormImpl.MONGODB, ServletImpl.TOMCAT, JdkVersion.DEFAULT_OPTION, OperatingSystem.LINUX),
                OperatingSystem.LINUX,
                [],
                new FileSystemOutputHandler(tempDir.toFile(), ConsoleOutput.NOOP),
                ConsoleOutput.NOOP)
    }
}
