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

package grails.test.app

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.hibernate.SessionFactory
import spock.lang.Specification

@Integration
@Rollback
class ArtistIntegrationSpec extends Specification implements GraphQLSpec {

    SessionFactory sessionFactory

    void "test listing artists and paintings"() {
        given:
        def a = new Artist(name: "Picasso").save(flush: true, failOnError: true)
        sessionFactory.currentSession.flush()
        sessionFactory.currentSession.transaction.commit()

        when:
        def resp = graphQL.graphql("""
            {
              artistList {
                id
                name
                paintings {
                  name
                  heightCm
                  widthCm
                }
              }
            }
        """)
        def json = resp.body()
        println json.toString()
        def artists = json.data.artistList
        def artist = artists[0]

        then:
        artists.size() == 1
        artist.id == a.id
        artist.name == "Picasso"
        artist.paintings.size() == 1
        artist.paintings[0].name == "test"
        artist.paintings[0].heightCm == 60
        artist.paintings[0].widthCm == 120
    }

}
