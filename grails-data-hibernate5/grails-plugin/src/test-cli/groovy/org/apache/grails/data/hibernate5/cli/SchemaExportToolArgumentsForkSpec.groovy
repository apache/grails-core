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
package org.apache.grails.data.hibernate5.cli

import java.util.concurrent.TimeUnit

import org.springframework.context.annotation.Bean
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.boot.config.GrailsAutoConfiguration
import grails.gorm.annotation.Entity

/**
 * The {@code schema-export} tool as the {@code runTool} task of a build runs it: by the class that runs a
 * tool, in a JVM of its own, with the application started around the tool. It is given the option and the
 * parameters it declares, and is held to them before the application is started for it.
 */
class SchemaExportToolArgumentsForkSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final int TIMEOUT_SECONDS = 180

    @TempDir
    File dir

    def "writes the DDL of the data source it is given to the file it is given"() {
        given:
        File ddl = new File(dir, 'schema/ddl.sql')

        when:
        Map result = run('schema-export', '--datasource=second', 'generate', ddl.path, SchemaExportForkApplication.name)

        then:
        result.exit == 0
        result.out.contains("Generating script to ${ddl.path} in environment 'development' for DataSource 'second'")
        ddl.text.toLowerCase().contains('create table schema_export_fork_book')
    }

    @Unroll
    def "schema-export #arguments is refused before the application is started for it, and the JVM ends with 2"() {
        when:
        Map result = run('schema-export', *arguments, SchemaExportForkFailingApplication.name)

        then:
        result.exit == 2
        result.err.readLines().contains("| Error schema-export: ${what}. grails help schema-export shows its options".toString())
        !result.err.contains('Context failed to load')

        where:
        arguments                                         || what
        ['--dataSource=lookup']                           || '--dataSource is not one of its options'
        ['--datasource']                                  || '--datasource needs a value'
        ['export', 'stdout', 'one.sql', 'two.sql']        || 'it takes at most 3 parameters, and was given 4'
    }

    /**
     * Runs the tool as the task of a build does: the class that runs a tool, the name of the tool and its
     * arguments, and the class of the application last.
     */
    private Map run(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(),
                                '-DdataSource.url=jdbc:h2:mem:forkDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                                '-DdataSource.dbCreate=none',
                                '-DdataSources.second.url=jdbc:h2:mem:secondForkDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                                '-DdataSources.second.dbCreate=none']
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        if (coverage) {
            command.add(coverage.replace('@fork@', UUID.randomUUID().toString()))
        }
        command.add(RUNNER)
        command.addAll(arguments.toList())
        File out = File.createTempFile('out', '.txt', dir)
        File err = File.createTempFile('err', '.txt', dir)
        Process process = new ProcessBuilder(command).directory(dir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.text, err: err.text]
    }
}

/**
 * The application the tool is run for, with the domain class of this package.
 */
class SchemaExportForkApplication extends GrailsAutoConfiguration {

    // resolves the placeholders of the beans of the plugins, as the auto-configuration of an application does
    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        new PropertySourcesPlaceholderConfigurer()
    }
}

/**
 * An application that fails when it is started, for the arguments that must not start it.
 */
class SchemaExportForkFailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}

@Entity
class SchemaExportForkBook {
    String title

    static mapping = {
        datasource 'ALL'
    }
}
