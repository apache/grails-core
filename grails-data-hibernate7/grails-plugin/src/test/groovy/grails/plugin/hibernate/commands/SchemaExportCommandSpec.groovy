/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.plugin.hibernate.commands

import grails.dev.commands.ExecutionContext
import grails.gorm.annotation.Entity
import grails.test.hibernate.HibernateSpec
import org.grails.build.parsing.DefaultCommandLine
import spock.lang.TempDir

import java.nio.file.Path

class SchemaExportCommandSpec extends HibernateSpec {

    @TempDir
    Path tempDir

    @Override
    List<Class> getDomainClasses() {
        [SchemaExportFoo]
    }

    void "test handle schema export"() {
        given:
        def command = new SchemaExportCommand()
        command.applicationContext = applicationContext
        
        def commandLine = new DefaultCommandLine()
        def executionContext = new ExecutionContext(commandLine)
        // ExecutionContext.targetDir is final and comes from BuildSettings.TARGET_DIR, 
        // but SchemaExportCommand also uses filename from args or default ddl.sql in targetDir.
        
        def outputFile = tempDir.resolve("my-schema.sql").toFile()
        commandLine.addRemainingArg(outputFile.absolutePath)

        when:
        boolean result = command.handle(executionContext)

        then:
        result
        outputFile.exists()
        def content = outputFile.text
        content.contains("create table schema_export_foo")
    }

    void "test handle schema export with export arg"() {
        given:
        def command = new SchemaExportCommand()
        command.applicationContext = applicationContext
        
        def commandLine = new DefaultCommandLine()
        commandLine.addRemainingArg("export")
        def outputFile = tempDir.resolve("ddl-export.sql").toFile()
        commandLine.addRemainingArg(outputFile.absolutePath)
        
        def executionContext = new ExecutionContext(commandLine)

        when:
        boolean result = command.handle(executionContext)

        then:
        result
        outputFile.exists()
    }

    void "test handle schema export with stdout arg"() {
        given:
        def command = new SchemaExportCommand()
        command.applicationContext = applicationContext
        
        def commandLine = new DefaultCommandLine()
        commandLine.addRemainingArg("stdout")
        def outputFile = tempDir.resolve("ddl-stdout.sql").toFile()
        commandLine.addRemainingArg(outputFile.absolutePath)
        
        def executionContext = new ExecutionContext(commandLine)

        when:
        boolean result = command.handle(executionContext)

        then:
        result
        outputFile.exists()
    }

    void "test handle schema export with datasource"() {
        given:
        def command = new SchemaExportCommand()
        command.applicationContext = applicationContext
        
        def commandLine = new DefaultCommandLine()
        commandLine.addUndeclaredOption("datasource", "default")
        def outputFile = tempDir.resolve("ddl-ds.sql").toFile()
        commandLine.addRemainingArg(outputFile.absolutePath)
        
        def executionContext = new ExecutionContext(commandLine)

        when:
        boolean result = command.handle(executionContext)

        then:
        result
        outputFile.exists()
    }

    void "test handle schema export with generate arg"() {
        given:
        def command = new SchemaExportCommand()
        command.applicationContext = applicationContext
        
        def commandLine = new DefaultCommandLine()
        commandLine.addRemainingArg("generate")
        def outputFile = tempDir.resolve("ddl-generate.sql").toFile()
        commandLine.addRemainingArg(outputFile.absolutePath)
        
        def executionContext = new ExecutionContext(commandLine)

        when:
        boolean result = command.handle(executionContext)

        then:
        result
        outputFile.exists()
    }
}

@Entity
class SchemaExportFoo {
    String name
}
