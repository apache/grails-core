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

package functionaltests

import datasources.Application
import example.QsClub
import example.QsTeam
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * The same association query on a named connection returns the rows of that data source, not the ones of the default.
 */
@Integration(applicationClass = Application)
class QueryEntryPointDataSourceSpec extends Specification {

    def setup() {
        QsClub.withTransaction {
            QsClub.executeUpdate('delete from QsClub', [:])
            QsTeam.executeUpdate('delete from QsTeam', [:])
            QsTeam team = new QsTeam(name: 'alpha').save(flush: true, failOnError: true)
            new QsClub(name: 'default-club', team: team).save(flush: true, failOnError: true)
        }
        QsClub.secondary.withTransaction {
            QsClub.secondary.executeUpdate('delete from QsClub', [:])
            QsTeam.secondary.executeUpdate('delete from QsTeam', [:])
            QsTeam team = new QsTeam(name: 'alpha').secondary.save(flush: true, failOnError: true)
            new QsClub(name: 'secondary-club', team: team).secondary.save(flush: true, failOnError: true)
        }
    }

    def cleanup() {
        QsClub.withTransaction {
            QsClub.executeUpdate('delete from QsClub', [:])
            QsTeam.executeUpdate('delete from QsTeam', [:])
        }
        QsClub.secondary.withTransaction {
            QsClub.secondary.executeUpdate('delete from QsClub', [:])
            QsTeam.secondary.executeUpdate('delete from QsTeam', [:])
        }
    }

    void "an association criteria query returns the rows of the data source it runs on"() {
        expect:
        QsClub.withTransaction { QsClub.withCriteria { team { eq 'name', 'alpha' } }*.name } == ['default-club']
        QsClub.secondary.withTransaction { QsClub.secondary.withCriteria { team { eq 'name', 'alpha' } }*.name } == ['secondary-club']
    }

    void "an association where query returns the rows of the data source it runs on"() {
        expect:
        QsClub.withTransaction { QsClub.where { team.name == 'alpha' }.list()*.name } == ['default-club']
        QsClub.secondary.withTransaction { QsClub.secondary.where { team.name == 'alpha' }.list()*.name } == ['secondary-club']
    }
}
