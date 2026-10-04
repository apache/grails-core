/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import jakarta.persistence.criteria.AbstractQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.hibernate.query.criteria.JpaSubQuery

import org.springframework.core.convert.ConversionService

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * A class that creates a JPA {@link CriteriaQuery} from a GORM {@link Query} and {@link DetachedCriteria}.
 *
 * @author burt
 * @author graemerocher
 * @since 7.0.0
 */
@CompileStatic
class JpaCriteriaQueryCreator<T> {

    private final Query.ProjectionList projections
    private final HibernateCriteriaBuilder criteriaBuilder
    private final GrailsHibernatePersistentEntity entity
    private final DetachedCriteria<?> detachedCriteria
    private final ConversionService conversionService
    private final HibernateQuery hibernateQuery
    private JpaQueryContext parentContext

    JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            CriteriaBuilder criteriaBuilder,
            PersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService) {
        this(projections, (HibernateCriteriaBuilder) criteriaBuilder, (GrailsHibernatePersistentEntity) entity, detachedCriteria, conversionService, null)
    }

    JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            HibernateCriteriaBuilder criteriaBuilder,
            GrailsHibernatePersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService,
            HibernateQuery hibernateQuery) {
        this.projections = projections
        this.criteriaBuilder = criteriaBuilder
        this.entity = entity
        this.detachedCriteria = detachedCriteria
        this.conversionService = conversionService
        this.hibernateQuery = hibernateQuery
    }

    void setParentContext(JpaQueryContext parentContext) {
        this.parentContext = parentContext
    }

    JpaCriteriaQuery<?> createQuery() {
        List<Query.Projection> projectionList = collectProjections()
        JpaCriteriaQuery<?> cq = createCriteriaQuery(projectionList)
        Class<?> javaClass = entity.javaClass
        Root<?> root = cq.from(javaClass)

        List<HibernateAlias> aliases = []
        if (hibernateQuery != null) {
            aliases.addAll(hibernateQuery.aliases)
        }
        for (Query.Criterion criterion : detachedCriteria.criteria) {
            if (criterion instanceof HibernateAlias) {
                aliases.add((HibernateAlias) criterion)
            }
        }

        JpaQueryContext context = JpaQueryContext.forSubquery(parentContext, aliases, root)
        registerDetachedJoins(context)
        discoverAliases(detachedCriteria.criteria, context)

        applyEagerFetchJoins(root, projectionList)

        new JpaProjectionAdapter(criteriaBuilder, context).adapt(projections, (AbstractQuery<?>) cq)
        assignGroupBy(cq, context)

        assignOrderBy(cq, context)
        assignCriteria(cq, root, context, entity)
        return cq
    }

    /**
     * Eagerly fetch-join the associations requested via {@code fetch:[assoc:'join']} (which a
     * dynamic finder turns into {@link HibernateQuery#join(String)} calls). Hibernate 5 initializes
     * these collections; without an explicit JPA {@code root.fetch(...)} they would only be plain
     * joins (or, when not referenced in a predicate, not materialized at all), leaving the
     * association uninitialized. Only applied to full-entity selects - a fetch join on a projection
     * query (for example {@code count}) is invalid.
     */
    private void applyEagerFetchJoins(Root<?> root, List<Query.Projection> projectionList) {
        if (hibernateQuery == null) {
            return
        }
        boolean entitySelect = true
        for (Query.Projection p : projectionList) {
            if (!(p instanceof Query.DistinctProjection)) {
                entitySelect = false
                break
            }
        }
        if (!entitySelect) {
            return
        }
        Map<String, ?> joinTypes = detachedCriteria.joinTypes
        for (String fetchPath : hibernateQuery.fetchJoinPaths) {
            if (fetchPath == null || fetchPath.indexOf('.') >= 0) {
                continue
            }
            Object configured = (joinTypes != null) ? joinTypes.get(fetchPath) : null
            JoinType joinType = (configured instanceof JoinType) ? (JoinType) configured : JoinType.LEFT
            try {
                root.fetch(fetchPath, joinType)
            } catch (IllegalArgumentException ignored) {
                // Not a fetchable association on this root; the plain join handling remains in place.
            }
        }
    }

    @SuppressWarnings('unchecked')
    <T> void populateSubquery(JpaSubQuery<T> subquery) {
        List<Query.Projection> projectionList = collectProjections()
        Class<?> javaClass = entity.javaClass
        Root<?> root = subquery.from(javaClass)

        List<HibernateAlias> aliases = []
        if (hibernateQuery != null) {
            aliases.addAll(hibernateQuery.aliases)
        }
        for (Query.Criterion criterion : detachedCriteria.criteria) {
            if (criterion instanceof HibernateAlias) {
                aliases.add((HibernateAlias) criterion)
            }
        }

        JpaQueryContext context = JpaQueryContext.forSubquery(parentContext, aliases, root)
        registerDetachedJoins(context)
        discoverAliases(detachedCriteria.criteria, context)

        new JpaProjectionAdapter(criteriaBuilder, context).adapt(projections, (AbstractQuery<?>) subquery)

        assignGroupBy(subquery, context)

        assignCriteria(subquery, root, context, entity)
    }

    private List<Query.Projection> collectProjections() {
        return projections.projectionList
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        List<Query.Projection> expressionProjections = []
        for (Query.Projection p : projections) {
            if (!(p instanceof Query.DistinctProjection)) {
                expressionProjections.add(p)
            }
        }

        if (expressionProjections.size() > 1) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createTupleQuery()
        } else if (expressionProjections.isEmpty()) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.javaClass)
        } else {
            Query.Projection first = expressionProjections.get(0)
            if (first instanceof Query.CountProjection || first instanceof Query.CountDistinctProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Long)
            } else if (first instanceof Query.AvgProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Double)
            } else if (first instanceof Query.IdProjection) {
                def identity = entity.identity
                Class<?> projectionType = identity != null ? identity.type : Object
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(projectionType)
            } else if (first instanceof Query.PropertyProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(resolveProjectionType((Query.PropertyProjection) first))
            }
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.javaClass)
        }
    }

    private void assignGroupBy(AbstractQuery<?> query, JpaQueryContext context) {
        List<Expression> groupByExpressions = []
        for (Query.GroupPropertyProjection groupPropertyProjection : collectGroupProjections()) {
            Expression<?> expr = context.getFullyQualifiedExpression(groupPropertyProjection.propertyName)
            if (expr != null) {
                groupByExpressions.add(expr)
            }
        }
        if (!groupByExpressions.isEmpty()) {
            query.groupBy(groupByExpressions as Expression[])
        }
    }

    private Class<?> resolveProjectionType(Query.PropertyProjection projection) {
        PersistentEntity persistentEntity = entity.mappingContext.getPersistentEntity(entity.javaClass.name)
        String propertyName = projection.propertyName
        if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR, 2)[1]
        }

        def property = persistentEntity.getPropertyByName(propertyName)
        if (property == null) {
            return Object
        }
        return property.type
    }

    @SuppressWarnings('unchecked')
    private void assignOrderBy(CriteriaQuery<?> cq, JpaQueryContext context) {
        List<Query.Order> orders = detachedCriteria.orders
        if (!orders.isEmpty()) {
            List<jakarta.persistence.criteria.Order> jpaOrders = []
            for (Query.Order order : orders) {
                String propertyName = order.property
                Expression<?> expression = context.getFullyQualifiedExpression(propertyName)
                jakarta.persistence.criteria.Order jpaOrder
                if (order.ignoreCase && expression.javaType == String) {
                    jpaOrder = order.direction == Query.Order.Direction.ASC ?
                            criteriaBuilder.asc(criteriaBuilder.lower((Expression<String>) expression)) :
                            criteriaBuilder.desc(criteriaBuilder.lower((Expression<String>) expression))
                } else {
                    jpaOrder = order.direction == Query.Order.Direction.ASC ?
                            criteriaBuilder.asc(expression) :
                            criteriaBuilder.desc(expression)
                }
                jpaOrders.add(jpaOrder)
            }
            cq.orderBy(jpaOrders as jakarta.persistence.criteria.Order[])
        }
    }

    private void discoverAliases(List<Query.Criterion> criteria, JpaQueryContext context) {
        if (criteria == null) {
            return
        }
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof HibernateAlias) {
                HibernateAlias ha = (HibernateAlias) criterion
                // If the alias is already defined in parent and materialized, just link it
                if (!context.hasAlias(ha.alias())) {
                    context.registerAlias(ha.alias(), ha)
                }
            } else if (criterion instanceof DetachedAssociationCriteria) {
                DetachedAssociationCriteria<?> dac = (DetachedAssociationCriteria<?>) criterion
                if (dac.alias != null) {
                    context.registerAlias(dac.alias, new HibernateAlias(dac.associationPath, dac.alias, JoinType.INNER))
                }
                discoverAliases(dac.criteria, context)
            } else if (criterion instanceof Query.PropertyNameCriterion) {
                Query.PropertyNameCriterion pnc = (Query.PropertyNameCriterion) criterion
                String propertyName = pnc.property
                if (propertyName.contains('.')) {
                    String alias = propertyName.substring(0, propertyName.indexOf('.'))
                    // Only register if not already known in this or parent context
                    if (!context.hasAlias(alias)) {
                        context.registerAlias(alias, new HibernateAlias(alias, alias, JoinType.INNER))
                    }
                }
            } else if (criterion instanceof Query.Junction) {
                discoverAliases(((Query.Junction) criterion).criteria, context)
            }
        }
    }

    private void registerDetachedJoins(JpaQueryContext context) {
        for (Map.Entry<String, ?> entry : detachedCriteria.joinTypes.entrySet()) {
            context.registerAlias(entry.key, new HibernateAlias(entry.key, entry.key, (JoinType) entry.value))
        }
    }

    private void assignCriteria(
            AbstractQuery<?> cq, Root<?> root, JpaQueryContext context, GrailsHibernatePersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.criteria
        if (!criteriaList.isEmpty()) {
            discoverAliases(criteriaList, context)
            PredicateGenerator predicateGenerator = new PredicateGenerator(criteriaBuilder, conversionService)
            def predicate = predicateGenerator.generate(cq, root, criteriaList, context, entity)
            if (predicate != null) {
                cq.where(predicate)
            }
        }
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        List<Query.GroupPropertyProjection> result = []
        for (Query.Projection p : projections.projectionList) {
            if (p instanceof Query.GroupPropertyProjection) {
                result.add((Query.GroupPropertyProjection) p)
            }
        }
        return result
    }

}
