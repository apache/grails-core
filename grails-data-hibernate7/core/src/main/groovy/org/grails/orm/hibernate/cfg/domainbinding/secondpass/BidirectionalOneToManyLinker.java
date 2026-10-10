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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;

/**
 * Links bidirectional one-to-many associations by copying columns. The foreign key of the
 * association belongs to the to-one side, which always creates it: Hibernate's
 * {@link org.hibernate.mapping.ToOne#createForeignKey()} for a simple identifier, the composite
 * identifier binder, with the referenced identifier columns, for a composite one. The inverse
 * collection key therefore creates no positional key of its own. For a simple identifier the two
 * keys were the same key already; for a composite identifier they differ, share the implicit name,
 * and only the first one registered reached the schema.
 * <p>
 * The copied columns are the columns of the to-one side in the order its key was aligned with the
 * composite identifier of the owner, so the copy is aligned already and Hibernate must not apply the
 * identifier's permutation to it a second time. The to-one side is aligned first here, because its own
 * deferred alignment may not have run yet when this second pass does.
 */
public class BidirectionalOneToManyLinker {

    private final GrailsPropertyResolver grailsPropertyResolver;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;

    /** Creates a new {@link BidirectionalOneToManyLinker} instance. */
    public BidirectionalOneToManyLinker(
            GrailsPropertyResolver grailsPropertyResolver,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
        this.grailsPropertyResolver = grailsPropertyResolver;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    }

    /** Link. */
    public void link(
            Collection collection,
            PersistentClass associatedClass,
            DependantValue key,
            HibernatePersistentProperty otherSide) {
        collection.setInverse(true);
        key.disableForeignKey();

        Value toOneValue =
                grailsPropertyResolver.getProperty(associatedClass, otherSide.getName()).getValue();
        alignToOneSide(otherSide, toOneValue);
        for (Column column : toOneValue.getColumns()) {
            Column mappingColumn = new Column();
            mappingColumn.setName(column.getName());
            mappingColumn.setLength(column.getLength());
            mappingColumn.setNullable(otherSide.isNullable());
            mappingColumn.setSqlType(column.getSqlType());

            mappingColumn.setValue(key);
            key.addColumn(mappingColumn);
            key.getTable().addColumn(mappingColumn);
        }
        key.setSorted(true);
    }

    /**
     * Aligns the to-one side with the composite identifier of the entity that owns the collection, the
     * entity it references. Does nothing when that entity has no composite identifier or the to-one
     * side is aligned already.
     */
    private void alignToOneSide(HibernatePersistentProperty otherSide, Value toOneValue) {
        if (otherSide instanceof HibernateToOneProperty toOne && toOneValue instanceof SimpleValue value) {
            GrailsHibernatePersistentEntity owner = toOne.getHibernateAssociatedEntity();
            owner.getHibernateCompositeIdentity()
                    .ifPresent(compositeId -> compositeIdentifierToManyToOneBinder.alignWithReferencedIdentifier(
                            otherSide, value, compositeId, owner));
        }
    }
}
