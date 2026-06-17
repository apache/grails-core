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
package org.grails.orm.hibernate.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A class that creates a JPA {@link CriteriaQuery} from a GORM {@link Query} and {@link DetachedCriteria}.
 *
 * @author burt
 * @author graemerocher
 * @since 7.0.0
 */
public class JpaCriteriaQueryCreator<T> {

    private final Query.ProjectionList projections;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final GrailsHibernatePersistentEntity entity;
    private final DetachedCriteria<?> detachedCriteria;
    private final ConversionService conversionService;
    private final HibernateQuery hibernateQuery;
    private JpaQueryContext parentContext;

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            CriteriaBuilder criteriaBuilder,
            PersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService) {
        this(projections, (HibernateCriteriaBuilder) criteriaBuilder, (GrailsHibernatePersistentEntity) entity, detachedCriteria, conversionService, null);
    }

    public JpaCriteriaQueryCreator(
            Query.ProjectionList projections,
            HibernateCriteriaBuilder criteriaBuilder,
            GrailsHibernatePersistentEntity entity,
            DetachedCriteria<?> detachedCriteria,
            ConversionService conversionService,
            HibernateQuery hibernateQuery) {
        this.projections = projections;
        this.criteriaBuilder = criteriaBuilder;
        this.entity = entity;
        this.detachedCriteria = detachedCriteria;
        this.conversionService = conversionService;
        this.hibernateQuery = hibernateQuery;
    }

    public void setParentContext(JpaQueryContext parentContext) {
        this.parentContext = parentContext;
    }

    public JpaCriteriaQuery<?> createQuery() {
        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);
        
        List<HibernateAlias> aliases = new ArrayList<>();
        if (hibernateQuery != null) {
            aliases.addAll(hibernateQuery.getAliases());
        }
        for (Query.Criterion criterion : detachedCriteria.getCriteria()) {
            if (criterion instanceof HibernateAlias ha) {
                aliases.add(ha);
            }
        }

        var context = JpaQueryContext.forSubquery(parentContext, aliases, root);
        registerDetachedJoins(context);
        discoverAliases(detachedCriteria.getCriteria(), context);

        applyEagerFetchJoins(root, projectionList);

        new JpaProjectionAdapter(criteriaBuilder, context).adapt(projections, (AbstractQuery<?>) cq);
        assignGroupBy(cq, context);

        assignOrderBy(cq, context);
        assignCriteria(cq, root, context, entity);
        return cq;
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
            return;
        }
        boolean entitySelect = projectionList.stream().noneMatch(p -> !(p instanceof Query.DistinctProjection));
        if (!entitySelect) {
            return;
        }
        Map<String, ?> joinTypes = detachedCriteria.getJoinTypes();
        for (String fetchPath : hibernateQuery.getFetchJoinPaths()) {
            if (fetchPath == null || fetchPath.indexOf('.') >= 0) {
                continue;
            }
            Object configured = (joinTypes != null) ? joinTypes.get(fetchPath) : null;
            JoinType joinType = (configured instanceof JoinType jt) ? jt : JoinType.LEFT;
            try {
                root.fetch(fetchPath, joinType);
            }
            catch (IllegalArgumentException ignored) {
                // Not a fetchable association on this root; the plain join handling remains in place.
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);
        
        List<HibernateAlias> aliases = new ArrayList<>();
        if (hibernateQuery != null) {
            aliases.addAll(hibernateQuery.getAliases());
        }
        for (Query.Criterion criterion : detachedCriteria.getCriteria()) {
            if (criterion instanceof HibernateAlias ha) {
                aliases.add(ha);
            }
        }

        var context = JpaQueryContext.forSubquery(parentContext, aliases, root);
        registerDetachedJoins(context);
        discoverAliases(detachedCriteria.getCriteria(), context);

        new JpaProjectionAdapter(criteriaBuilder, context).adapt(projections, (AbstractQuery<?>) subquery);

        assignGroupBy(subquery, context);

        assignCriteria(subquery, root, context, entity);
    }

    private List<Query.Projection> collectProjections() {
        return projections.getProjectionList();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        List<Query.Projection> expressionProjections = projections.stream()
                .filter(p -> !(p instanceof Query.DistinctProjection))
                .toList();

        if (expressionProjections.size() > 1) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createTupleQuery();
        } else if (expressionProjections.isEmpty()) {
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getJavaClass());
        } else {
            var first = expressionProjections.get(0);
            if (first instanceof Query.CountProjection || first instanceof Query.CountDistinctProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Long.class);
            } else if (first instanceof Query.AvgProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(Double.class);
            } else if (first instanceof Query.IdProjection) {
                var identity = entity.getIdentity();
                Class<?> projectionType = identity != null ? identity.getType() : Object.class;
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(projectionType);
            } else if (first instanceof Query.PropertyProjection propertyProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(resolveProjectionType(propertyProjection));
            }
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getJavaClass());
        }
    }

    private void assignGroupBy(AbstractQuery<?> query, JpaQueryContext context) {
        var groupByExpressions = collectGroupProjections().stream()
                .map(groupPropertyProjection -> context.getFullyQualifiedExpression(groupPropertyProjection.getPropertyName()))
                .filter(Objects::nonNull)
                .toArray(Expression[]::new);
        if (groupByExpressions.length > 0) {
            query.groupBy(groupByExpressions);
        }
    }

    private Class<?> resolveProjectionType(Query.PropertyProjection projection) {
        PersistentEntity persistentEntity = entity.getMappingContext().getPersistentEntity(entity.getJavaClass().getName());
        String propertyName = projection.getPropertyName();
        if (propertyName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
            propertyName = propertyName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR, 2)[1];
        }

        var property = persistentEntity.getPropertyByName(propertyName);
        if (property == null) {
            return Object.class;
        }
        return property.getType();
    }

    @SuppressWarnings("unchecked")
    private void assignOrderBy(CriteriaQuery<?> cq, JpaQueryContext context) {
        List<Query.Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            var jpaOrders = orders.stream()
                    .map(order -> {
                        var propertyName = order.getProperty();
                        Expression<?> expression = context.getFullyQualifiedExpression(propertyName);
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            return order.getDirection().equals(Query.Order.Direction.ASC) ?
                                    criteriaBuilder.asc(criteriaBuilder.lower((Expression<String>) expression)) :
                                    criteriaBuilder.desc(criteriaBuilder.lower((Expression<String>) expression));
                        } else {
                            return order.getDirection().equals(Query.Order.Direction.ASC) ?
                                    criteriaBuilder.asc(expression) :
                                    criteriaBuilder.desc(expression);
                        }
                    })
                    .toArray(jakarta.persistence.criteria.Order[]::new);
            cq.orderBy(jpaOrders);
        }
    }

    private void discoverAliases(List<Query.Criterion> criteria, JpaQueryContext context) {
        if (criteria == null) return;
        for (Query.Criterion criterion : criteria) {
            if (criterion instanceof HibernateAlias ha) {
                // If the alias is already defined in parent and materialized, just link it
                if (!context.hasAlias(ha.alias())) {
                    context.registerAlias(ha.alias(), ha);
                }
            } else if (criterion instanceof DetachedAssociationCriteria<?> dac) {
                if (dac.getAlias() != null) {
                    context.registerAlias(dac.getAlias(), new HibernateAlias(dac.getAssociationPath(), dac.getAlias()));
                }
                discoverAliases(dac.getCriteria(), context);
            } else if (criterion instanceof Query.PropertyNameCriterion pnc) {
                String propertyName = pnc.getProperty();
                if (propertyName.contains(".")) {
                    String alias = propertyName.substring(0, propertyName.indexOf("."));
                    // Only register if not already known in this or parent context
                    if (!context.hasAlias(alias)) {
                        context.registerAlias(alias, new HibernateAlias(alias, alias));
                    }
                }
            } else if (criterion instanceof Query.Junction junction) {
                discoverAliases(junction.getCriteria(), context);
            }
        }
    }

    private void registerDetachedJoins(JpaQueryContext context) {
        detachedCriteria.getJoinTypes().forEach((path, joinType) ->
                context.registerAlias(path, new HibernateAlias(path, path, joinType))
        );
    }

    private void assignCriteria(
            AbstractQuery<?> cq, Root<?> root, JpaQueryContext context, GrailsHibernatePersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            discoverAliases(criteriaList, context);
            var predicateGenerator = new PredicateGenerator(criteriaBuilder, conversionService);
            var predicate = predicateGenerator.generate(cq, root, criteriaList, context, entity);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
    }

    private List<Query.GroupPropertyProjection> collectGroupProjections() {
        return projections.getProjectionList().stream()
                .filter(Query.GroupPropertyProjection.class::isInstance)
                .map(Query.GroupPropertyProjection.class::cast)
                .toList();
    }
}
