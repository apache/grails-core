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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * A class that creates a JPA {@link CriteriaQuery} from a GORM {@link Query} and {@link DetachedCriteria}.
 *
 * @since 8.0
 */
public class JpaCriteriaQueryCreator<T> {

    private final Query.ProjectionList projections;
    private final HibernateCriteriaBuilder criteriaBuilder;
    private final GrailsHibernatePersistentEntity entity;
    private final DetachedCriteria<?> detachedCriteria;
    private final ConversionService conversionService;
    private final HibernateQuery hibernateQuery;
    private final QueryAliasRegistrar aliasRegistrar;
    private JpaQueryContext parentContext;
    private JpaQueryContext context;
    private boolean distinctInMemory;

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
        this.aliasRegistrar = new QueryAliasRegistrar(detachedCriteria);
    }

    public void setParentContext(JpaQueryContext parentContext) {
        this.parentContext = parentContext;
    }

    /**
     * Returns the values of the criteria parameters in the query or subquery last built by this creator, which
     * must be bound to the query once it is created. A subquery built under a parent context records its values
     * in the parent's context, so they are bound with the query that contains it.
     *
     * @return the value of each parameter
     */
    public Map<ParameterExpression<?>, Object> getParameterValues() {
        return context != null ? context.getParameterValues() : Collections.emptyMap();
    }

    /**
     * Whether the query last built by {@link #createQuery()} selects distinct entities without the SQL keyword,
     * because it is ordered by a value that is not among the columns of the entity. Its results are then to be
     * made distinct once they are read, as Hibernate 5 did for a distinct list.
     *
     * @return whether the entities of the result are to be made distinct in memory
     */
    public boolean isDistinctInMemory() {
        return distinctInMemory;
    }

    public JpaCriteriaQuery<?> createQuery() {
        distinctInMemory = false;
        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);

        applyRequestedJoins(root, projectionList);

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) cq);
        assignGroupBy(cq, context);

        // the criteria come first, so that an order reuses the join the criteria have for an association
        assignCriteria(cq, root, context, entity);
        assignOrderBy(cq, context);
        return cq;
    }

    /**
     * Eagerly fetch-join the associations requested via {@code fetch:[assoc:'join']} (which a
     * dynamic finder turns into {@link HibernateQuery#join(String)} calls). Hibernate 5 initializes
     * these collections; without an explicit JPA {@code root.fetch(...)} they would only be plain
     * joins (or, when not referenced in a predicate, not materialized at all), leaving the
     * association uninitialized. Only applied to full-entity selects - a fetch join on a projection
     * query (for example {@code count}) is invalid.
     * <p>
     * An inner join that is requested for a to-one association decides which rows the query has, whether or not
     * anything refers to the association. A fetch join does that for a full-entity select. A count or another
     * projection query has no fetch join, so the association is joined with a plain join there, and a paged list
     * counts the rows that it returns.
     */
    private void applyRequestedJoins(Root<?> root, List<Query.Projection> projectionList) {
        Map<String, JoinType> joinTypes = detachedCriteria.getJoinTypes();
        Set<String> fetched = new HashSet<>();
        boolean entitySelect = projectionList.stream().noneMatch(p -> !(p instanceof Query.DistinctProjection));
        if (entitySelect && hibernateQuery != null) {
            for (String fetchPath : hibernateQuery.getFetchJoinPaths()) {
                if (fetchPath == null || fetchPath.indexOf('.') >= 0) {
                    continue;
                }
                JoinType joinType = joinTypes.getOrDefault(fetchPath, JoinType.LEFT);
                try {
                    root.fetch(fetchPath, joinType);
                    fetched.add(fetchPath);
                }
                catch (IllegalArgumentException ignored) {
                    // Not a fetchable association on this root; the plain join handling remains in place.
                }
            }
        }
        joinTypes.forEach((path, joinType) -> {
            if (joinType == JoinType.INNER && !fetched.contains(path) && path.indexOf('.') < 0 &&
                    entity.getPropertyByName(path) instanceof ToOne<?> toOne && !(toOne instanceof Embedded)) {
                context.resolveAssociation(path);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) subquery);

        assignGroupBy(subquery, context);

        assignCriteria(subquery, root, context, entity);
    }

    private List<Query.Projection> collectProjections() {
        return projections.getProjectionList();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Query.Projection> projections) {
        List<Query.Projection> expressionProjections = projections.stream()
                .filter(p -> !(p instanceof Query.DistinctProjection) && !(p instanceof SqlGroupProjection))
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
            } else if (first instanceof SqlProjection sqlProjection) {
                return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(sqlProjection.getType());
            }
            return (JpaCriteriaQuery<?>) criteriaBuilder.createQuery(entity.getJavaClass());
        }
    }

    private void assignGroupBy(AbstractQuery<?> query, JpaQueryContext context) {
        var translator = new JpaProjectionTranslator(criteriaBuilder, context, entity);
        var groupByExpressions = projections.getProjectionList().stream()
                .map(projection -> {
                    if (projection instanceof Query.GroupPropertyProjection groupPropertyProjection) {
                        return context.getFullyQualifiedExpression(groupPropertyProjection.getPropertyName());
                    } else if (projection instanceof SqlGroupProjection sqlGroupProjection) {
                        // the column alias of a SQL projection does not reach the SQL, so a group by clause that
                        // names one, quoted or not, groups by the SQL it stands for; as in standard SQL, a name in
                        // double quotes is case sensitive, while backquotes (MySQL) and square brackets (SQL Server)
                        // leave a column alias case insensitive
                        String groupBy = sqlGroupProjection.getSql().trim();
                        String name = SqlText.of(groupBy, SqlSyntax.forCriteriaBuilder(criteriaBuilder)).unquoted();
                        boolean caseSensitive = groupBy.startsWith("\"") && !name.equals(groupBy);
                        SqlProjection aliased = findSqlProjection(name, !caseSensitive);
                        return translator.translateSql(aliased != null ? aliased.getSql() : sqlGroupProjection.getSql(), null);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toArray(Expression[]::new);
        if (groupByExpressions.length > 0) {
            query.groupBy(groupByExpressions);
        }
    }

    /**
     * Returns the SQL projection whose column alias is the given name, or {@code null}.
     */
    private SqlProjection findSqlProjection(String name, boolean ignoreCase) {
        for (Query.Projection projection : projections.getProjectionList()) {
            if (projection instanceof SqlProjection sqlProjection && sqlProjection.getColumnAlias() != null &&
                    (ignoreCase ? sqlProjection.getColumnAlias().equalsIgnoreCase(name) : sqlProjection.getColumnAlias().equals(name))) {
                return sqlProjection;
            }
        }
        return null;
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
        if (isCountOnly()) {
            // a count returns one row whatever the order, and an ORDER BY on a column that is not aggregated fails
            // on H2 and most other databases, so a count query leaves the criteria's orders out
            return;
        }
        // the orders that are still in an association block come after the orders of the criteria
        List<Query.Order> orders = new ArrayList<>(detachedCriteria.getOrders());
        orders.addAll(AssociationOrders.inBlocks(detachedCriteria.getCriteria()));
        boolean distinctEntities = isDistinctEntities();
        boolean orderedBySelection = !distinctEntities && isOrderedBySelection();
        List<jakarta.persistence.criteria.Order> jpaOrders = new ArrayList<>();
        for (Query.Order order : orders) {
            var propertyName = order.getProperty();
            SqlProjection sqlProjection = findSqlProjection(propertyName, false);
            boolean selected = isSelected(propertyName);
            // an order of an association block, and the context that the criteria of its block were built against
            AssociationOrder blockOrder = order instanceof AssociationOrder associationOrder ? associationOrder : null;
            JpaQueryContext blockContext =
                    blockOrder != null ? context.getAssociationContext(blockOrder.getAssociation()) : null;
            Expression<?> expression;
            if (sqlProjection != null) {
                expression = context.getSelectionAlias(sqlProjection.getColumnAlias());
            } else if (blockOrder != null && orderedBySelection && !selected) {
                // the database would reject this order, and leaving it out would hide that the query is not sorted
                throw new IllegalArgumentException("Cannot sort by [" + propertyName +
                        "]: a distinct or grouped query can only be ordered by what it selects");
            } else if (selected && (orderedBySelection || blockOrder == null)) {
                // a distinct or grouped query can only be ordered by what it selects, so the order
                // sorts by the expression of the projection and not through a join of its own
                expression = context.getFullyQualifiedExpression(propertyName);
            } else if (blockContext != null) {
                // the order sorts by the joins that its own block made, which its path from the root does not
                // tell apart from the joins of another block of the same association
                String property = blockOrder.getAssociationProperty();
                PersistentEntity associated = blockOrder.getAssociation().getAssociation().getAssociatedEntity();
                expression = blockContext.getSortExpression(property, endsWithForeignKey(associated, property));
            } else {
                expression = context.getSortExpression(propertyName, endsWithForeignKey(entity, propertyName));
            }
            if (expression == null) {
                // an order that names nothing, in a block as anywhere else, is a mistake that ignoring would hide
                throw new IllegalArgumentException("Invalid " + HibernateQueryArgument.SORT.value() + " property");
            }
            if (distinctEntities && !context.isColumnOfRoot(expression)) {
                distinctInMemory = true;
            }
            boolean ascending = order.getDirection().equals(Query.Order.Direction.ASC);
            if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                Expression<String> lower = criteriaBuilder.lower((Expression<String>) expression);
                jpaOrders.add(ascending ? criteriaBuilder.asc(lower) : criteriaBuilder.desc(lower));
            } else {
                jpaOrders.add(ascending ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression));
            }
        }
        if (distinctInMemory) {
            // SQL only orders a distinct query by what it selects, and an order through a join is not among
            // the columns of the entity; without the keyword the entities are made distinct once they are read
            cq.distinct(false);
        }
        if (!jpaOrders.isEmpty()) {
            cq.orderBy(jpaOrders);
        }
    }

    /**
     * Whether the query selects distinct entities and nothing else, as {@code listDistinct} does.
     */
    private boolean isDistinctEntities() {
        List<Query.Projection> projectionList = projections.getProjectionList();
        return !projectionList.isEmpty() && projectionList.stream().allMatch(Query.DistinctProjection.class::isInstance);
    }

    /**
     * Whether the query selects distinct or grouped values, or aggregates, and not only plain properties. Most
     * databases reject an order of such a query by a value that it does not select.
     */
    private boolean isOrderedBySelection() {
        for (Query.Projection projection : projections.getProjectionList()) {
            if (!(projection instanceof Query.IdProjection) && projection.getClass() != Query.PropertyProjection.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a sort path from an entity ends with a foreign key: the identifier of an association that has its
     * key in the table before it on the path. A path that starts with an alias is not such a path.
     */
    private static boolean endsWithForeignKey(PersistentEntity from, String propertyName) {
        String[] tokens = propertyName.split("\\.");
        PersistentEntity current = from;
        for (int i = 0; i < tokens.length - 1 && current != null; i++) {
            if (!(current.getPropertyByName(tokens[i]) instanceof Association<?> association)) {
                return false;
            }
            if (i == tokens.length - 2) {
                return HqlListQueryBuilder.isForeignKey(association, tokens[i + 1]);
            }
            current = association.getAssociatedEntity();
        }
        return false;
    }

    /**
     * Whether the query selects what an order by the property sorts by: the property itself, or the association
     * or component that the property belongs to, as {@code distinct('club')} does for an order by
     * {@code club.name}. Such an order sorts by the expression of the projection, which is read from the join
     * that the projection has made.
     */
    private boolean isSelected(String propertyName) {
        int owner = propertyName.lastIndexOf('.');
        return isSelectedProperty(propertyName) || owner > 0 && isSelectedProperty(propertyName.substring(0, owner));
    }

    /**
     * Whether a projection of the query selects the property as it is, as {@code property()}, {@code distinct()}
     * and {@code groupProperty()} do, and not an aggregate of it.
     */
    private boolean isSelectedProperty(String propertyName) {
        for (Query.Projection projection : projections.getProjectionList()) {
            if (projection instanceof Query.PropertyProjection selected &&
                    !(projection instanceof Query.CountDistinctProjection || projection instanceof Query.AvgProjection ||
                            projection instanceof Query.MaxProjection || projection instanceof Query.MinProjection ||
                            projection instanceof Query.SumProjection)) {
                String selectedName = selected.getPropertyName();
                int separator = selectedName.indexOf(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR);
                if (propertyName.equals(separator < 0 ? selectedName : selectedName.substring(separator + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the query selects nothing but counts, as {@code count()} and {@code countDistinct()} do: such a query
     * has one row, so the criteria's orders do not apply to it. A query that groups and counts keeps its orders.
     */
    private boolean isCountOnly() {
        List<Query.Projection> projectionList = projections.getProjectionList();
        return !projectionList.isEmpty() && projectionList.stream()
                .allMatch(projection -> projection instanceof Query.CountProjection ||
                        projection instanceof Query.CountDistinctProjection);
    }

    private void assignCriteria(
            AbstractQuery<?> cq, Root<?> root, JpaQueryContext context, GrailsHibernatePersistentEntity entity) {
        List<Query.Criterion> criteriaList = detachedCriteria.getCriteria();
        if (!criteriaList.isEmpty()) {
            aliasRegistrar.discover(criteriaList, context);
            var predicateGenerator = new PredicateGenerator(criteriaBuilder, conversionService);
            var predicate = predicateGenerator.generate(cq, root, criteriaList, context, entity);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
    }
}
