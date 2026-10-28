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
package org.apache.grails.data.neo4j.core.querytail

import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.neo4j.driver.Session
import org.neo4j.driver.exceptions.ClientException
import org.testcontainers.neo4j.Neo4jContainer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import static org.apache.grails.datamapping.core.querytail.QueryTailRedactor.WITHHELD

/**
 * Runs against a Neo4j 5 server the statements it reads differently from Neo4j 3.5, the server the
 * other specifications run on, as it decodes a Unicode escape before it parses, wherever it is written.
 * Each statement the capture records is one Neo4j 5 reads as the capture does, and each one it
 * withholds is one Neo4j 5 reads otherwise or rejects.
 */
@Requires({ dockerAvailable() })
class QueryTailNeo4j5Spec extends Specification {

    static final String REJECTED = 'rejected'

    /** A quote escaped in Arabic-Indic digits, which Neo4j 5 decodes as it does ASCII ones. */
    static final String ARABIC_INDIC_QUOTE = '\\' + 'u\u0660\u0660\u0662\u0667'

    @Shared
    @AutoCleanup('stop')
    Neo4jContainer neo4j = new Neo4jContainer('neo4j:5.26-community').withoutAuthentication()

    @TempDir
    File tempDir

    Neo4jDatastore datastore

    void setupSpec() {
        neo4j.start()
    }

    void cleanup() {
        datastore?.close()
    }

    @Unroll
    void "a statement with #statement is recorded only when Neo4j 5 reads it as the capture does"() {
        given:
        File file = new File(tempDir, 'capture.log')
        datastore = new Neo4jDatastore([(Settings.SETTING_NEO4J_URL)       : neo4j.boltUrl,
                                        (QueryTailSettings.SETTING_ENABLED): 'true',
                                        (QueryTailSettings.SETTING_FILE)   : file.path], new Class[0])

        expect:
        run(cypher) == result
        records(file).last().sql == recorded

        where:
        statement                                                         | cypher                                                                          || result                         | recorded
        'an escaped backslash before a u in a string'                     | "RETURN 'C:" + '\\\\' + "u0027' AS v"                                           || [v: 'C:' + '\\' + 'u0027']     | 'RETURN ? AS v'
        'an escape that decodes into a letter in a string'                | "RETURN 'caf" + '\\' + "u00e9' AS v"                                            || [v: 'caf\u00e9']               | 'RETURN ? AS v'
        'an escape that decodes into a letter in a comment'               | 'RETURN 1 /* caf' + '\\' + 'u00e9 */ AS v'                                      || [v: 1]                         | 'RETURN ? AS v'
        'an escaped backslash before a u in a line comment'               | 'RETURN 1 // C:' + '\\\\' + 'users\nAS v'                                       || [v: 1]                         | 'RETURN ? AS v'
        'an escape that decodes into a letter in a name'                  | 'RETURN 1 AS `caf' + '\\' + 'u00e9`'                                            || ['caf\u00e9': 1]               | 'RETURN ? AS `caf' + '\\' + 'u00e9`'
        'an escaped backslash before a u in a name'                       | 'RETURN 1 AS `C:' + '\\\\' + 'users`'                                           || [('C:' + '\\\\' + 'users'): 1] | 'RETURN ? AS `C:' + '\\\\' + 'users`'
        'a backslash before a u and no hexadecimal digits'                | 'RETURN 1 // C:' + '\\' + 'users\nAS v'                                         || REJECTED                       | 'RETURN ? AS v'
        'an escape that decodes into a quote in a string'                 | "RETURN 'a" + '\\' + "u0027 + 'x' + " + '\\' + "u0027b' AS v"                   || [v: 'axb']                     | WITHHELD
        'an escape that decodes into a quote Neo4j 3.5 keeps in a string' | "RETURN 'it" + '\\' + "u0027s' AS v"                                            || REJECTED                       | WITHHELD
        'an escape whose digits are of another script'                    | "RETURN 'a" + ARABIC_INDIC_QUOTE + " + 'x' + " + ARABIC_INDIC_QUOTE + "b' AS v" || [v: 'axb']                     | WITHHELD
        'an escape of two u'                                              | 'RETURN "a' + '\\' + 'uu0022 + "x" + ' + '\\' + 'uu0022b" AS v'                 || REJECTED                       | WITHHELD
        'an escape that decodes into a backslash in a string'             | "RETURN 'a" + '\\' + "u005c' + 1' AS v"                                         || [v: "a' + 1"]                  | WITHHELD
        'an escape that decodes into an asterisk in a block comment'      | 'RETURN 1 /* ' + '\\' + 'u002a/ + 1 /* */ AS v'                                 || [v: 2]                         | WITHHELD
        'an escape after an escaped backslash in a block comment'         | 'RETURN 1 /* ' + '\\\\\\' + 'u002a/ + 1 /* */ AS v'                             || [v: 2]                         | WITHHELD
        'an escape that decodes into a slash in a block comment'          | 'RETURN 1 /* *' + '\\' + 'u002f + 1 /* */ AS v'                                 || [v: 2]                         | WITHHELD
        'an escape that decodes into a line feed in a line comment'       | 'RETURN 1 // ' + '\\' + 'u000a + 1\nAS v'                                       || [v: 2]                         | WITHHELD
        'an escape that decodes into a carriage return in a line comment' | 'RETURN 1 // ' + '\\' + 'u000d + 1\nAS v'                                       || [v: 2]                         | WITHHELD
        'an escape that decodes into a backtick in a name'                | 'WITH 1 AS a RETURN `a' + '\\' + 'u0060 AS v'                                   || [v: 1]                         | WITHHELD
        'an escape outside a string that decodes into a number'           | 'RETURN ' + '\\' + 'u0031 AS v'                                                 || [v: 1]                         | WITHHELD
    }

    /**
     * Whether a Docker daemon is reachable: {@code DOCKER_HOST} names one, or Docker Desktop or Linux
     * exposes its socket. The Docker client is not asked, as it can fail on macOS when its API version
     * is not the daemon's.
     */
    static boolean dockerAvailable() {
        System.getenv('DOCKER_HOST') || [System.getProperty('user.home') + '/.docker/run/docker.sock', '/var/run/docker.sock']
                .any { new File(it).exists() }
    }

    private Object run(String cypher) {
        Session session = datastore.boltDriver.session()
        try {
            session.run(cypher).single().asMap()
        }
        catch (ClientException ignored) {
            REJECTED
        }
        finally {
            session.close()
        }
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }
}
