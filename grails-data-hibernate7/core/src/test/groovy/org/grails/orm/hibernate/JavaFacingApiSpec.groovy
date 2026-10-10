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

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import jakarta.persistence.criteria.JoinType
import org.hibernate.Session
import spock.lang.Specification
import spock.lang.Unroll

import grails.orm.HibernateCriteriaBuilder
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider
import org.grails.orm.hibernate.query.HibernateAlias
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor
import org.grails.orm.hibernate.support.hibernate7.HibernateCallback
import org.grails.orm.hibernate.support.hibernate7.HibernateOperations
import org.grails.orm.hibernate.support.hibernate7.SessionFactoryUtils
import org.grails.orm.hibernate.support.hibernate7.support.OpenSessionInViewInterceptor

/**
 * Pins the API that Java callers saw before the Hibernate 7 modules were converted to Groovy. A Groovy
 * {@code static final} without {@code public} is a property, so these constants stopped being readable as
 * {@code Type.CONSTANT} from Java.
 */
class JavaFacingApiSpec extends Specification {

    @Unroll
    void '#name of #type.simpleName is a public static final field'() {
        when:
        Field field = type.getField(name)

        then:
        Modifier.isPublic(field.modifiers)
        Modifier.isStatic(field.modifiers)
        Modifier.isFinal(field.modifiers)

        where:
        type                                    | name
        HibernateCriteriaBuilder                | 'ALIAS_SEPARATOR'
        HibernateCriteriaBuilder                | 'BOOLEAN'
        HibernateCriteriaBuilder                | 'SERIALIZABLE'
        GrailsHibernateUtil                     | 'ARGUMENT_MAX'
        GrailsHibernateUtil                     | 'ORDER_DESC'
        IdentityEnumType                        | 'ENUM_ID_ACCESSOR'
        IdentityEnumType                        | 'PARAM_ENUM_CLASS'
        BackticksRemover                        | 'BACKTICK'
        ClosureEventTriggeringInterceptor       | 'BEFORE_INSERT_EVENT'
        ClosureEventTriggeringInterceptor       | 'ONLOAD_SAVE'
        SessionFactoryUtils                     | 'SESSION_SYNCHRONIZATION_ORDER'
        OpenSessionInViewInterceptor            | 'PARTICIPATE_SUFFIX'
    }

    void 'every SQL type constant of the criteria builder is a public static final field'() {
        expect:
        ['BIG_DECIMAL', 'BINARY', 'BLOB', 'CLOB', 'DATE', 'TEXT', 'TIMESTAMP', 'URL', 'YES_NO'].every {
            int modifiers = HibernateCriteriaBuilder.getField(it).modifiers
            Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
        }
    }

    void 'every deprecated query argument constant of GrailsHibernateUtil is a public field'() {
        expect:
        GrailsHibernateUtil.declaredFields.findAll { it.name.startsWith('ARGUMENT_') || it.name.startsWith('ORDER_') }
                .every { Modifier.isPublic(it.modifiers) }
        GrailsHibernateUtil.ARGUMENT_MAX == 'max'
    }

    void 'the two argument HibernateAlias constructor joins with INNER'() {
        when:
        HibernateAlias alias = HibernateAlias.getConstructor(String, String).newInstance('face', 'f')

        then:
        alias.path() == 'face'
        alias.alias() == 'f'
        alias.joinType() == JoinType.INNER
    }

    @Unroll
    void '#type.simpleName configureNamingStrategy keeps declaring its checked exceptions'() {
        when:
        List<Class<?>> declared = type.getMethod('configureNamingStrategy', String, Object).exceptionTypes as List

        then:
        declared.containsAll([ClassNotFoundException, InstantiationException, IllegalAccessException])

        where:
        type << [NamingStrategyProvider]
    }

    @Unroll
    void '#method of #type.simpleName keeps its Nullable result'() {
        when:
        List<String> annotations = type.getMethod(method, parameters as Class[]).annotatedReturnType.annotations*.annotationType()*.name

        then:
        annotations.contains('org.jspecify.annotations.Nullable')

        where:
        type                | method           | parameters
        HibernateCallback   | 'doInHibernate'  | [Session]
        HibernateOperations | 'execute'        | [HibernateCallback]
        HibernateOperations | 'get'            | [Class, Serializable]
        HibernateOperations | 'get'            | [String, Serializable]
    }
}
