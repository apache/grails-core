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

import org.apache.grails.gradle.model.cli.ProjectFacts
import spock.lang.Unroll

/**
 * The records of a project under {@code project-record-sample} of the tests of {@code grails-gradle-model}
 * are what this command line writes for a web application and a REST API, as {@code create-app} and
 * {@code create-restapi} generate them: the reader of the record that tools are told what a project is
 * by, {@code ProjectFacts}, is tested with them, and this specification keeps them what the command line
 * writes. The digests and the versions of Grails, which change with every change of the generator and
 * every release, are compared as {@code <digest>} and {@code <version>}, and the repositories are left out,
 * since the specifications generate with a repository of the framework of their own; everything else is
 * compared as it is written.
 *
 * <p>After a change of the record that is meant, write the samples again by running this spec with the
 * environment variable {@code FORGE_UPDATE_GOLDEN} set to {@code true}, and read the difference before it
 * is committed, with the tests of {@code grails-gradle-model}.</p>
 */
class ProjectRecordSampleSpec extends CommandSpec {

    static final String SAMPLES = '../../grails-gradle/model/src/test/resources/project-record-sample'

    PrintStream originalOut
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        System.setOut(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
    }

    @Unroll
    void "the record of a #type the command line writes is the sample the reader of the record is tested with"() {
        given:
        assert ForgeCli.execute(beanContext, create, 'com.example.shop') == 0
        File written = new File(dir, 'shop/' + ProjectFacts.RECORD)
        File sample = new File(previousUsrDir, "${SAMPLES}/${type}/${ProjectFacts.RECORD}").canonicalFile
        if (System.getenv('FORGE_UPDATE_GOLDEN') == 'true' && !System.getenv('CI')) {
            sample.parentFile.mkdirs()
            sample.bytes = written.bytes
        }

        expect:
        sample.isFile()
        comparable(written.getText('UTF-8')).readLines() == comparable(sample.getText('UTF-8')).readLines()

        and: 'what the reader of the record reads of it'
        ProjectFacts facts = ProjectFacts.read(new File(dir, 'shop'))
        facts.recorded
        facts.applicationType == type
        facts.features == ProjectFacts.read(sample.parentFile.parentFile).features

        where:
        type       | create
        'web'      | 'create-app'
        'rest-api' | 'create-restapi'
    }

    // the record without what changes with every change of the generator and every release, and without the repositories
    private static String comparable(String record) {
        return record.replace('\r\n', '\n')
                .replaceAll(/(?m)^repositories:\n(?:[- ] .*\n)*/, '')
                .replaceAll(/(?m)^(\s*sha256: )[0-9a-f]{64}$/, '$1<digest>')
                .replaceAll(/(?m)^(\s*version: |grailsVersion: ).+$/, '$1<version>')
    }
}
