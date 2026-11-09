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

package hierarchycli

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

import grails.boot.GrailsApp
import grails.boot.GrailsAppBuilder
import grails.boot.config.GrailsEarlyPluginRegistrationPostProcessor
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.util.Holders
import hierarchycli.command.VisitorReportRunner

/**
 * Runs the command the way {@link Application#main} does, through the same builder: a Grails
 * application without a web server as the parent, the command as a plain Spring child, the arguments
 * doing the work and the exit code coming back through {@link SpringApplication#exit}.
 */
class ReportingCommandSpec extends Specification {

    PrintStream originalOut = System.out

    ByteArrayOutputStream printed = new ByteArrayOutputStream()

    ConfigurableApplicationContext command

    ConfigurableApplicationContext grails

    void setup() {
        System.setOut(new PrintStream(printed, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        if (command?.isActive()) {
            command.close()
        }
        if (grails?.isActive()) {
            grails.close()
        }
    }

    private void run(String... args) {
        command = Application.builder().run(args)
        grails = (ConfigurableApplicationContext) command.parent
    }

    void 'the command registers the visitors named on the command line and reports them'() {
        when:
        run('Ada', 'Grace')

        then: 'the Grails application is the parent, without a web server'
        grails != null
        !(grails instanceof WebServerApplicationContext)
        grails.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        Holders.grailsApplication.is(grails.getBean(GrailsApplication.APPLICATION_ID))

        and: 'the command is a plain Spring child of it, started by a GrailsApp but running no plugin lifecycle'
        !(command instanceof WebServerApplicationContext)
        !command.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        !command.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
        command.getBean(GrailsApplication.APPLICATION_ID).is(grails.getBean(GrailsApplication.APPLICATION_ID))

        and: 'the arguments were registered through the Grails service and GORM of the parent'
        grails.getBean(VisitorService).names() == ['Ada', 'Grace']
        command.getBean(VisitorReportRunner).report == '2 visitors: Ada, Grace'
        printed.toString().contains('2 visitors: Ada, Grace')

        when: 'the command exits'
        int exitCode = SpringApplication.exit(command)

        then:
        exitCode == 0
        !command.isActive()
    }

    void 'without names the command prints its usage and exits with a failure code'() {
        when:
        run()

        then:
        printed.toString().contains(VisitorReportRunner.USAGE)
        grails.getBean(VisitorService).count() == 0
        SpringApplication.exit(command) == 1
    }

    void 'the builder main runs is a Grails builder without a web server'() {
        given:
        GrailsAppBuilder builder = Application.builder()

        expect:
        builder.application() instanceof GrailsApp
        builder.application().webApplicationType == WebApplicationType.NONE
    }

    void 'both contexts of the hierarchy are started by a GrailsApp'() {
        given:
        run('Ada')

        expect: 'the parent and the child each carry what only a GrailsApp sets up: the Grails environment as an active profile and the Grails bean factory defaults'
        [grails, command].every { ConfigurableApplicationContext context -> startedByGrailsApp(context) }
    }

    private static boolean startedByGrailsApp(ConfigurableApplicationContext context) {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        context.environment.activeProfiles.contains(Environment.current.name) &&
                beanFactory.allowBeanDefinitionOverriding &&
                beanFactory.allowCircularReferences
    }

    void 'closing the Grails parent closes the command'() {
        given:
        run('Linus')

        when:
        grails.close()

        then:
        !command.isActive()
    }
}
