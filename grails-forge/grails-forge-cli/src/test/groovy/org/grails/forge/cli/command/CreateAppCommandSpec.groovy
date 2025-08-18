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

package org.grails.forge.cli.command

import io.micronaut.configuration.picocli.PicocliRunner
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.io.ConsoleOutput
import spock.lang.AutoCleanup
import spock.lang.Shared

class CreateAppCommandSpec extends CommandSpec implements CommandFixture {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(Environment.CLI)

    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()

    void "test creating project with defaults"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        PicocliRunner.run(CreateAppCommand, ctx, "foobar")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating project with logbackGroovy"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        PicocliRunner.run(CreateAppCommand, ctx, "-flogbackGroovy", "foobar")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating a project with an invalid gorm implementation"() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        when:
        PicocliRunner.run(CreateAppCommand, ctx, "temp",  "--gorm", "xyz")

        then:
        noExceptionThrown()
        baos.toString().contains("Invalid GORM implementation selection: xyz")
    }

    void "community and preview features are labelled as such"() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        PrintStream old = System.out
        System.setOut(new PrintStream(baos))

        and:
        def (previewFeature, communityFeature) = beanContext.getBean(WebAvailableFeatures).with {
            [allFeatures.find { it.preview }, allFeatures.find { it.community }]
        }

        when:
        PicocliRunner.run(CreateAppCommand, ctx, "temp", "--list-features")

        then:
        noExceptionThrown()
        baos.toString().contains("$previewFeature.name [PREVIEW]")
        communityFeature == null
//        baos.toString().contains("$communityFeature.name [COMMUNITY]")

        cleanup:
        System.setOut(old)
    }
}
