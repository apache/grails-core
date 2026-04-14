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

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A PagedResultList for Hibernate 7.
 *
 * @author burt
 * @since 7.0.0
 */
public class HibernatePagedResultList extends grails.gorm.PagedResultList {

    private final GrailsHibernatePersistentEntity entity;

    public HibernatePagedResultList(HibernateQuery query) {
        super(query);
        this.entity = query.getEntity();
    }

    public HibernatePagedResultList(GrailsHibernateTemplate template, GrailsHibernatePersistentEntity entity, Query query) {
        super(query);
        this.entity = entity;
    }

    public HibernatePagedResultList(GrailsHibernateTemplate template, PersistentEntity entity, Query query) {
        this(template, (GrailsHibernatePersistentEntity) entity, query);
    }

    public GrailsHibernatePersistentEntity getEntity() {
        return entity;
    }
}
