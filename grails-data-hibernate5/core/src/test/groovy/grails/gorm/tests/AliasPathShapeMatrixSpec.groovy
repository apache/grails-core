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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Unroll

/**
 * Every spelling of the path that createAlias is given, used three ways: the alias restricts, is projected and
 * orders. A path may start with {@code this}, go through an embedded component to an association inside it, go
 * through another association first, or start at an alias that an earlier createAlias declared, and a path may be
 * given a second alias. Each cell must give the result the same query gives without any alias.
 */
class AliasPathShapeMatrixSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([ShCountry, ShClub, ShOwner, ShPet])
    }

    void setup() {
        ShCountry c1 = new ShCountry(name: 'C1').save(failOnError: true)
        ShCountry c2 = new ShCountry(name: 'C2').save(failOnError: true)
        ShClub k1 = new ShClub(name: 'K1').save(failOnError: true)
        ShClub k2 = new ShClub(name: 'K2').save(failOnError: true)
        ShOwner a = new ShOwner(name: 'A', club: k1).save(failOnError: true)
        ShOwner b = new ShOwner(name: 'B', club: k2).save(failOnError: true)
        new ShPet(name: 'Ann', owner: a, address: new ShAddress(street: 's1', country: c1)).save(failOnError: true)
        new ShPet(name: 'Bob', owner: b, address: new ShAddress(street: 's2', country: c2)).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
    }

    /** label, the createAlias calls in order, the alias property that identifies Ann, its value for Ann, and for Bob */
    private static final List<List> SHAPES = [
            ['plain', [['owner', 'o']], 'o.name', 'A', 'B'],
            ['this prefix', [['this.owner', 'o']], 'o.name', 'A', 'B'],
            ['nested, prefix joined for it', [['owner.club', 'c']], 'c.name', 'K1', 'K2'],
            ['this prefix and nested', [['this.owner.club', 'c']], 'c.name', 'K1', 'K2'],
            ['embedded then association', [['address.country', 'c']], 'c.name', 'C1', 'C2'],
            ['this prefix, embedded then association', [['this.address.country', 'c']], 'c.name', 'C1', 'C2'],
            ['dotted path from an earlier alias', [['owner', 'o'], ['o.club', 'c']], 'c.name', 'K1', 'K2'],
            ['second alias of one path', [['owner', 'o'], ['owner', 'o2']], 'o2.name', 'A', 'B'],
            ['this prefix, then a second alias', [['this.owner', 'o'], ['owner', 'o2']], 'o2.name', 'A', 'B'],
            ['embedded association, then a second alias', [['address.country', 'c'], ['address.country', 'c2']], 'c2.name', 'C1', 'C2']
    ]

    private static List<List> cells() {
        SHAPES.collectMany { List shape -> ['restrict', 'project', 'order'].collect { String use -> [shape, use] } }
    }

    @Unroll
    void "#label used to #use"() {
        given:
        List<List<String>> aliases = shape[1]
        String aliasProperty = shape[2]
        String ann = shape[3]

        when:
        Object result = ShPet.withCriteria {
            aliases.each { List<String> a -> createAlias(a[0], a[1]) }
            switch (use) {
                case 'restrict':
                    eq(aliasProperty, ann)
                    order('name')
                    break
                case 'project':
                    eq('name', 'Ann')
                    projections {
                        property(aliasProperty)
                    }
                    break
                default:
                    order(aliasProperty, 'desc')
            }
        }

        then:
        switch (use) {
            case 'restrict':
                assert result*.name == ['Ann']
                break
            case 'project':
                assert result == [ann]
                break
            default:
                assert result*.name == ['Bob', 'Ann']
        }

        where:
        [shape, use] << cells()
        label = shape[0]
    }
}

@Entity
class ShCountry {
    String name
}

@Entity
class ShClub {
    String name
}

@Entity
class ShOwner {
    String name
    ShClub club
}

class ShAddress {
    String street
    ShCountry country
}

@Entity
class ShPet {
    String name
    ShOwner owner
    ShAddress address
    static embedded = ['address']
}
