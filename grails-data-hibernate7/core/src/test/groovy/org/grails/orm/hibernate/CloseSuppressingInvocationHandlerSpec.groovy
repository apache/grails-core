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
package org.grails.orm.hibernate

import org.hibernate.Session
import org.hibernate.query.Query
import spock.lang.Specification
import java.lang.reflect.Method

class CloseSuppressingInvocationHandlerSpec extends Specification {

    def "test close is suppressed"() {
        given:
        Session target = Mock(Session)
        GrailsHibernateTemplate template = Mock(GrailsHibernateTemplate)
        CloseSuppressingInvocationHandler handler = new CloseSuppressingInvocationHandler(target, template)
        Method closeMethod = Session.class.getMethod("close")

        when:
        def result = handler.invoke(null, closeMethod, null)

        then:
        0 * target.close()
        result == null
    }

    def "test equals and hashCode"() {
        given:
        Session target = Mock(Session)
        GrailsHibernateTemplate template = Mock(GrailsHibernateTemplate)
        CloseSuppressingInvocationHandler handler = new CloseSuppressingInvocationHandler(target, template)
        Method equalsMethod = Object.class.getMethod("equals", Object.class)
        Method hashCodeMethod = Object.class.getMethod("hashCode")
        def proxy = new Object()

        expect:
        handler.invoke(proxy, equalsMethod, [proxy] as Object[]) == true
        handler.invoke(proxy, equalsMethod, [new Object()] as Object[]) == false
        handler.invoke(proxy, hashCodeMethod, null) == System.identityHashCode(proxy)
    }

    def "test query preparation"() {
        given:
        Session target = Mock(Session)
        GrailsHibernateTemplate template = Mock(GrailsHibernateTemplate)
        CloseSuppressingInvocationHandler handler = new CloseSuppressingInvocationHandler(target, template)
        
        Query hibernateQuery = Mock(Query)
        Method createQueryMethod = Session.class.getMethod("createQuery", String.class)
        
        when:
        def result = handler.invoke(null, createQueryMethod, ["from Book"] as Object[])

        then:
        1 * target.createQuery("from Book") >> hibernateQuery
        1 * template.prepareQuery(hibernateQuery)
        result == hibernateQuery
    }

    def "test criteria preparation"() {
        given:
        Session target = Mock(Session)
        GrailsHibernateTemplate template = Mock(GrailsHibernateTemplate)
        CloseSuppressingInvocationHandler handler = new CloseSuppressingInvocationHandler(target, template)
        
        Query jpaQuery = Mock(Query)
        Method createQueryMethod = Session.class.getMethod("createQuery", String.class, Class.class)
        
        when:
        def result = handler.invoke(null, createQueryMethod, ["from Book", Object.class] as Object[])

        then:
        1 * target.createQuery("from Book", Object.class) >> jpaQuery
        1 * template.prepareCriteria(jpaQuery)
        result == jpaQuery
    }
}
