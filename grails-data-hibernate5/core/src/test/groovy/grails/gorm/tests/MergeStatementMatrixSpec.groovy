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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.EmptyInterceptor
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Merge of a new, a detached and a managed instance, for each kind of id, with and without an interceptor that calls
 * no instance transient (the subclass): the number of inserts and updates the merge writes.
 */
@Issue('https://github.com/apache/grails-core/issues/14354')
class MergeStatementMatrixSpec extends Specification {

    @Shared boolean neverTransient = false

    @Shared List<String> kinds = ['generated', 'assigned']

    @Shared List<String> statements = Collections.synchronizedList(new ArrayList<String>())

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(settings()),
            MxGenerated, MxAssignedNoVersion, MxAssignedVersioned
    )

    protected Map<String, Object> settings() {
        [(Settings.SETTING_DB_CREATE): 'create-drop',
         'hibernate.session_factory.statement_inspector': new MxSqlCapture(statements)]
    }

    static Object fresh(String kind, String key) {
        switch (kind) {
            case 'generated': return new MxGenerated(title: key)
            case 'assigned': return new MxAssignedNoVersion(id: key, title: key)
            case 'versioned': return new MxAssignedVersioned(id: key, title: key)
            default: throw new IllegalArgumentException(kind)
        }
    }

    static Object stored(String kind, String key) {
        Object instance = fresh(kind, key)
        instance.merge(flush: true)
    }

    long count(String kind, String prefix) {
        statements.count { it.toLowerCase().startsWith(prefix) && it.toLowerCase().contains(table(kind)) }
    }

    static String table(String kind) {
        [generated: 'mx_generated', assigned: 'mx_assigned_no_version', versioned: 'mx_assigned_versioned'][kind]
    }

    static Class entityClass(String kind) {
        [generated: MxGenerated, assigned: MxAssignedNoVersion, versioned: MxAssignedVersioned][kind]
    }

    @Unroll
    void "merge of a new #kind instance writes one insert and no update"() {
        given:
        String key = "new-${kind}"

        when:
        statements.clear()
        MxGenerated.withNewTransaction { fresh(kind, key).merge(flush: true) }

        then:
        count(kind, 'insert') == 1
        count(kind, 'update') == 0

        where:
        kind << kinds
    }

    @Unroll
    void "merge of a detached #kind instance #changeText"() {
        given:
        String key = "det-${kind}-${change}"
        Object detached = MxGenerated.withNewTransaction { stored(kind, key) }

        when:
        if (change) {
            detached.title = key + '-changed'
        }
        statements.clear()
        MxGenerated.withNewTransaction { detached.merge(flush: true) }
        String title = MxGenerated.withNewTransaction { entityClass(kind).list()*.title.find { it == key + '-changed' } }

        then:
        count(kind, 'insert') == 0
        count(kind, 'update') == (change ? 1 : 0)
        title == (change ? key + '-changed' : null)

        where:
        [kind, change] << [kinds, [true, false]].combinations()
        changeText = change ? 'writes one update' : 'writes nothing'
    }

    @IgnoreIf({ instance.neverTransient })
    @Unroll
    void "a second merge of a new #kind instance before the first insert runs stores the newer values (#form)"() {
        given:
        String key = "twice-${kind}-${form}"

        when:
        statements.clear()
        MxGenerated.withNewTransaction {
            Object first = fresh(kind, key)
            first.merge()
            if (form == 'same instance') {
                first.title = 'newer'
                first.merge(flush: true)
            } else {
                Object second = fresh(kind, key)
                second.title = 'newer'
                second.merge(flush: true)
            }
        }
        List<String> titles = MxGenerated.withNewTransaction { entityClass(kind).list()*.title }

        then:
        titles.contains('newer')
        !titles.contains(key)

        where:
        [kind, form] << [kinds - 'generated', ['same instance', 'another instance with the same id']].combinations()
    }

    @IgnoreIf({ instance.neverTransient })
    @Unroll
    void "a #kind instance merged and then changed and merged as #form, flushed first: #flushFirst, ends as one row with the newer title"() {
        given:
        String key = "again-${kind}-${form}-${flushFirst}"

        when:
        MxGenerated.withNewTransaction {
            Object first = fresh(kind, key)
            Object merged = first.merge(flush: flushFirst)
            switch (form) {
                case 'the same instance':
                    first.title = key + '-newer'
                    first.merge(flush: true)
                    break
                case 'the instance merge returned':
                    merged.title = key + '-newer'
                    merged.merge(flush: true)
                    break
                case 'a second instance with the same id':
                    Object second = fresh(kind, key)
                    second.title = key + '-newer'
                    second.merge(flush: true)
                    break
                default:
                    first.title = key + '-newer'
                    first.merge(flush: false)
                    first.title = key + '-newest'
                    first.merge(flush: true)
            }
        }
        List<String> titles = MxGenerated.withNewTransaction { entityClass(kind).list()*.title.findAll { it.startsWith(key) } }

        then:
        titles == [key + (form == 'the same instance, changed twice' ? '-newest' : '-newer')]

        where:
        [kind, flushFirst, form] << [
                ['assigned', 'versioned'],
                [false, true],
                ['the same instance', 'the instance merge returned', 'a second instance with the same id', 'the same instance, changed twice']
        ].combinations()
    }

    @IgnoreIf({ instance.neverTransient })
    @Unroll
    void "a generated instance merged and then its merged copy changed and merged again, flushed first: #flushFirst, ends as one row"() {
        given:
        String key = "again-generated-${flushFirst}"

        when:
        MxGenerated.withNewTransaction {
            MxGenerated merged = new MxGenerated(title: key).merge(flush: flushFirst)
            merged.title = key + '-newer'
            merged.merge(flush: true)
        }
        List<String> titles = MxGenerated.withNewTransaction { MxGenerated.list()*.title.findAll { it.startsWith(key) } }

        then:
        titles == [key + '-newer']

        where:
        flushFirst << [false, true]
    }

    @Unroll
    void "merge of a managed #kind instance writes its change once"() {
        given:
        String key = "man-${kind}"

        when:
        statements.clear()
        MxGenerated.withNewTransaction {
            Object instance = stored(kind, key)
            instance.title = key + '-changed'
            instance.merge(flush: true)
        }

        then:
        count(kind, 'insert') == 1
        count(kind, 'update') == 1

        where:
        kind << kinds
    }

    @Unroll
    void "merge of an initialized proxy of a #kind instance, handed to the instance api, merges the entity and writes one update"() {
        given:
        String key = "proxy-${kind}"
        Object id = MxGenerated.withNewTransaction { stored(kind, key).id }
        Object api = datastore.gormEnhancer.findInstanceApi(entityClass(kind))

        when:
        statements.clear()
        Object merged = MxGenerated.withNewTransaction {
            Object proxy = entityClass(kind).load(id)
            assert proxy instanceof HibernateProxy
            Hibernate.initialize(proxy)
            assert Hibernate.isInitialized(proxy)
            proxy.title = key + '-changed'
            api.merge(proxy, [flush: true])
        }

        then:
        !(merged instanceof HibernateProxy)
        merged.title == key + '-changed'
        count(kind, 'insert') == 0
        count(kind, 'update') == 1

        where:
        kind << kinds
    }

    static class MxNeverTransient extends EmptyInterceptor {
        @Override
        Boolean isTransient(Object entity) {
            Boolean.FALSE
        }
    }

    static class MxSqlCapture implements StatementInspector {
        private final List<String> statements

        MxSqlCapture(List<String> statements) {
            this.statements = statements
        }

        @Override
        String inspect(String sql) {
            statements.add(sql)
            return sql
        }
    }
}

@Entity
class MxGenerated {
    Long id
    Long version
    String title
}

@Entity
class MxAssignedVersioned {
    String id
    Long version
    String title

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class MxAssignedNoVersion {
    String id
    String title

    static mapping = {
        id generator: 'assigned'
        version false
    }
}

/**
 * An interceptor that calls nothing transient cannot work with a generated id, which has no value to look up, so the
 * kinds with an id that the application assigns run here.
 */
class MergeStatementMatrixNeverTransientSpec extends MergeStatementMatrixSpec {

    void setupSpec() {
        kinds = ['assigned']
        neverTransient = true
    }

    @Override
    protected Map<String, Object> settings() {
        Map<String, Object> settings = super.settings()
        settings['hibernate.session_factory.interceptor'] = new MxNeverTransient()
        settings
    }
}
