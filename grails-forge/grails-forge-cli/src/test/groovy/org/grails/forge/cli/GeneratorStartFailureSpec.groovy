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
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The project generator of the command line runs as production, whatever environment the JVM it is
 * started in names, and puts the environment it found back when it is closed. This holds for a start
 * that fails as well: a program that runs the command line in its own JVM finds its environment as it
 * was, also after a command that could not start the generator.
 *
 * <p>The start fails here as it does where the JVM names a configuration of Spring that is not there,
 * which a program that runs the command line can have set for itself.</p>
 */
class GeneratorStartFailureSpec extends Specification {

    static final String ENVIRONMENT = 'grails.env'
    static final String CONFIGURATION = 'spring.config.location'

    File dir = Files.createTempDirectory('grailsforgestart').toFile().canonicalFile
    String previousUserDir
    String previousEnvironment
    String previousConfiguration
    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        previousUserDir = System.getProperty('user.dir')
        previousEnvironment = System.getProperty(ENVIRONMENT)
        previousConfiguration = System.getProperty(CONFIGURATION)
        System.setProperty('user.dir', dir.path)
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        // a configuration the start of the generator cannot read, as a program can name one for itself
        System.setProperty(CONFIGURATION, new File(dir, 'not-there.yml').toURI().toString())
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.dir', previousUserDir)
        restore(ENVIRONMENT, previousEnvironment)
        restore(CONFIGURATION, previousConfiguration)
        dir.deleteDir()
    }

    @Unroll
    void "a command that cannot start the generator leaves the environment of a JVM that names #named as it was"() {
        given:
        restore(ENVIRONMENT, named)

        when: 'as GrailsCli.execute runs it'
        int exit = ForgeCli.launch('grails', false, 'create-app', 'example.shop')

        then: 'the generator was not started, and the command said so'
        exit == 1
        err.toString().readLines().any { String line -> line.startsWith('| Error The project generator of the command line could not be started') }
        !new File(dir, 'shop').exists()

        and: 'the environment is the one the JVM had'
        System.getProperty(ENVIRONMENT) == named

        where:
        named << ['development', 'test', 'production', null]
    }

    void "in an interactive console the environment is put back as soon as the start has failed, and the next command fails as the first did"() {
        given: 'one generator for every command, as the console has'
        System.setProperty(ENVIRONMENT, 'development')
        ForgeContext context = new ForgeContext()

        when:
        int first = ForgeCli.execute(context, 'create-app', 'example.shop')

        then: 'before the console is left'
        first == 1
        System.getProperty(ENVIRONMENT) == 'development'

        when: 'the program changes it in the meantime'
        System.setProperty(ENVIRONMENT, 'test')
        int second = ForgeCli.execute(context, 'create-app', 'example.shop')
        context.close()

        then: 'it is not started again, and what the program set is left'
        second == 1
        err.toString().readLines().findAll { String line -> line.startsWith('| Error The project generator') }.size() == 2
        System.getProperty(ENVIRONMENT) == 'test'
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
