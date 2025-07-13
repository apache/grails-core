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

class CreateTagLibCommandSpec extends CommandSpec implements CommandFixture {

    ApplicationContext beanContext

    void setup() {
        beanContext = ApplicationContext.run()
    }

    void cleanup() {
        beanContext.close()
        beanContext = null
    }

    void "test creating a taglib"() {

        setup:
        generateProject(ApplicationType.WEB)
        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        ConsoleOutput consoleOutput = Mock(ConsoleOutput)
        CreateTagLibCommand command = new CreateTagLibCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)
        command.tagLibName = "simple"

        when:
        Integer exitCode = command.call()
        File output = new File(dir, "grails-app/taglib/example/grails/SimpleTagLib.groovy")
        File specOutput = new File(dir, "src/test/groovy/example/grails/SimpleTagLibSpec.groovy")

        then:
        exitCode == 0
        output.exists()
        specOutput.exists()
        2 * consoleOutput.out({ it.contains("Rendered taglib") })
    }

    void "test app with taglib"() {
        setup:
        generateProject(ApplicationType.WEB)
        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)
        ConsoleOutput consoleOutput = Mock(ConsoleOutput)
        CreateTagLibCommand command = new CreateTagLibCommand(codeGenConfig, getOutputHandler(consoleOutput), consoleOutput)

        when:
        command.tagLibName  = 'simple'
        Integer exitCode = command.call()
        executeGradleCommand("test")

        then:
        exitCode == 0
        testOutputContains("SimpleTagLibSpec > test simple tag as method FAILED")

        when:
        new File(dir, "grails-app/taglib/example/grails/SimpleTagLib.groovy").text = '''package example.grails

class SimpleTagLib {

    def simple = { attrs, body ->
        out << "Hello!"
    }
}
'''
        then:
        executeGradleCommand("test")
        testOutputContains("BUILD SUCCESSFUL")

    }
}
