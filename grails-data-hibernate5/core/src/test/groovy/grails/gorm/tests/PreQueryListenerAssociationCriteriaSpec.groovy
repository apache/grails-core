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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A listener of the query events can add criteria on an association to a query, also to one that GORM
 * runs without executing the query itself, such as a query by example.
 */
class PreQueryListenerAssociationCriteriaSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore
    @Shared ClubCriteriaListener listener = new ClubCriteriaListener()

    void setupSpec() {
        Map config = [
                'dataSource.url'        : 'jdbc:h2:mem:preQueryListenerAssociationCriteria;LOCK_TIMEOUT=10000',
                'dataSource.dialect'    : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create',
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), ListenerClub, ListenerTeam)
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher).addApplicationListener(listener)
        ListenerTeam.withTransaction {
            new ListenerTeam(name: 'Arsenal First Team', club: new ListenerClub(name: 'Arsenal').save(failOnError: true)).save(failOnError: true)
            new ListenerTeam(name: 'Chelsea First Team', club: new ListenerClub(name: 'Chelsea').save(failOnError: true)).save(failOnError: true)
            new ListenerTeam(name: 'Unattached First Team').save(failOnError: true, flush: true)
        }
    }

    void cleanup() {
        listener.active = false
    }

    void '#api applies criteria on an association that a listener adds to the query'() {
        when:
        listener.active = true
        final List<String> names = ListenerTeam.withTransaction { query.call() }

        then:
        names == ['Arsenal First Team']

        where:
        api                  | query
        'findAll by example' | { ListenerTeam.findAll(new ListenerTeam())*.name }
        'find by example'    | { [ListenerTeam.find(new ListenerTeam(), [sort: 'name', order: 'desc'])?.name] }
        'findAllWhere'       | { ListenerTeam.findAllWhere([league: 'Premier'])*.name }
        'findWhere'          | { [ListenerTeam.findWhere([league: 'Premier'], [sort: 'name', order: 'desc'])?.name] }
        'a where query'      | { ListenerTeam.where { league == 'Premier' }.list()*.name }
    }

    static class ClubCriteriaListener implements ApplicationListener<ApplicationEvent> {

        volatile boolean active

        @Override
        void onApplicationEvent(ApplicationEvent event) {
            if (active && event instanceof PreQueryEvent && ((PreQueryEvent) event).query.entity.javaClass == ListenerTeam) {
                new DetachedCriteria(ListenerTeam).build {
                    club {
                        eq('name', 'Arsenal')
                    }
                }.criteria.each { ((PreQueryEvent) event).query.add(it) }
            }
        }
    }
}

@Entity
class ListenerClub {
    String name
}

@Entity
class ListenerTeam {
    String name
    String league = 'Premier'
    ListenerClub club

    static constraints = {
        club nullable: true
    }
}
