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
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

/**
 * The home the specifications of this module run with. What the command line keeps for its user, the
 * copies of the feature catalogs, the jars of feature packs and the journal of a change, is kept under
 * {@code .grails/forge} of the home of the user: a specification that runs a command as it is, and
 * names no home of its own, would read and write that of whoever runs the build. The build gives
 * every test task a home under its build directory, and the Gradle user home that there was.
 */
class TestHomeSpec extends Specification {

    void "the home of the specifications is a directory of the build, and not that of whoever runs them"() {
        given:
        Path home = Path.of(System.getProperty('user.home')).toRealPath()
        Path build = Path.of('build').toRealPath()

        expect: 'the home is a directory below the build directory of this module'
        Files.isDirectory(home)
        home.startsWith(build)
        home != build

        and: 'builds that the specifications run keep the Gradle user home there was, which is not below that home'
        System.getenv('GRADLE_USER_HOME')
        !Path.of(System.getenv('GRADLE_USER_HOME')).toAbsolutePath().normalize().startsWith(home)
    }

    void "a command that names no home keeps what it keeps under the home of the specifications"() {
        given: 'an application, in a directory of its own'
        File directory = Files.createTempDirectory('grailsforgetesthome').toFile().canonicalFile
        String previous = System.getProperty('user.dir')
        ConfigurableApplicationContext context = Application.builder().run()
        Path home = Path.of(System.getProperty('user.home'))
        Path build = Path.of('build').toRealPath()

        when: 'it is created and gets a feature, by the command line as it is'
        System.setProperty('user.dir', directory.absolutePath)
        int created = ForgeCli.execute(context, 'create-app', 'com.example.shop', '--inplace')
        int added = ForgeCli.execute(context, 'feature', 'add', 'grails-quartz')

        then: 'the journal of the change was kept under the home of the specifications'
        created == 0
        added == 0
        new File(directory, 'build.gradle').text.contains('quartz')
        home.toRealPath().startsWith(build)
        Files.isDirectory(home.resolve('.grails/forge/transactions'))

        cleanup:
        System.setProperty('user.dir', previous)
        context?.close()
        directory?.deleteDir()
    }
}
