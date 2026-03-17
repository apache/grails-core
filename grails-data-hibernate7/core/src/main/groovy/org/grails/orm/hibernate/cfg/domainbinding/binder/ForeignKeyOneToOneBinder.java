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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

/**
 * Binds a {@link HibernateOneToOneProperty} whose foreign key resides on this side as a Hibernate
 * {@link ManyToOne} value, and applies unique-key constraints as needed.
 *
 * <p>This handles the case where {@code isValidHibernateOneToOne()} is {@code false} — i.e. the
 * association cannot be mapped as a Hibernate {@code OneToOne}, so it falls back to a ManyToOne
 * column with an alternate unique key.
 */
public class ForeignKeyOneToOneBinder {

    private final ManyToOneBinder manyToOneBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;

    public ForeignKeyOneToOneBinder(
            ManyToOneBinder manyToOneBinder, SimpleValueColumnFetcher simpleValueColumnFetcher) {
        this.manyToOneBinder = manyToOneBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
    }

    /**
     * Binds the one-to-one property as a {@link ManyToOne} value and applies unique-key constraints.
     */
    public ManyToOne bind(HibernateOneToOneProperty property, String path) {
        Table table = property.getTable();
        GrailsHibernatePersistentEntity refDomainClass = property.getHibernateAssociatedEntity();
        ManyToOne manyToOne = manyToOneBinder.bindManyToOne(property, path);
        if (refDomainClass.getHibernateCompositeIdentity().isEmpty()) {
            bindUniqueKey(property, manyToOne);
        }
        return manyToOne;
    }

    private void bindUniqueKey(HibernateOneToOneProperty property, ManyToOne manyToOne) {
        PropertyConfig config = property.getMappedForm();
        manyToOne.setAlternateUniqueKey(true);
        Column c = simpleValueColumnFetcher.getColumnForSimpleValue(manyToOne);
        if (c == null) {
            throw new MappingException("There is no column for property [" + property.getName() + "]");
        }
        if (!config.isUniqueWithinGroup()) {
            c.setUnique(config.isUnique());
        } else if (property.isBidirectional()
                && property.getHibernateInverseSide().isValidHibernateOneToOne()) {
            c.setUnique(true);
        }
    }
}
