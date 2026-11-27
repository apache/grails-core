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
package org.apache.grails.data.hibernate7.dbmigration.cli

import java.util.concurrent.TimeUnit

import groovy.sql.Sql

import org.springframework.context.annotation.Bean

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.boot.config.GrailsAutoConfiguration

/**
 * The tools of the Database Migration plugin as the {@code runTool} task of a build runs them: by the class
 * that runs a tool, in a JVM of its own, with the application started around the tool. A tool is given the
 * options and parameters it declares, and is held to them before the application is started for it.
 */
class DatabaseMigrationToolArgumentsForkSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final int TIMEOUT_SECONDS = 180

    @TempDir
    File dir

    def "a switch it declares takes no value, so the file after --force is the change log it replaces"() {
        given: 'a change log that is there already'
        File changeLogs = new File(dir, 'migrations')
        changeLogs.mkdirs()
        File there = new File(changeLogs, 'there.groovy')
        there.text = 'written by hand'

        when:
        Map result = run(["-Dgrails.plugin.databasemigration.changelogLocation=${changeLogs.path}".toString()],
                'dbm-create-changelog', '--force', 'there.groovy', DatabaseMigrationForkApplication.name)

        then:
        result.exit == 0
        there.text.replaceAll(/\s/, '') == 'databaseChangeLog={}'
    }

    def "dbm-generate-changelog leaves out the objects the configuration excludes"() {
        given: 'a database with a table to keep and one to leave out'
        String url = "jdbc:h2:file:${new File(dir, 'generated').path};DB_CLOSE_ON_EXIT=FALSE".toString()
        Sql.withInstance(url, 'sa', '', 'org.h2.Driver') { Sql sql ->
            sql.execute('CREATE TABLE kept (id INT NOT NULL PRIMARY KEY)')
            sql.execute('CREATE TABLE secret (id INT NOT NULL PRIMARY KEY)')
        }

        when:
        Map result = run(["-DdataSource.url=${url}".toString(), '-DdataSource.username=sa', '-DdataSource.password=',
                          '-DdataSource.driverClassName=org.h2.Driver', '-Dgrails.plugin.databasemigration.excludeObjects=SECRET'],
                'dbm-generate-changelog', DatabaseMigrationForkApplication.name)
        String changeLog = result.out.replaceAll(/\s/, '')

        then:
        result.exit == 0
        changeLog.contains('createTable(tableName:"KEPT")')
        !changeLog.contains('tableName:"SECRET"')
    }

    @Unroll
    def "dbm-changelog-to-groovy #arguments writes #written"() {
        given:
        File changeLogs = new File(dir, 'migrations')
        changeLogs.mkdirs()
        new File(changeLogs, 'changelog.yml').text = '''\
databaseChangeLog:
- changeSet:
    id: 1
    author: John Smith
    changes:
    - createTable:
        tableName: book
        columns:
        - column:
            name: id
            type: INT
'''

        when:
        Map result = run(["-Dgrails.plugin.databasemigration.changelogLocation=${changeLogs.path}".toString(),
                          "-Dgrails.plugin.databasemigration.reports.changelogLocation=${changeLogs.path}".toString()],
                'dbm-changelog-to-groovy', *arguments, DatabaseMigrationForkApplication.name)

        then:
        result.exit == 0
        changeLogs.list().toList().sort() == ['changelog.yml', written].sort()

        where:
        arguments                                              || written
        ['changelog.yml', 'converted']                         || 'converted.groovy'
        ['--dataSource=reports', 'changelog.yml', 'converted'] || 'converted-reports.groovy'
    }

    @Unroll
    def "#tool #arguments is refused before the application is started for it, and the JVM ends with 2"() {
        when:
        Map result = run([], tool, *arguments, DatabaseMigrationForkFailingApplication.name)

        then:
        result.exit == 2
        result.err.readLines().contains("| Error ${tool}: ${what}. grails help ${tool} shows its options".toString())
        !result.err.contains('Context failed to load')

        where:
        tool                   | arguments                    || what
        'dbm-create-changelog' | ['--forse', 'there.groovy']  || '--forse is not one of its options'
        'dbm-update'           | ['--datasource=reports']     || '--datasource is not one of its options'
        'dbm-status'           | ['--verbose']                || '--verbose needs a value'
        'dbm-tag'              | ['--dataSource=reports']     || 'TAG is required'
        'dbm-update'           | ['--contexts=test', 'extra'] || 'it takes no parameters, and was given 1'
    }

    /**
     * Runs a tool as the task of a build does: the class that runs a tool, the name of the tool and its
     * arguments, and the class of the application last.
     */
    private Map run(List<String> jvmArguments, String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(),
                                '-DdataSource.url=jdbc:h2:mem:forkDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                                '-DdataSource.dbCreate=none',
                                // the web testing support brings the web plugins, which need what only a web application has
                                '-Dgrails.plugin.excludes=groovyPages,urlMappings,controllers,interceptors,restResponder,converters,mimeTypes']
        command.addAll(jvmArguments)
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
 * The application a tool is run for: the plugins on the class path, and no classes of its own.
 */
class DatabaseMigrationForkApplication extends GrailsAutoConfiguration {

    @Override
    Collection<String> packageNames() {
        []
    }
}

/**
 * An application that fails when it is started, for the tools that must not start it.
 */
class DatabaseMigrationForkFailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}
