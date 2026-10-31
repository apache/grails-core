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
package org.grails.orm.hibernate.persister;

import org.hibernate.MappingException;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.engine.jdbc.mutation.JdbcValueBindings;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.internal.MutationQueryOptions;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.Collection;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.BasicCollectionPersister;
import org.hibernate.persister.collection.mutation.RowMutationOperations;
import org.hibernate.sql.model.ast.MutatingTableReference;
import org.hibernate.sql.model.ast.builder.CollectionRowDeleteBuilder;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

import static org.hibernate.persister.collection.mutation.RowMutationOperations.DEFAULT_RESTRICTOR;

/**
 * The persister of a collection of entities in a join table that deletes the row of one removed element by the
 * owner's key and the element's foreign key.
 *
 * <p>Hibernate deletes a row of a set or a bag by the key and only the element columns that are not nullable. GORM
 * leaves the element column of a join table nullable, and it has to where several collections share the table with a
 * column each, so removing one element would delete every row of the owner's collection. A row of a collection of
 * entities always has its element, so this persister restricts the delete by the element columns, nullable or not.
 * {@link GormPersisterClassResolver} gives it to sets and bags of entities; lists and maps delete a row by their
 * index.</p>
 *
 * @since 8.0.1
 */
public class JoinTableCollectionPersister extends BasicCollectionPersister {

    /** Creates the persister, with the signature Hibernate calls a collection persister class with. */
    public JoinTableCollectionPersister(
            Collection collectionBinding,
            CollectionDataAccess cacheAccessStrategy,
            RuntimeModelCreationContext creationContext) throws MappingException, CacheException {
        super(collectionBinding, cacheAccessStrategy, creationContext);
    }

    @Override
    protected RowMutationOperations buildRowMutationOperations() {
        RowMutationOperations hibernate = super.buildRowMutationOperations();
        if (!hibernate.hasDeleteRow()) {
            return hibernate;
        }
        return new RowMutationOperations(
                this,
                hibernate.hasInsertRow() ? reference -> hibernate.getInsertRowOperation(reference.getTableMapping()) : null,
                hibernate.getInsertRowValues(),
                hibernate.hasUpdateRow() ? reference -> hibernate.getUpdateRowOperation() : null,
                hibernate.getUpdateRowValues(),
                hibernate.getUpdateRowRestrictions(),
                this::deleteRowOperation,
                (collection, key, element, rowPosition, session, bindings) ->
                        bindDeleteRowRestrictions(key, element, session, bindings),
                hibernate.getDeleteAllRowsOperationProducer());
    }

    private JdbcMutationOperation deleteRowOperation(MutatingTableReference reference) {
        PluralAttributeMapping attribute = getAttributeMapping();
        CollectionRowDeleteBuilder delete = new CollectionRowDeleteBuilder(this, reference, getFactory(), sqlWhereString);
        delete.addKeyRestrictionsLeniently(attribute.getKeyDescriptor().getKeyPart());
        delete.addKeyRestrictionsLeniently(attribute.getElementDescriptor());
        return getFactory().getJdbcServices().getDialect().getSqlAstTranslatorFactory()
                .buildModelMutationTranslator(delete.buildMutation(), getFactory())
                .translate(null, MutationQueryOptions.INSTANCE);
    }

    private void bindDeleteRowRestrictions(
            Object key, Object element, SharedSessionContractImplementor session, JdbcValueBindings bindings) {
        PluralAttributeMapping attribute = getAttributeMapping();
        attribute.getKeyDescriptor().getKeyPart().decompose(key, 0, bindings, null, DEFAULT_RESTRICTOR, session);
        attribute.getElementDescriptor().decompose(element, 0, bindings, null,
                (valueIndex, jdbcValueBindings, noop, jdbcValue, jdbcValueMapping) -> {
                    if (!jdbcValueMapping.isFormula()) {
                        jdbcValueBindings.bindValue(jdbcValue, jdbcValueMapping, ParameterUsage.RESTRICT);
                    }
                },
                session);
    }
}
