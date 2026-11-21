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
package org.apache.grails.forge.core.state

import java.nio.file.Files
import java.nio.file.Path
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The record that is written for an application that was there: what the reader makes of it, and
 * that nothing is merged against the baseline it does not have.
 */
class AdoptedProjectRecordSpec extends ApplicationContextSpec {

    static final GenerationOptions OPTIONS = new GenerationOptions(JdkVersion.DEFAULT_OPTION, ServletImpl.TOMCAT, GormImpl.HIBERNATE5, DevelopmentReloading.NONE)

    @TempDir
    Path directory

    @TempDir
    Path home

    void "the record reads as one that was written for an application that was there, with a baseline that has no file"() {
        when:
        Map<String, byte[]> files = AdoptedProjectRecord.files(ApplicationType.REST_API, 'shop', 'com.acme', OPTIONS, ['grails-quartz', 'cache'], ['h2'],
                [new LockedFeature('gorm-hibernate5', LockedFeature.BUNDLED_PACK), new LockedFeature('cache', LockedFeature.BUNDLED_PACK),
                 new LockedFeature('grails-quartz', LockedFeature.BUNDLED_PACK)])
        write(files)
        ProjectState state = ProjectState.read(directory)

        then: 'the files are those of a record, the manifest last, and the content of no file of the application'
        files.keySet().toList() == ['.grails/.gitignore', '.grails/.gitattributes', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml']
        new String(files.get('.grails/project.yml'), 'UTF-8').contains('generationState: untracked\n')

        and:
        !state.manifest.tracked
        state.manifest.generationState == ProjectManifest.UNTRACKED
        state.manifest.applicationType == ApplicationType.REST_API
        state.manifest.name == 'shop'
        state.manifest.defaultPackage == 'com.acme'
        state.manifest.grailsVersion == VersionInfo.grailsVersion
        state.manifest.options == OPTIONS
        state.manifest.requestedFeatures*.name == ['cache', 'grails-quartz']
        state.manifest.excludedFeatures*.name == ['h2']
        state.lock.generatorName == ProjectLock.GENERATOR
        state.lock.generatorVersion == VersionInfo.version
        state.lock.featureNames == ['gorm-hibernate5', 'cache', 'grails-quartz']
        state.baseline.files.isEmpty()

        and: 'what reads the selection alone reads it too'
        ProjectState.readSelection(directory).lock.featureNames == state.lock.featureNames
    }

    @Unroll
    void "what does not make a record the reader takes is refused before anything is written: #what"() {
        when:
        AdoptedProjectRecord.files(ApplicationType.WEB, name, defaultPackage, OPTIONS, requested, excluded, [new LockedFeature('cache', LockedFeature.BUNDLED_PACK)])

        then:
        ProjectStateException e = thrown()
        e.message.contains(message)

        where:
        what                             | name   | defaultPackage | requested       | excluded  || message
        'a name that is a path'          | 'a/b'  | 'com.acme'     | []              | []        || 'name is not a name an application can be created with'
        'a package that is none'         | 'shop' | 'com.1acme'    | []              | []        || 'defaultPackage is not a package an application can be created with'
        'a feature asked for and absent' | 'shop' | 'com.acme'     | ['grails-quartz'] | []      || 'the feature grails-quartz was asked for and is not among the features of the lock'
        'a feature left out and there'   | 'shop' | 'com.acme'     | []              | ['cache'] || 'the feature cache was left out and is among the features of the lock'
    }

    void "no feature is added to an application with such a record, and it is not generated again"() {
        given:
        write(AdoptedProjectRecord.files(ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [new LockedFeature('cache', LockedFeature.BUNDLED_PACK)]))
        Files.writeString(directory.resolve('build.gradle'), '// the build of an application that was there\n')
        FeatureAddition addition = new FeatureAddition(beanContext.getBean(ProjectGenerator), home)

        when:
        addition.plan(directory, ['grails-quartz'], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        ProjectChangeException refused = thrown()
        refused.message.startsWith('The record of the application was written for an application that was there, and not by the generator ' +
                '(generationState in .grails/project.yml is untracked, as project migrate writes it).')
        refused.message.contains('no feature is added to such an application by a command, and it is not generated again. Nothing was changed.')

        when:
        addition.regenerate(directory, OperatingSystem.LINUX)

        then:
        ProjectChangeException again = thrown()
        again.message == refused.message

        and:
        Files.readString(directory.resolve('build.gradle')) == '// the build of an application that was there\n'
    }

    private void write(Map<String, byte[]> files) {
        files.each { String path, byte[] content ->
            Path file = directory.resolve(path)
            Files.createDirectories(file.parent)
            Files.write(file, content)
        }
    }
}
