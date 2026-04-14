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
package org.grails.orm.hibernate.query;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Bridges the Query API with the Hibernate HQL for SELECT queries.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
//TODO Cleanup
public class HibernateHqlQueryCreator {

    public static Query createHqlQuery(
            HibernateDatastore datastore,
            SessionFactory sessionFactory,
            org.grails.datastore.mapping.model.PersistentEntity entity,
            HqlQueryContext ctx) {
        Session session = sessionFactory.getCurrentSession();
        HibernateSession hibernateSession = new HibernateSession(datastore, sessionFactory);
        String hqlStr = ctx.hql();
        if (ctx.isUpdate()) {
            var mq = session.createMutationQuery(hqlStr);
            return new MutationHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx, new MutationQueryDelegate(mq));
        } else {
            var q = ctx.isNative() ?
                session.createNativeQuery(hqlStr, ctx.targetClass()) :
                session.createQuery(hqlStr, ctx.targetClass());
            return new SelectHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx, new SelectQueryDelegate(q));
        }
    }
}
