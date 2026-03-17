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

import grails.gorm.DetachedCriteria
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import org.grails.orm.hibernate.query.JpaFromProvider
import spock.lang.Specification

class JpaFromProviderSpec extends Specification {

    /** Build a bare JpaFromProvider with no joins by using an empty DetachedCriteria
     *  and a mock JpaCriteriaQuery that can't be joined against (no fetch strategies). */
    private JpaFromProvider bare(Class target, From root) {
        def dc = new DetachedCriteria(target)
        def cq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery) {
            from(_) >> root
        }
        def provider = new JpaFromProvider(dc, cq, root)
        return provider
    }

    def "getFullyQualifiedPath resolves a single-segment property against root"() {
        given:
        Path namePath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> String  // stub for getFromsByName internal logic
            get("name") >> namePath
        }
        JpaFromProvider provider = bare(String, root)

        when:
        Path result = provider.getFullyQualifiedPath("name")

        then:
        result == namePath
    }

    def "getFullyQualifiedPath returns root From when key is 'root'"() {
        given:
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)

        when:
        Path result = provider.getFullyQualifiedPath("root")

        then:
        result == root
    }

    def "getFullyQualifiedPath resolves a named alias directly when key matches"() {
        given:
        From aliasFrom = Mock(From) {
            getJavaType() >> Integer
        }
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)
        provider.put("t", aliasFrom)

        when:
        Path result = provider.getFullyQualifiedPath("t")

        then:
        result == aliasFrom
    }

    def "getFullyQualifiedPath resolves a dotted path alias.property"() {
        given:
        Path clubPath = Mock(Path)
        From aliasFrom = Mock(From) {
            getJavaType() >> Integer
            get("club") >> clubPath
        }
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)
        provider.put("t", aliasFrom)

        when:
        Path result = provider.getFullyQualifiedPath("t.club")

        then:
        result == clubPath
    }

    def "getFullyQualifiedPath throws for blank property name"() {
        given:
        From root = Mock(From) { getJavaType() >> String }
        JpaFromProvider provider = bare(String, root)

        when:
        provider.getFullyQualifiedPath("   ")

        then:
        thrown(IllegalArgumentException)
    }

    def "getFullyQualifiedPath throws for null property name"() {
        given:
        From root = Mock(From) { getJavaType() >> String }
        JpaFromProvider provider = bare(String, root)

        when:
        provider.getFullyQualifiedPath(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "clone produces an independent copy that does not affect original"() {
        given:
        From root = Mock(From) { getJavaType() >> String }
        From extra = Mock(From) { getJavaType() >> Integer }
        JpaFromProvider original = bare(String, root)

        when:
        JpaFromProvider copy = (JpaFromProvider) original.clone()
        copy.put("extra", extra)

        then: "original is unaffected"
        original.getFullyQualifiedPath("root") == root
        copy.getFullyQualifiedPath("root") == root
        copy.getFullyQualifiedPath("extra") == extra
    }

    def "put overwrites an existing key"() {
        given:
        From first = Mock(From) { getJavaType() >> String }
        From second = Mock(From) { getJavaType() >> String }
        JpaFromProvider provider = bare(String, first)
        provider.put("root", second)

        when:
        def result = provider.getFullyQualifiedPath("root")

        then:
        result == second
    }

    def "root alias registered via setAlias is available for dotted lookup"() {
        given:
        Path idPath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> String
            get("id") >> idPath
        }
        def dc = new DetachedCriteria(String)
        dc.setAlias("myAlias")
        def cq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery) {
            from(_) >> root
        }
        JpaFromProvider provider = new JpaFromProvider(dc, cq, root)

        when:
        Path result = provider.getFullyQualifiedPath("myAlias.id")

        then:
        result == idPath
    }
}
