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
package grails.gorm.tests.generated

import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A natural id the mapping states with {@code id natural: ...} on an entity the generated mode cannot hand to Hibernate's
 * {@code @NaturalId} as it is: a subclass (the domain binder adds the unique key to the table of the hierarchy), an embedded
 * property (the key spans the columns of the embedded type) and a name that is no property (the domain binder skips it). The
 * generated mode boots with the unique keys the domain binder creates (name and column order), flags the same properties as natural,
 * and rejects the same duplicates.
 */
class GeneratedDomainClassesNaturalIdSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:ni${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /**
     * The unique keys of every table (columns in order, and names when asked for: Hibernate names the key of a root's {@code @NaturalId}
     * itself) and the updatability of the properties, with the properties of a root flagged natural.
     */
    private Map<String, Object> schema(HibernateDatastore booted, boolean names = false, boolean natural = true) {
        Map<String, Object> result = new TreeMap<String, Object>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result["table ${table.name}".toString()] = [
                        columns   : table.columns*.name.sort(),
                        uniqueKeys: table.uniqueKeys.values().collect { names ? [name: it.name, columns: it.columns*.name] : [columns: it.columns*.name] }.sort { it.toString() },
                ]
            }
        }
        for (PersistentClass persistentClass : booted.metadata.entityBindings) {
            result["updatable ${persistentClass.entityName}".toString()] = persistentClass.declaredProperties.collectEntries { Property p -> [(p.name): p.updateable] }
            if (natural && persistentClass instanceof RootClass) {
                result["natural ${persistentClass.entityName}".toString()] = persistentClass.declaredProperties.findAll { Property p -> p.naturalIdentifier }*.name
            }
        }
        return result
    }

    @Unroll
    void "#label has the unique key and the natural properties the domain binder gives it"() {
        given: "the domain binder also marks a property of a parent as natural when its subclass names it, which the generated mode does not"
        boolean natural = group != [NiMixRoot, NiMixChild]

        when:
        Map<String, Object> binder = schema(boot(group, false), false, natural)
        Map<String, Object> generated = schema(boot(group, true), false, natural)

        then:
        generated == binder
        generated["table ${table}".toString()].uniqueKeys*.columns == keys

        where:
        label                                              | group                                  | table             | keys
        'a subclass of a single-table hierarchy'           | [NiRoot, NiChild]                      | 'ni_root'         | [['code']]
        'a subclass naming a property of its parent'       | [NiMixRoot, NiMixChild]                | 'ni_mix_root'     | [['code', 'name']]
        'a mutable natural id on a subclass'               | [NiMutRoot, NiMutChild]                | 'ni_mut_root'     | [['code']]
        'a root and a subclass that both state one'        | [NiBothRoot, NiBothChild]              | 'ni_both_root'    | [['code'], ['name']]
        'a subclass three levels down'                     | [NiGrand, NiGrandChild, NiGrandGrand]  | 'ni_grand'        | [['extra', 'code']]
        'a subclass naming a foreign key'                  | [NiFkTarget, NiFkRoot, NiFkChild]      | 'ni_fk_root'      | [['target_id', 'code']]
        'a subclass of a joined hierarchy'                 | [NiJRoot, NiJChild]                    | 'nijchild'       | [['region', 'code']]
        'an embedded property after a simple one'          | [NiEmb]                                | 'ni_emb'          | [['code', 'home_street', 'home_city']]
        'an embedded property before a simple one'         | [NiEmb2]                               | 'ni_emb2'         | [['home_street', 'home_city', 'code']]
        'an embedded property of a subclass'               | [NiEmbRoot, NiEmbChild]                | 'ni_emb_root'     | [['home_street', 'home_city', 'code']]
        'an embedded property alone'                       | [NiEmbOnly]                            | 'ni_emb_only'     | [['home_street', 'home_city']]
        'a name that is no property after a real one'      | [NiTypo]                               | 'ni_typo'         | [['code']]
        'a name that is no property and nothing else'      | [NiOnlyTypo]                           | 'ni_only_typo'    | []
    }

    @Unroll
    void "the unique key of #label has the name the domain binder gives it"() {
        when:
        Map<String, Object> binder = schema(boot(group, false), true, false)
        Map<String, Object> generated = schema(boot(group, true), true, false)

        then:
        generated["table ${table}".toString()].uniqueKeys == binder["table ${table}".toString()].uniqueKeys
        generated["table ${table}".toString()].uniqueKeys.size() == count

        where:
        label                             | group                                 | table          | count
        'a subclass'                      | [NiRoot, NiChild]                     | 'ni_root'      | 1
        'a subclass naming a parent column' | [NiMixRoot, NiMixChild]             | 'ni_mix_root'  | 1
        'a subclass of a joined hierarchy' | [NiJRoot, NiJChild]                  | 'nijchild'     | 1
        'a subclass three levels down'    | [NiGrand, NiGrandChild, NiGrandGrand] | 'ni_grand'     | 1
    }

    @Unroll
    void "#label rejects a duplicate natural id and accepts a distinct one, as the domain binder does"() {
        when:
        Map<Boolean, List> outcome = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): attempts.collect { Map values -> save(type, values) }]
        }

        then:
        outcome[true] == outcome[false]
        outcome[true] == expected

        where:
        label                       | group                      | type        | attempts                                                                                          | expected
        'a subclass'                | [NiRoot, NiChild]          | NiChild     | [[code: 'a'], [code: 'b'], [code: 'a']]                                                           | ['saved', 'saved', 'rejected: JdbcSQLIntegrityConstraintViolationException']
        'a subclass with a parent property' | [NiMixRoot, NiMixChild] | NiMixChild | [[code: 'a', name: 'x'], [code: 'a', name: 'y'], [code: 'a', name: 'x']]                       | ['saved', 'saved', 'rejected: JdbcSQLIntegrityConstraintViolationException']
        'an embedded property'      | [NiEmb]                    | NiEmb       | [[code: 'a', home: new NiHome(street: 's', city: 'c')], [code: 'a', home: new NiHome(street: 's', city: 'd')], [code: 'a', home: new NiHome(street: 's', city: 'c')]] | ['saved', 'saved', 'rejected: JdbcSQLIntegrityConstraintViolationException']
        'a name that is no property' | [NiTypo]                  | NiTypo      | [[code: 'a'], [code: 'b'], [code: 'a']]                                                           | ['saved', 'saved', 'rejected: JdbcSQLIntegrityConstraintViolationException']
        'nothing but a missing name' | [NiOnlyTypo]              | NiOnlyTypo  | [[code: 'a'], [code: 'a']]                                                                        | ['saved', 'saved']
    }

    void "a subclass with a natural id is stored, found by query and updated as the domain binder does, and is also read by id"() {
        when:
        Map<Boolean, Map> outcome = [false, true].collectEntries { boolean generated ->
            boot([NiRoot, NiChild, NiMutRoot, NiMutChild], generated)
            Map steps = [:]
            Long immutable = null
            Long mutable = null
            steps.saveImmutable = attempt { immutable = NiChild.withTransaction { NiChild.newInstance(code: 'a', name: 'n').save(failOnError: true, flush: true).id } }
            steps.saveMutable = attempt { mutable = NiMutChild.withTransaction { NiMutChild.newInstance(code: 'a').save(failOnError: true, flush: true).id } }
            steps.listImmutable = attempt { NiChild.withNewSession { NiChild.list()*.code } }
            steps.findImmutable = attempt { NiChild.withNewSession { NiChild.findByCode('a')?.name } }
            steps.getImmutable = attempt { NiChild.withNewSession { NiChild.get(immutable).code } }
            steps.updateImmutable = attempt { NiChild.withTransaction { NiChild.get(immutable).code = 'changed' } }
            steps.updateMutable = attempt { NiMutChild.withTransaction { NiMutChild.get(mutable).code = 'changed' } }
            steps.reloadImmutable = attempt { NiChild.withNewSession { NiChild.get(immutable).code } }
            steps.reloadMutable = attempt { NiMutChild.withNewSession { NiMutChild.get(mutable).code } }
            [(generated): steps]
        }
        List<String> shared = ['saveImmutable', 'saveMutable', 'listImmutable', 'findImmutable']

        then: "what the domain binder can do is done the same way"
        outcome[true].subMap(shared) == outcome[false].subMap(shared)
        outcome[true].subMap(shared) == [saveImmutable: 'ok: 1', saveMutable: 'ok: 1', listImmutable: 'ok: [a]', findImmutable: 'ok: n']

        and: "the domain binder marks the properties as natural identifiers, which makes Hibernate 7 fail on every load of the subclass"
        ['getImmutable', 'updateImmutable', 'updateMutable', 'reloadImmutable', 'reloadMutable'].every {
            outcome[false][it] == 'failed: NullPointerException'
        }

        and: "the generated mode keeps the unique key and the updatability and loads the entity"
        outcome[true].subMap(['getImmutable', 'reloadImmutable', 'reloadMutable']) ==
                [getImmutable: 'ok: a', reloadImmutable: 'ok: a', reloadMutable: 'ok: changed']
    }

    void "a natural id on an embedded property reloads the embedded value"() {
        when:
        Map<Boolean, Object> outcome = [false, true].collectEntries { boolean generated ->
            boot([NiEmb], generated)
            Long id = NiEmb.withTransaction { NiEmb.newInstance(code: 'a', home: new NiHome(street: 's', city: 'c')).save(failOnError: true, flush: true).id }
            [(generated): NiEmb.withNewSession { NiEmb e = NiEmb.get(id); [e.code, e.home.street, e.home.city] }]
        }

        then:
        outcome[true] == outcome[false]
        outcome[true] == ['a', 's', 'c']
    }

    private static String attempt(Closure<?> step) {
        try {
            return "ok: ${step.call()}".toString()
        } catch (Throwable e) {
            Throwable root = e
            while (root.cause != null && root.cause != root) {
                root = root.cause
            }
            return "failed: ${root.class.simpleName}".toString()
        }
    }

    private static String save(Class type, Map values) {
        try {
            type.withTransaction { type.newInstance(values).save(failOnError: true, flush: true) }
            return 'saved'
        } catch (Throwable e) {
            Throwable root = e
            while (root.cause != null && root.cause != root) {
                root = root.cause
            }
            return "rejected: ${root.class.simpleName}".toString()
        }
    }
}

@Entity
class NiRoot {
    String name
}

@Entity
class NiChild extends NiRoot {
    String code
    static mapping = { id natural: 'code' }
}

@Entity
class NiMixRoot {
    String name
}

@Entity
class NiMixChild extends NiMixRoot {
    String code
    static mapping = { id natural: ['code', 'name'] }
}

@Entity
class NiMutRoot {
    String name
}

@Entity
class NiMutChild extends NiMutRoot {
    String code
    static mapping = { id natural: [properties: ['code'], mutable: true] }
}

@Entity
class NiBothRoot {
    String name
    static mapping = { id natural: 'name' }
}

@Entity
class NiBothChild extends NiBothRoot {
    String code
    static mapping = { id natural: 'code' }
}

@Entity
class NiGrand {
    String name
}

@Entity
class NiGrandChild extends NiGrand {
    String code
}

@Entity
class NiGrandGrand extends NiGrandChild {
    String extra
    static mapping = { id natural: ['extra', 'code'] }
}

@Entity
class NiFkTarget {
    String label
}

@Entity
class NiFkRoot {
    String name
}

@Entity
class NiFkChild extends NiFkRoot {
    NiFkTarget target
    String code
    static mapping = { id natural: ['target', 'code'] }
}

@Entity
class NiJRoot {
    String name
    static mapping = { tablePerHierarchy false }
}

@Entity
class NiJChild extends NiJRoot {
    String code
    String region
    static mapping = { id natural: ['region', 'code'] }
}

class NiHome {
    String street
    String city
}

@Entity
class NiEmb {
    String code
    NiHome home
    static embedded = ['home']
    static mapping = { id natural: ['code', 'home'] }
}

@Entity
class NiEmb2 {
    String code
    NiHome home
    static embedded = ['home']
    static mapping = { id natural: ['home', 'code'] }
}

@Entity
class NiEmbRoot {
    String label
}

@Entity
class NiEmbChild extends NiEmbRoot {
    String code
    NiHome home
    static embedded = ['home']
    static mapping = { id natural: ['home', 'code'] }
}

@Entity
class NiEmbOnly {
    NiHome home
    static embedded = ['home']
    static mapping = { id natural: ['home'] }
}

@Entity
class NiTypo {
    String code
    static mapping = { id natural: ['code', 'typo'] }
}

@Entity
class NiOnlyTypo {
    String code
    static mapping = { id natural: ['typo'] }
}
