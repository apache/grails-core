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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * String-based query methods ({@code executeQuery}/{@code executeUpdate}/{@code find}/{@code findAll}
 * over a {@code CharSequence} query) must keep their convenience overloads delegating to the single
 * terminal {@code (query, Map, Map)} overload, so persistence adapters (e.g. Hibernate) can override
 * just the terminal to run real HQL. The base implementation itself does not support string queries
 * and reports that via {@code unsupported(...)}; it must NOT throw directly from the convenience
 * overloads (which would bypass the adapter override). Regression guard for the refactor that
 * replaced every overload body with a direct UnsupportedOperationException.
 */
class GormStaticApiStringQueryDelegationSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(StringQueryThing)

    void "convenience overloads delegate to the terminal overload that adapters override"() {
        given: "a static api that overrides only the terminal (query, Map, Map) overloads, as adapters do"
        def api = new TerminalOverridingStaticApi(StringQueryThing, datastore, [])

        expect: "the convenience overloads reach the (query, Map, Map) terminal override rather than throwing"
        api.executeQuery('from StringQueryThing') == ['Q']
        api.executeQuery('from StringQueryThing', [:]) == ['Q']
        api.executeUpdate('delete StringQueryThing') == 7
        api.executeUpdate('delete StringQueryThing', [:]) == 7
        api.find('from StringQueryThing') == 'F'
        api.findAll('from StringQueryThing') == ['FA']
    }

    void "the base implementation reports string queries as unsupported rather than silently working"() {
        given: "a plain static api with no adapter override"
        def api = new GormStaticApi(StringQueryThing, datastore, [])

        when:
        api.executeQuery('from StringQueryThing')

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('String-based queries')
    }

    static class TerminalOverridingStaticApi<D> extends GormStaticApi<D> {

        TerminalOverridingStaticApi(Class<D> persistentClass, Datastore datastore, List finders) {
            super(persistentClass, datastore, finders)
        }

        @Override
        List executeQuery(CharSequence query, Map params, Map args) { ['Q'] }

        @Override
        Integer executeUpdate(CharSequence query, Map params, Map args) { 7 }

        @Override
        D find(CharSequence query, Map params, Map args) { (D) 'F' }

        @Override
        List<D> findAll(CharSequence query, Map params, Map args) { (List<D>) ['FA'] }
    }
}

@Entity
class StringQueryThing {

    String name
}
