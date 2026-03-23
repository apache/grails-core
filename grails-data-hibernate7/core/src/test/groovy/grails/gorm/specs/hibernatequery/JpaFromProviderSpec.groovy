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
package grails.gorm.specs.hibernatequery

import org.hibernate.query.criteria.JpaCriteriaQuery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Path
import org.grails.orm.hibernate.query.JpaFromProvider
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaFromProviderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([JpaFromProviderSpecPerson, JpaFromProviderSpecPet, JpaFromProviderSpecFace])
    }

    private JpaFromProvider bare(Class clazz, From root) {
        def dc = new DetachedCriteria(clazz)
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) {
            getJavaType() >> String
            alias(_) >> it
        }
        return new JpaFromProvider(dc, [], root)
    }

    def "getFromsByName returns root for 'root' key"() {
        given:
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }
        
        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)

        expect:
        provider.getFromsByName().get("root") == root
    }

    def "getFullyQualifiedPath returns root for entity name if it matches root"() {
        given:
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)

        expect:
        provider.getFullyQualifiedPath("JpaFromProviderSpecPerson") == root
    }

    def "getFullyQualifiedPath returns root for 'root' prefix"() {
        given:
        Path idPath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
            get("id") >> idPath
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)

        expect:
        provider.getFullyQualifiedPath("root.id") == idPath
    }

    def "getFullyQualifiedPath throws for null property name"() {
        given:
        From root = Mock(From)
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)

        when:
        provider.getFullyQualifiedPath(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "clone produces an independent copy that does not affect original"() {
        given:
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)
        From extra = Mock(From)

        when:
        JpaFromProvider clone = provider.clone()
        clone.put("extra", extra)

        then:
        clone.getFromsByName().containsKey("extra")
        !provider.getFromsByName().containsKey("extra")
    }

    def "put overwrites an existing key"() {
        given:
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        JpaFromProvider provider = bare(JpaFromProviderSpecPerson, root)
        From newRoot = Mock(From)

        when:
        provider.put("root", newRoot)

        then:
        provider.getFromsByName().get("root") == newRoot
    }

    def "root alias registered via setAlias is available for dotted lookup"() {
        given:
        Path idPath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
            get("id") >> idPath
        }
        root.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        def dc = new DetachedCriteria(JpaFromProviderSpecPerson)
        dc.setAlias("myAlias")
        JpaFromProvider provider = new JpaFromProvider(dc, [], root)

        when:
        Path result = provider.getFullyQualifiedPath("myAlias.id")

        then:
        result == idPath
    }

    def "getFromsByName creates hierarchical joins for projection paths"() {
        given:
        def dc = new DetachedCriteria(JpaFromProviderSpecPerson)
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        // Stub for auto-joined basic collections
        root.join("nicknames", _) >> Mock(Join) { alias(_) >> it }

        Join teamJoin = Mock(Join) {
            getJavaType() >> String
            alias(_) >> it
        }
        Join clubJoin = Mock(Join) {
            getJavaType() >> String
            alias(_) >> it
        }

        and: "projections with nested paths"
        def projections = [
                new org.grails.datastore.mapping.query.Query.PropertyProjection("team.club.name")
        ]

        when:
        JpaFromProvider provider = new JpaFromProvider(dc, projections, root)

        then: "joins are created hierarchically"
        1 * root.join("team", jakarta.persistence.criteria.JoinType.LEFT) >> teamJoin
        1 * teamJoin.join("club", jakarta.persistence.criteria.JoinType.LEFT) >> clubJoin
        0 * clubJoin.join(_, _)

        and: "paths are registered in provider"
        provider.getFullyQualifiedPath("team") == teamJoin
        provider.getFullyQualifiedPath("team.club") == clubJoin
    }

    def "constructor with parent provider inherits froms and supports correlation"() {
        given:
        From outerRoot = Mock(From) { getJavaType() >> JpaFromProviderSpecPerson }
        JpaFromProvider parent = bare(JpaFromProviderSpecPerson, outerRoot)

        and: "subquery detached criteria"
        def subDc = new DetachedCriteria(JpaFromProviderSpecPet)
        From subRoot = Mock(From) { getJavaType() >> JpaFromProviderSpecPet }
        subRoot.join(_ as String, _ as jakarta.persistence.criteria.JoinType) >> Mock(Join) { alias(_) >> it }

        when:
        JpaFromProvider subProvider = new JpaFromProvider(parent, subDc, [], subRoot)

        then: "subquery provider has its own root"
        subProvider.getFullyQualifiedPath("root") == subRoot

        and: "subquery provider inherits outer paths"
        subProvider.getFullyQualifiedPath("root") != outerRoot // subquery root shadows outer root
    }

    def "getFromsByName automatically joins basic collections"() {
        given:
        def dc = new DetachedCriteria(JpaFromProviderSpecPerson)
        From root = Mock(From) {
            getJavaType() >> JpaFromProviderSpecPerson
        }
        Join nicknamesJoin = Mock(Join) {
            getJavaType() >> String
            alias(_) >> it
        }

        when:
        JpaFromProvider provider = new JpaFromProvider(dc, [], root)

        then: "basic collection is joined automatically"
        1 * root.join("nicknames", jakarta.persistence.criteria.JoinType.LEFT) >> nicknamesJoin

        and: "path is registered"
        provider.getFullyQualifiedPath("nicknames") == nicknamesJoin
    }
}

@Entity
class JpaFromProviderSpecPerson implements GormEntity<JpaFromProviderSpecPerson> {
    Long id
    String firstName
    Set<String> nicknames
    static hasMany = [nicknames: String]
}

@Entity
class JpaFromProviderSpecPet implements GormEntity<JpaFromProviderSpecPet> {
    Long id
    JpaFromProviderSpecPerson owner
}

@Entity
class JpaFromProviderSpecFace implements GormEntity<JpaFromProviderSpecFace> {
    Long id
}
