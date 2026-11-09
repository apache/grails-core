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
package grails.plugin.hibernate.commands

import java.util.concurrent.TimeUnit

import grails.gorm.annotation.Entity
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.data.hibernate7.cli.SchemaExportCommand
import org.grails.build.parsing.CommandLine
import org.grails.orm.hibernate.HibernateDatastore
import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification
import spock.lang.TempDir

class SchemaExportCommandSpec extends Specification {

    @TempDir
    File tempDir

    void "schema export failure is visible with #loggingDescription"() {
        given:
        String output = runFixture(apiOnly)

        expect:
        output.contains('result=false')
        if (apiOnly) {
            assert output.contains('SchemaManagementException')
            assert !output.contains('Unable to export database schema')
        }
        else {
            assert output.count('Unable to export database schema') == 1
            assert output.contains('ERROR')
            assert output.contains('SchemaManagementException')
        }
        output.readLines().last() == 'verified'

        where:
        apiOnly | loggingDescription
        true    | 'an API-only SLF4J classpath'
        false   | 'an ERROR-capable SLF4J provider'
    }

    private String runFixture(boolean apiOnly) {
        File argumentsFile = new File(tempDir, "schema-export-${apiOnly}.args")
        File outputFile = new File(tempDir, "schema-export-${apiOnly}.output")
        argumentsFile.text = """\
            -cp
            ${testRuntimeClasspath(apiOnly)}
            ${SchemaExportCommandSpec.name}
            ${apiOnly}
            """.stripIndent().trim()
        Process process = new ProcessBuilder(
            new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            "@${argumentsFile.absolutePath}"
        ).redirectErrorStream(true).redirectOutput(outputFile).start()
        process.outputStream.close()
        try {
            awaitProcess(process, 'SchemaExportCommand logging fixture')
            String output = outputFile.getText('UTF-8').trim()
            assert process.exitValue() == 0 : output
            output
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }

    private static void awaitProcess(Process process, String fixtureName) {
        if (process.waitFor(60, TimeUnit.SECONDS)) {
            return
        }
        process.destroyForcibly()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            throw new AssertionError("${fixtureName} did not terminate after timing out")
        }
        throw new AssertionError("${fixtureName} timed out after 60 seconds")
    }

    private static String testRuntimeClasspath(boolean apiOnly) {
        String[] entries = System.getProperty('java.class.path').split(File.pathSeparator)
        if (!apiOnly) {
            return entries.join(File.pathSeparator)
        }
        entries.findAll { !it.contains('slf4j-simple') && !it.contains('logback-classic') }.join(File.pathSeparator)
    }

    static void main(String[] args) {
        boolean apiOnly = Boolean.parseBoolean(args[0])
        def hibernateDatastore = new HibernateDatastore(Hibernate7SchemaExportLoggingEntity)
        def applicationContext = [
            getBean: { String name, Class type ->
                assert name == 'hibernateDatastore'
                assert type == HibernateDatastore
                hibernateDatastore
            }
        ] as ConfigurableApplicationContext
        def command = new SchemaExportCommand(applicationContext: applicationContext)
        def targetDirectory = File.createTempDir('hibernate7-schema-export', '')
        def commandLine = [
            getRemainingArgs: { [targetDirectory.absolutePath] },
            getUndeclaredOptions: { [:] }
        ] as CommandLine
        try {
            assert LoggerFactory.getLogger(SchemaExportCommand).errorEnabled == !apiOnly
            boolean result = command.handle(new ExecutionContext(commandLine))
            println "result=${result}"
            assert !result
        } finally {
            hibernateDatastore?.close()
            targetDirectory?.deleteDir()
        }
        println 'verified'
    }
}

@Entity
class Hibernate7SchemaExportLoggingEntity {
    Long id
    String name
}
