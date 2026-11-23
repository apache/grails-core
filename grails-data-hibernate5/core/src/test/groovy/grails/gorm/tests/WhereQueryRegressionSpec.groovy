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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import jakarta.persistence.criteria.JoinType
import spock.lang.Issue

class WhereQueryRegressionSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(WrPet, WrNick, WrSite, WrSeries, WrStory)
    }

    private long statements() {
        manager.sessionFactory.statistics.prepareStatementCount
    }

    @Issue('https://github.com/apache/grails-core/issues/14425')
    void "creating a detached where query that joins a collection runs no SQL"() {
        given:
        manager.sessionFactory.statistics.statisticsEnabled = true
        long before = statements()

        when:
        def query = WrPet.where {
            def tags = nickNames
            tags.nickname == 'Spot'
            order('tags.nickname')
        }

        then:
        statements() == before

        when:
        new WrPet(name: 'p').addToNickNames(new WrNick(nickname: 'Spot')).save(flush: true)
        session.clear()

        then: 'the query still finds the pet when it is finally run'
        query.list()*.name == ['p']
    }

    @Issue('https://github.com/apache/grails-core/issues/14422')
    void "a left join in a nested where subquery keeps rows matched only through the primary site"() {
        given:
        WrSite a = new WrSite(name: 'A').save()
        WrSite b = new WrSite(name: 'B').save()
        WrSeries s1 = new WrSeries(name: 's1', primarySite: a).save()
        WrSeries s2 = new WrSeries(name: 's2', primarySite: b)
        s2.addToAdditionalSites(a)
        s2.save()
        new WrStory(title: 't1', series: s1).save()
        new WrStory(title: 't2', series: s2).save(flush: true)
        session.clear()
        Long siteId = a.id
        Closure seriesCriteria = {
            WrSeries.where {
                join('additionalSites', JoinType.LEFT)
                or {
                    eq('primarySite.id', siteId)
                    additionalSites { eq('id', siteId) }
                }
            }
        }

        expect:
        seriesCriteria().id().list().size() == 2
        WrStory.where { series in seriesCriteria().id() }.id().list().size() == 2
    }
}

@Entity
class WrPet {
    String name
    static hasMany = [nickNames: WrNick]
}

@Entity
class WrNick {
    String nickname
    static belongsTo = [pet: WrPet]
}

@Entity
class WrSite {
    String name
}

@Entity
class WrSeries {
    String name
    WrSite primarySite
    static hasMany = [additionalSites: WrSite]
}

@Entity
class WrStory {
    String title
    WrSeries series
}
