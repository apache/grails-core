/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class CollectionFetchPaginationSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    protected boolean isLeftJoinSort() { false }

    void setupSpec() {
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = leftJoinSort
        manager.domainClasses.addAll([FetchPageGroup, FetchPageMember, FetchPageAward])
    }

    void 'a deferred collection fetch counts distinct roots but pages joined sort rows'() {
        given:
        final first = new FetchPageGroup(name: 'First group')
        first.addToMembers(name: 'Member A').addToMembers(name: 'Member B')
        first.addToAwards(title: 'A').addToAwards(title: 'B').addToAwards(title: 'C')
        first.save(failOnError: true)
        final second = new FetchPageGroup(name: 'Second group')
        second.addToMembers(name: 'Member C').addToMembers(name: 'Member D')
        second.addToAwards(title: 'D').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final pages = (0..7).collect { int offset ->
            manager.session.clear()
            final results = FetchPageGroup.list(max: 1, offset: offset, sort: 'awards.title', fetch: [members: 'join'])
            [names: results*.name, total: results.totalCount, members: results.collect { it.members*.name.sort() }]
        }
        manager.session.clear()
        final unpaged = FetchPageGroup.list(sort: 'awards.title', fetch: [members: 'join'])
        manager.session.clear()
        final twoRows = FetchPageGroup.list(max: 2, sort: 'awards.title', fetch: [members: 'join'])

        then:
        unpaged*.name == ['First group', 'Second group']
        pages*.names == [['First group'], ['First group'], ['First group'], ['Second group'], [], [], [], []]
        pages.every { it.total == 2 }
        pages.every { it.total == unpaged.size() }
        pages.take(3).every { it.members == [['Member A', 'Member B']] }
        pages[3].members == [['Member C', 'Member D']]

        and: 'a two-row SQL page can contain only one distinct root'
        twoRows*.name == ['First group']
        twoRows.totalCount == unpaged.size()
        twoRows.first().members*.name.sort() == ['Member A', 'Member B']
    }
}

class CollectionFetchPaginationLeftJoinSpec extends CollectionFetchPaginationSpec {
    @Override
    protected boolean isLeftJoinSort() { true }
}

@Entity
class FetchPageGroup {
    String name
    Collection<FetchPageMember> members
    static hasMany = [members: FetchPageMember, awards: FetchPageAward]
}

@Entity
class FetchPageMember {
    String name
    static belongsTo = [group: FetchPageGroup]
}

@Entity
class FetchPageAward {
    String title
    static belongsTo = [group: FetchPageGroup]
}
