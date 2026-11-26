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

import jakarta.persistence.criteria.JoinType
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.hibernate.Hibernate

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class NestedRequestedLeftJoinSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    protected boolean isLeftJoinSort() {
        false
    }

    void setupSpec() {
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = leftJoinSort
        manager.domainClasses.addAll([NestedLeftSortRoot, NestedLeftSortChild, NestedLeftSortLink])
    }

    void setup() {
        final detail = new NestedLeftSortLink(name: 'Detail').save(failOnError: true)
        final parent = new NestedLeftSortLink(name: 'Parent', next: detail).save(failOnError: true)
        final partialParent = new NestedLeftSortLink(name: 'Partial parent').save(failOnError: true)
        final populated = new NestedLeftSortRoot(name: 'Populated', parent: parent).save(failOnError: true)
        final partial = new NestedLeftSortRoot(name: 'Partial', parent: partialParent).save(failOnError: true)
        new NestedLeftSortRoot(name: 'Empty').save(failOnError: true)
        new NestedLeftSortChild(name: 'A', root: populated, link: parent).save(failOnError: true)
        new NestedLeftSortChild(name: 'B', root: populated, link: detail).save(failOnError: true)
        new NestedLeftSortChild(name: 'C', root: populated).save(failOnError: true)
        new NestedLeftSortChild(name: 'D', root: partial).save(failOnError: true, flush: true)
        manager.session.clear()
    }

    void '#api keeps missing associations when #sortForm sorts the requested LEFT path #path with paging #paged'() {
        when:
        final results = query(api, path, sortForm, paged ? [max: 20] : [:])

        then:
        results*.name.unique().sort() == ['Empty', 'Partial', 'Populated']
        path != 'children.link' || results.find { it.name == 'Populated' }.children*.name.sort() == ['A', 'B', 'C']

        where:
        [api, path, sortForm, paged] << [['where', 'detached', 'finder'], ['children.link', 'parent.next'], ['string', 'map'], [false, true]].combinations()
    }

    void 'a stored #api sort keeps missing associations on the requested LEFT path #path'() {
        expect:
        query(api, path, 'stored', [:])*.name.unique().sort() == ['Empty', 'Partial', 'Populated']

        where:
        [api, path] << [['where', 'detached'], ['children.link', 'parent.next']].combinations()
    }

    void 'a paged #api nested LEFT sort keeps the complete collection at offset #offset'() {
        when:
        final results = query(api, 'children.link', 'string', [max: 1, offset: offset], 'Populated')

        then:
        results*.name == ['Populated']
        !Hibernate.isInitialized(results.first().children)
        results.first().children*.name.sort() == ['A', 'B', 'C']

        where:
        [api, offset] << [['where', 'detached', 'finder'], [0, 1]].combinations()
    }

    void 'a nested LEFT request retains an explicit intermediate INNER join for #path'() {
        given:
        final criteria = new DetachedCriteria(NestedLeftSortRoot).join(path, JoinType.INNER).join(path + suffix, JoinType.LEFT)

        expect:
        criteria.list(sort: path + suffix + '.name')*.name.unique().sort() == ['Partial', 'Populated']

        where:
        path       | suffix
        'children' | '.link'
        'parent'   | '.next'
    }

    void 'a nested LEFT request does not replace a genuine intermediate association restriction'() {
        given:
        final criteria = new DetachedCriteria(NestedLeftSortRoot).build {
            or {
                eq('name', 'Empty')
                children { eq('name', 'A') }
            }
        }

        expect:
        criteria.list(fetch: ['children.link': JoinType.LEFT], sort: 'children.link.name')*.name == ['Populated']
    }

    void 'a nested LEFT request respects replacement fetch modes and path boundaries for #request'() {
        given:
        final criteria = new DetachedCriteria(NestedLeftSortRoot)
        if (request == 'similar prefix') {
            criteria.join('childrenExtra.link', JoinType.LEFT)
        }
        else if (request == 'selected intermediate') {
            criteria.join('children', JoinType.INNER).select('children').join('children.link', JoinType.LEFT)
        }
        else {
            criteria.join('children.link', JoinType.LEFT)
            if (request == 'join replacement') {
                criteria.join('children.link')
            }
            else {
                criteria.select('children.link')
            }
        }

        when:
        final results = criteria.list(sort: 'children.link.name')

        then:
        !results.empty
        !('Empty' in results*.name)

        where:
        request << ['similar prefix', 'selected intermediate', 'join replacement', 'select replacement']
    }

    private static List query(String api, String path, String sortForm, Map paging, String pattern = '%') {
        final sortProperty = path + '.name'
        final arguments = [fetch: [(path): JoinType.LEFT]] + paging
        if (sortForm != 'stored') {
            arguments.sort = sortForm == 'map' ? [(sortProperty): 'asc', name: 'asc'] : sortProperty
        }
        if (api == 'finder') {
            return NestedLeftSortRoot.findAllByNameLike(pattern, arguments)
        }
        final criteria = api == 'where' ? NestedLeftSortRoot.where { name ==~ pattern } : new DetachedCriteria(NestedLeftSortRoot).like('name', pattern)
        (sortForm == 'stored' ? criteria.sort(sortProperty) : criteria).list(arguments)
    }
}

class NestedRequestedLeftJoinEnabledSpec extends NestedRequestedLeftJoinSpec {
    @Override
    protected boolean isLeftJoinSort() {
        true
    }
}

@Entity
class NestedLeftSortRoot {
    String name
    NestedLeftSortLink parent
    static hasMany = [children: NestedLeftSortChild, childrenExtra: NestedLeftSortChild]
    static mappedBy = [children: 'root', childrenExtra: 'extraRoot']
    static constraints = {
        parent nullable: true
    }
}

@Entity
class NestedLeftSortChild {
    String name
    NestedLeftSortRoot root
    NestedLeftSortRoot extraRoot
    NestedLeftSortLink link
    static constraints = {
        root nullable: true
        extraRoot nullable: true
        link nullable: true
    }
}

@Entity
class NestedLeftSortLink {
    String name
    NestedLeftSortLink next
    static constraints = {
        next nullable: true
    }
}
