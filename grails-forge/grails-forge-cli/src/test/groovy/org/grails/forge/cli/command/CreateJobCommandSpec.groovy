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

import io.micronaut.context.ApplicationContext
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.feature.other.GrailsQuartz
import org.grails.forge.io.ConsoleOutput
import spock.lang.AutoCleanup
import spock.lang.Shared

class CreateJobCommandSpec extends CommandSpec implements CommandFixture {

    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()


    void "test creating a job"() {

        setup:
        generateProject(ApplicationType.WEB)
        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        codeGenConfig.getFeatures().add(GrailsQuartz.FEATURE_NAME)
        ConsoleOutput consoleOutput = Mock(ConsoleOutput)
        CreateJobCommand command = new CreateJobCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)
        command.jobName = "Scheduled"

        when:
        Integer exitCode = command.call()
        File output = new File(dir, "grails-app/jobs/example/grails/ScheduledJob.groovy")

        then:
        exitCode == 0
        output.exists()
        1 * consoleOutput.out({ it.contains("Rendered job") })
    }

    void "test plugin not selected"() {

        setup:
        generateProject(ApplicationType.WEB)
        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        ConsoleOutput consoleOutput = Mock(ConsoleOutput)
        CreateJobCommand command = new CreateJobCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)
        command.jobName = "Scheduled"

        when:
        command.call()

        then:
        final e = thrown(IllegalArgumentException)
        e.message == 'Please first select Grails Quartz Plugin'
    }
}
