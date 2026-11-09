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
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/16842')
class JunctionAssociationCreateAliasSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(AliasTag, AliasItem, AliasOwner, AliasShelf)
    }

    void setup() {
        def a = new AliasTag(name: 'a').save()
        def b = new AliasTag(name: 'b').save()
        new AliasOwner(name: 'item tagged a', tag: b).addToItems(new AliasItem(label: 'itemA', tag: a).save()).save()
        new AliasOwner(name: 'item tagged b', tag: a).addToItems(new AliasItem(label: 'itemB', tag: b).save()).save(flush: true)
        session.clear()
    }

    void "a join-type alias in an association block outside a junction is relative to the association"() {
        expect:
        AliasOwner.withCriteria {
            items {
                createAlias('tag', 't', 1)
                eq('t.name', 'a')
            }
            projections { property('name') }
        } == ['item tagged a']
    }

    void "a join-type alias in an association block inside or is relative to the association"() {
        expect:
        AliasOwner.withCriteria {
            or {
                eq('name', 'nobody')
                items {
                    createAlias('tag', 't', 1)
                    eq('t.name', 'a')
                }
            }
            projections { property('name') }
        } == ['item tagged a']
    }

    void "a join-type alias in an association block inside and is relative to the association"() {
        expect:
        AliasOwner.withCriteria {
            and {
                items {
                    createAlias('tag', 't', 1)
                    eq('t.name', 'a')
                }
            }
            projections { property('name') }
        } == ['item tagged a']
    }

    void "a join-type alias in an association block inside or resolves against an owner without that property"() {
        given:
        new AliasShelf(name: 's').addToItems(new AliasItem(label: 'itemS', tag: AliasTag.findByName('a')).save()).save(flush: true)
        session.clear()

        expect:
        AliasShelf.withCriteria {
            or {
                eq('name', 'nobody')
                items {
                    createAlias('tag', 't', 1)
                    eq('t.name', 'a')
                }
            }
            projections { property('name') }
        } == ['s']
    }

    void "the two-argument alias inside or keeps working"() {
        expect:
        AliasOwner.withCriteria {
            or {
                eq('name', 'nobody')
                items {
                    createAlias('tag', 't')
                    eq('t.name', 'a')
                }
            }
            projections { property('name') }
        } == ['item tagged a']
    }
}

@Entity
class AliasTag {
    String name
}

@Entity
class AliasItem {
    String label
    AliasTag tag
}

@Entity
class AliasOwner {
    String name
    AliasTag tag

    static hasMany = [items: AliasItem]
}

@Entity
class AliasShelf {
    String name

    static hasMany = [items: AliasItem]
}
