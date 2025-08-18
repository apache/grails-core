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
import org.grails.forge.io.ConsoleOutput
import spock.lang.AutoCleanup
import spock.lang.Shared

class CreateControllerCommandSpec extends CommandSpec implements CommandFixture {

    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()


    void 'test creating a controller'() {

        setup:
        generateProject(ApplicationType.WEB)
        def codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        def consoleOutput = Mock(ConsoleOutput)
        def command = new CreateControllerCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)
        command.controllerName = 'Greeting'

        when:
        def exitCode = command.call()
        def output = new File(dir, 'grails-app/controllers/example/grails/GreetingController.groovy')
        def specOutput = new File(dir, 'src/test/groovy/example/grails/GreetingControllerSpec.groovy')

        then:
        exitCode == 0
        output.exists()
        specOutput.exists()
        2 * consoleOutput.out({ it.contains('Rendered controller') })
    }

    void 'test app with controller'() {
        setup:
        generateProject(ApplicationType.WEB)
        def codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        def consoleOutput = Mock(ConsoleOutput)
        def command = new CreateControllerCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)

        when:
        command.controllerName  = 'Greeting'
        def exitCode = command.call()
        executeGradleCommand('build')

        then:
        exitCode == 0
        testOutputContains('BUILD SUCCESSFUL')
    }
}
