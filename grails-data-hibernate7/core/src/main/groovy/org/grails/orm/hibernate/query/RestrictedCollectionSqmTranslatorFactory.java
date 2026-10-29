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

import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter;
import org.hibernate.query.sqm.sql.SqmTranslator;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.tree.SqmDmlStatement;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.expression.SqmCollectionSize;
import org.hibernate.query.sqm.tree.predicate.SqmEmptinessPredicate;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Translates queries the way Hibernate's standard translator does, except {@code is empty} and {@code is not empty}
 * on a many-to-many collection that has a restriction of its own, which they translate as {@code size(...) = 0} and
 * {@code size(...) > 0}.
 *
 * <p>Hibernate 7 tests the emptiness of a collection with an {@code exists} subquery over a join to the collection.
 * For a many-to-many collection it puts the restriction of the collection, the {@code where} fragment of its join
 * table, on the join to the element, which the {@code select 1} of that subquery never renders, so the restriction is
 * lost; {@code size(...)} and {@code member of} apply it to their subquery directly. A collection that shares a join
 * table and a key column with another one carries such a restriction (its element columns are not null), so without
 * it {@code is empty} would see the rows of the other collection (apache/grails-core#16748). A one-to-many collection
 * keeps its restriction on the main join, and every collection without a restriction keeps Hibernate's {@code exists}
 * subquery.</p>
 *
 * <p>GORM registers this factory as the {@code hibernate.query.sqm.translator} setting unless the application sets one
 * of its own. It covers HQL and criteria queries, and the update and delete statements Hibernate translates in one
 * piece. An update or delete of an entity whose hierarchy spans several tables is translated by Hibernate's
 * multi-table converter, which this factory does not reach, so there the emptiness test still sees the rows of the
 * other collection. A dialect that brings a translator of its own, such as Sybase, keeps it: queries on that
 * database are translated as Hibernate translates them.</p>
 *
 * @since 8.0.1
 */
public class RestrictedCollectionSqmTranslatorFactory implements SqmTranslatorFactory {

    @Override
    public SqmTranslator<SelectStatement> createSelectTranslator(
            SqmSelectStatement<?> sqmSelectStatement,
            QueryOptions queryOptions,
            DomainParameterXref domainParameterXref,
            QueryParameterBindings domainParameterBindings,
            LoadQueryInfluencers loadQueryInfluencers,
            SqlAstCreationContext creationContext,
            boolean deduplicateSelectionItems) {
        SqmTranslatorFactory dialectFactory = dialectFactory(creationContext);
        if (dialectFactory != null) {
            return dialectFactory.createSelectTranslator(sqmSelectStatement, queryOptions, domainParameterXref,
                    domainParameterBindings, loadQueryInfluencers, creationContext, deduplicateSelectionItems);
        }
        return new RestrictedCollectionSqmTranslator<>(creationContext, sqmSelectStatement, queryOptions,
                loadQueryInfluencers, domainParameterXref, domainParameterBindings, deduplicateSelectionItems);
    }

    @Override
    public SqmTranslator<? extends MutationStatement> createMutationTranslator(
            SqmDmlStatement<?> sqmDmlStatement,
            QueryOptions queryOptions,
            DomainParameterXref domainParameterXref,
            QueryParameterBindings domainParameterBindings,
            LoadQueryInfluencers loadQueryInfluencers,
            SqlAstCreationContext creationContext) {
        SqmTranslatorFactory dialectFactory = dialectFactory(creationContext);
        if (dialectFactory != null) {
            return dialectFactory.createMutationTranslator(sqmDmlStatement, queryOptions, domainParameterXref,
                    domainParameterBindings, loadQueryInfluencers, creationContext);
        }
        return new RestrictedCollectionSqmTranslator<>(creationContext, sqmDmlStatement, queryOptions,
                loadQueryInfluencers, domainParameterXref, domainParameterBindings, false);
    }

    /** The translator factory of the dialect, which Hibernate would use when no factory is configured, or null. */
    private static SqmTranslatorFactory dialectFactory(SqlAstCreationContext creationContext) {
        return creationContext.getSessionFactory().getJdbcServices().getDialect().getSqmTranslatorFactory();
    }

    /** Hibernate's translator, with the emptiness test of a restricted many-to-many collection as a size test. */
    static class RestrictedCollectionSqmTranslator<T extends Statement> extends BaseSqmToSqlAstConverter<T> {

        RestrictedCollectionSqmTranslator(
                SqlAstCreationContext creationContext,
                SqmStatement<?> statement,
                QueryOptions queryOptions,
                LoadQueryInfluencers loadQueryInfluencers,
                DomainParameterXref domainParameterXref,
                QueryParameterBindings domainParameterBindings,
                boolean deduplicateSelectionItems) {
            super(creationContext, statement, queryOptions, loadQueryInfluencers, domainParameterXref,
                    domainParameterBindings, deduplicateSelectionItems);
        }

        @Override
        public Object visitIsEmptyPredicate(SqmEmptinessPredicate predicate) {
            if (!losesRestriction(predicate.getPluralPath())) {
                return super.visitIsEmptyPredicate(predicate);
            }
            TypeConfiguration types = getCreationContext().getMappingMetamodel().getTypeConfiguration();
            Expression size = visitPluralAttributeSizeFunction(
                    new SqmCollectionSize(predicate.getPluralPath(), predicate.nodeBuilder()));
            return new ComparisonPredicate(
                    size,
                    predicate.isNegated() ? ComparisonOperator.GREATER_THAN : ComparisonOperator.EQUAL,
                    new QueryLiteral<>(0, types.getBasicTypeForJavaType(Integer.class)),
                    types.getBasicTypeForJavaType(Boolean.class));
        }

        /**
         * Whether Hibernate's emptiness test would lose the restriction of the collection of the path: a many-to-many
         * collection of an entity that has one. A collection inside an embeddable keeps Hibernate's translation.
         */
        private boolean losesRestriction(SqmPath<?> pluralPath) {
            SqmPath<?> owner = pluralPath.getLhs();
            if (owner == null || !(owner.getReferencedPathSource().getPathType() instanceof EntityDomainType<?> entity)) {
                return false;
            }
            EntityPersister persister =
                    getCreationContext().getMappingMetamodel().findEntityDescriptor(entity.getHibernateEntityName());
            AttributeMapping attribute = persister == null ? null :
                    persister.findAttributeMapping(pluralPath.getReferencedPathSource().getPathName());
            return attribute instanceof PluralAttributeMapping collection && collection.hasWhereRestrictions() &&
                    collection.getCollectionDescriptor().isManyToMany();
        }
    }
}
