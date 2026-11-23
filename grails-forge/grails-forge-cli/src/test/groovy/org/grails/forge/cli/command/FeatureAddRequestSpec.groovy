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

import org.apache.grails.forge.core.state.ProjectState
import spock.lang.Unroll

/**
 * What one invocation of {@code feature add} asks for is looked at as a whole, before what the
 * application already has is set aside: two features of one exclusive group are a contradiction that
 * {@code --replace} does not resolve, and an invocation that was carried out changes nothing when it
 * is repeated.
 */
class FeatureAddRequestSpec extends AbstractFeatureAddCommandSpec {

    @Unroll
    void "#requested #options is refused on what create-app #created made, naming the features of one group, and nothing is changed"() {
        given:
        create(dir, CreateAppCommand, created)
        Map<String, String> before = tree(dir)
        List<String> requestedBefore = ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name

        when:
        int exit = add(([requested] + options) as String[])

        then: 'the request is a mistake in the command line, as it is for create-app'
        exit == 2
        err.toString().contains("The features ${named} of the group ${group}, and an application has one feature of such a group: name one of them. ")
        err.toString().contains('Nothing was changed')
        (options.contains('--replace')) == err.toString().contains('--replace puts a feature in the place of one the application has; it does not choose between features named together')
        !out.toString().contains('Feature')
        tree(dir) == before
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name == requestedBefore
        !new File(dir, '.grails/transactions').exists()

        where:
        created                    | requested                      | options                    || named                                  | group
        []                         | 'h2,postgres'                  | ['--replace']              || 'h2 and postgres are both'             | 'database-driver'
        []                         | 'h2,postgres'                  | []                         || 'h2 and postgres are both'             | 'database-driver'
        []                         | 'postgres,h2'                  | ['--replace']              || 'postgres and h2 are both'             | 'database-driver'
        []                         | 'h2,postgres'                  | ['--replace', '--dry-run'] || 'h2 and postgres are both'             | 'database-driver'
        []                         | 'h2,postgres'                  | ['--diff']                 || 'h2 and postgres are both'             | 'database-driver'
        ['--features', 'postgres'] | 'h2,postgres'                  | ['--replace']              || 'h2 and postgres are both'             | 'database-driver'
        ['--features', 'postgres'] | 'h2,postgres'                  | []                         || 'h2 and postgres are both'             | 'database-driver'
        ['--features', 'mysql']    | 'h2,postgres'                  | ['--replace']              || 'h2 and postgres are both'             | 'database-driver'
        ['--features', 'mysql']    | 'h2,postgres'                  | []                         || 'h2 and postgres are both'             | 'database-driver'
        []                         | 'grails-quartz,mysql,postgres' | ['--replace']              || 'mysql and postgres are both'          | 'database-driver'
        []                         | 'sitemesh3,grails-layout'      | ['--replace']              || 'sitemesh3 and grails-layout are both' | 'gsp-layout'
        []                         | 'h2,postgres,mysql'            | ['--replace']              || 'h2, postgres and mysql are all'       | 'database-driver'
    }

    void "the contradiction is refused whichever of the two the application has after an earlier invocation"() {
        given:
        create(dir, CreateAppCommand, [])

        when: 'the one, then the other'
        int first = add('h2,postgres', '--replace')
        String firstSaid = err.toString()
        reset()
        int postgres = add('postgres')
        reset()
        int second = add('h2,postgres', '--replace')

        then: 'the request is refused before and after the application has postgres, so it never alternates'
        first == 2
        firstSaid.contains('The features h2 and postgres are both of the group database-driver')
        postgres == 0
        second == 2
        err.toString().contains('The features h2 and postgres are both of the group database-driver')
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name == ['postgres']
    }

    @Unroll
    void "repeating #arguments, which was carried out, changes nothing on what create-app #created made"() {
        given:
        create(dir, CreateAppCommand, created)

        expect: 'the first invocation is carried out'
        add(arguments as String[]) == 0

        when:
        Map<String, String> after = tree(dir)
        List<String> requested = ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name
        reset()
        int again = add(arguments as String[])

        then:
        again == 0
        err.toString().isEmpty()
        out.toString().contains('Nothing was changed')
        tree(dir) == after
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name == requested

        when: 'and once more'
        reset()
        int third = add(arguments as String[])

        then:
        third == 0
        tree(dir) == after

        where:
        created                    | arguments
        []                         | ['postgres']
        []                         | ['postgres', '--replace']
        []                         | ['postgres,grails-quartz']
        []                         | ['postgres,postgres']
        []                         | ['h2', '--replace']
        ['--features', 'postgres'] | ['mysql', '--replace']
        ['--features', 'postgres'] | ['mysql,grails-quartz', '--replace']
        ['--features', 'h2']       | ['postgres', '--replace']
    }
}
