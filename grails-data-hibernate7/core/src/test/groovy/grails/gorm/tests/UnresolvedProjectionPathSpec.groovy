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

import spock.lang.Issue

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Issue('https://github.com/apache/grails-core/issues/16861')
class UnresolvedProjectionPathSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(UnresolvedPathTeam, UnresolvedPathLike)
    }

    void setup() {
        def likeA = new UnresolvedPathLike(label: 'likeA').save(failOnError: true)
        def likeB = new UnresolvedPathLike(label: 'likeB').save(failOnError: true)
        new UnresolvedPathTeam(name: 'both', favorite: likeA).save(failOnError: true)
        new UnresolvedPathTeam(name: 'x', favorite: likeB).save(failOnError: true)
        new UnresolvedPathTeam(name: 'y').save(failOnError: true)
        new UnresolvedPathTeam(name: 'z').save(flush: true, failOnError: true)
    }

    void 'projection #kind on an unresolved path fails naming the path'() {
        when:
        UnresolvedPathTeam.withCriteria(closure)

        then:
        def e = thrown(Exception)
        e.message.contains(path)
        e.message.contains('UnresolvedPathTeam')

        where:
        kind              | path            | closure
        'property alias'  | 'zz.name'       | { projections { property('zz.name') } }
        'property name'   | 'nonexistent'   | { projections { property('nonexistent') } }
        'distinct'        | 'zz.name'       | { projections { distinct('zz.name') } }
        'groupProperty'   | 'zz.name'       | { projections { groupProperty('zz.name') } }
        'second column'   | 'zz.name'       | { projections { property('name'); property('zz.name') } }
        'with restriction'| 'zz.name'       | { eq('name', 'both'); projections { property('zz.name') } }
        'max'             | 'zz.name'       | { projections { max('zz.name') } }
        'sum'             | 'zz.id'         | { projections { sum('zz.id') } }
        'count'           | 'zz.name'       | { projections { count('zz.name') } }
        'countDistinct'   | 'zz.name'       | { projections { countDistinct('zz.name') } }
        'nested missing'  | 'favorite.nonexistent' | { projections { property('favorite.nonexistent') } }
    }

    void 'detached criteria projection on an unresolved path fails naming the path'() {
        when:
        new DetachedCriteria(UnresolvedPathTeam).property('zz.name').list()

        then:
        def e = thrown(Exception)
        e.message.contains('zz.name')
    }

    void 'resolvable projections still work'() {
        expect:
        UnresolvedPathTeam.withCriteria { projections { property('name') } }.sort() == ['both', 'x', 'y', 'z']
        UnresolvedPathTeam.withCriteria { projections { count('name') } } == [4]
    }
}

@Entity
class UnresolvedPathLike {
    String label
}

@Entity
class UnresolvedPathTeam {
    String name
    UnresolvedPathLike favorite

    static constraints = {
        favorite nullable: true
    }
}
