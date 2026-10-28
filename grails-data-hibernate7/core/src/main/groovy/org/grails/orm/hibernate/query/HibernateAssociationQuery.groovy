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

import java.util.List;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A thin wrapper over {@link HibernateQuery} that collects criteria for a single association scope.
 *
 * <p>When {@link HibernateQuery#createQuery(String)} is called (e.g. via {@code Person.withCriteria
 * { pets { eq 'name', 'Lucky' } }}), the {@code AbstractCriteriaBuilder} sets the current query to
 * this instance and routes all criteria added inside the closure through {@link
 * #add(Query.Criterion)}. Those criteria are held by an inner {@link HibernateQuery} scoped to the
 * associated entity.
 *
 * <p>At query-execution time, {@link PredicateGenerator} dispatches on this type and performs a
 * {@code LEFT JOIN} on {@link #associationPath}, then applies the collected predicates.
 *
 * @see PredicateGenerator
 * @see HibernateQuery#createQuery(String)
 */
public class HibernateAssociationQuery extends AssociationQuery {

    final String alias;

    /** Dotted property path used for the JPA join (e.g. {@code "pets"} or {@code "owner.address"}) */
    final String associationPath;

    /** Criteria collector — a real HibernateQuery scoped to the associated entity */
    private final HibernateQuery innerQuery;

    public HibernateAssociationQuery(
            HibernateSession session,
            GrailsHibernatePersistentEntity associatedEntity,
            Association association,
            String associationPath,
            String alias) {
        super(session, associatedEntity, association);
        this.alias = alias;
        this.associationPath = associationPath;
        this.innerQuery = new HibernateQuery(session, associatedEntity);
    }

    /** Returns the criteria collected inside the association closure. */
    public List<Query.Criterion> getAssociationCriteria() {
        return innerQuery.getAllCriteria();
    }

    @Override
    public GrailsHibernatePersistentEntity getEntity() {
        return (GrailsHibernatePersistentEntity) super.getEntity();
    }

    @Override
    public void add(Query.Criterion criterion) {
        innerQuery.add(criterion);
    }

    @Override
    public void add(Query.Junction currentJunction, Query.Criterion criterion) {
        innerQuery.add(currentJunction, criterion);
    }

    @Override
    public Query.Junction disjunction() {
        return innerQuery.disjunction();
    }

    @Override
    public Query.Junction negation() {
        return innerQuery.negation();
    }
}
