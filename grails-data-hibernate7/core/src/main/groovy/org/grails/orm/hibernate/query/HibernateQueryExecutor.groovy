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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import jakarta.persistence.LockModeType
import jakarta.persistence.Tuple
import org.hibernate.Session
import org.hibernate.query.Query
import org.hibernate.query.QueryFlushMode
import org.hibernate.query.criteria.JpaCriteriaQuery

import org.grails.datastore.mapping.proxy.ProxyHandler

@CompileStatic
@SuppressWarnings(['ClassStartsWithBlankLine', 'Indentation'])
record HibernateQueryExecutor(
        Integer offset,
        Integer maxResults,
        LockModeType lockResult,
        Boolean queryCache,
        Integer fetchSize,
        Integer timeout,
        QueryFlushMode flushMode,
        Boolean readOnly,
        ProxyHandler proxyHandler) {

    List list(Session session, JpaCriteriaQuery jpaCq) {
        return configureQuery(session, jpaCq).resultList
    }

    Object scroll(Session session, JpaCriteriaQuery jpaCq) {
        return configureQuery(session, jpaCq).scroll()
    }

    Object singleResult(Session session, JpaCriteriaQuery jpaCq) {
        Query query = configureQuery(session, jpaCq)
        try {
            Object singleResult = query.singleResult
            return proxyHandler.unwrap(singleResult)
        } catch (org.hibernate.NonUniqueResultException | jakarta.persistence.NonUniqueResultException e) {
            return proxyHandler.unwrap(query.resultList.get(0))
        } catch (jakarta.persistence.NoResultException e) {
            return null
        }
    }

    private Query configureQuery(Session session, JpaCriteriaQuery jpaCq) {
        Query query = session.createQuery(jpaCq)
        if (Tuple.equals(jpaCq.resultType)) {
            query.setTupleTransformer({ payload, aliases -> payload })
        }
        if (offset != null && offset > 0) {
            query.setFirstResult(offset)
        }
        if (queryCache != null) {
            query.setHint('org.hibernate.cacheable', queryCache)
        }
        if (maxResults != null && maxResults > 0) {
            query.setMaxResults(maxResults)
        }
        if (lockResult != null) {
            query.setLockMode(lockResult)
        }
        if (fetchSize != null && fetchSize > 0) {
            query.setFetchSize(fetchSize)
        }
        if (timeout != null && timeout > 0) {
            query.setTimeout(timeout)
        }
        if (flushMode != null) {
            query.setQueryFlushMode(flushMode)
        }
        if (readOnly != null) {
            query.setReadOnly(readOnly)
        }
        return query
    }

}
