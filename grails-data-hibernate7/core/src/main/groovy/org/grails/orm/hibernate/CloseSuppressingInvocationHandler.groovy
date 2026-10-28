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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

import groovy.transform.CompileStatic
import org.hibernate.Session
import org.hibernate.query.Query

/**
 * Invocation handler that suppresses close calls on Hibernate Sessions. Also prepares returned
 * Query and Criteria objects.
 *
 * @see org.hibernate.Session#close
 */
@CompileStatic
class CloseSuppressingInvocationHandler implements InvocationHandler {

    protected final Session target
    protected final GrailsHibernateTemplate template

    CloseSuppressingInvocationHandler(Session target, GrailsHibernateTemplate template) {
        this.target = target
        this.template = template
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        // Invocation on Session interface coming in...

        switch (method.name) {
            case 'equals':
                // Only consider equal when proxies are identical.
                return Objects.equals(proxy, args[0])
            case 'hashCode':
                // Use hashCode of Session proxy.
                return System.identityHashCode(proxy)
            case 'close':
                // Handle close method: suppress, not valid.
                return null
            default:
                // do nothing
                break
        }

        Object retVal = method.invoke(target, args)

        // If return value is a Query or Criteria, apply transaction timeout.
        // Applies to createQuery, getNamedQuery, createCriteria.
        if (retVal instanceof Query) {
            template.prepareQuery((Query) retVal)
        }
        if (retVal instanceof Query) {
            template.prepareCriteria((Query) retVal)
        }

        return retVal
    }

}
