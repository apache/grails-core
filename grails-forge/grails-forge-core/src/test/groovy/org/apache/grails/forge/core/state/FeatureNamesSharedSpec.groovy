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
package org.apache.grails.forge.core.state

import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Specification

/**
 * The name of a feature the record of a project has is what the reader of the record that tools are
 * told what a project is by, {@code ProjectFacts} of {@code grails-gradle-model}, reads as one: the
 * two are held to the same names by a file they share, beside the records that reader is tested with.
 * Each line of the file is {@code accepted} or {@code refused} and a name as a JSON string; a line
 * that starts with {@code #} is a comment.
 */
@Requires({ FeatureNamesSharedSpec.SHARED.isFile() })
class FeatureNamesSharedSpec extends Specification {

    static final File SHARED = new File('../../grails-gradle/model/src/test/resources/project-record-sample/feature-names.txt')

    void "a name the shared file accepts is the name of a feature in the record, and one it refuses is not"() {
        given:
        List<List<Object>> lines = SHARED.readLines('UTF-8').findAll { String line -> !line.trim().isEmpty() && !line.startsWith('#') }.collect { String line ->
            int space = line.indexOf(' ')
            [line.substring(0, space), new JsonSlurper().parseText(line.substring(space + 1))]
        }

        expect: 'the file says something of both kinds'
        lines.every { List<Object> line -> line[0] in ['accepted', 'refused'] && line[1] instanceof String }
        lines.any { List<Object> line -> line[0] == 'accepted' }
        lines.any { List<Object> line -> line[0] == 'refused' }

        and: 'the record of the command line reads each as the file says'
        lines.findAll { List<Object> line -> EarlierRecord.FEATURE_NAME.matcher((String) line[1]).matches() != (line[0] == 'accepted') } == []
    }
}
