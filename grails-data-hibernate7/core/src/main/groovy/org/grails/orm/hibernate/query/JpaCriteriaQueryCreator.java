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

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
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
    private boolean sortedByCollection;

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
        this.aliasRegistrar = new QueryAliasRegistrar(detachedCriteria, hibernateQuery);
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

    /**
     * Whether the query last built by {@link #createQuery()} lists entities sorted by a property of a
     * collection, so that it has a row for each element of the collection.
     *
     * @return whether an order of the query reads a collection
     */
    public boolean isSortedByCollection() {
        return sortedByCollection;
    }

    public JpaCriteriaQuery<?> createQuery() {
        distinctInMemory = false;
        sortedByCollection = false;
        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);
        context.setLeftJoinSort(entity.getMappingContext() instanceof HibernateMappingContext mappingContext &&
                mappingContext.isLeftJoinAssociationSort());

        applyRequestedJoins(root, projectionList);

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) cq);
        assignGroupBy(cq, context);

        // the criteria come first, so that an order reuses the join the criteria have for an association
        assignCriteria(cq, root, context, entity);
        assignOrderBy(cq, context, isCountOnly());
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
        applyRequestedInnerJoins(fetched);
    }

    /**
     * Joins the to-one associations that the query requests an inner join for and has not fetched.
     */
    private void applyRequestedInnerJoins(Set<String> fetched) {
        detachedCriteria.getJoinTypes().forEach((path, joinType) -> {
            if (joinType == JoinType.INNER && !fetched.contains(path) && path.indexOf('.') < 0 &&
                    entity.getPropertyByName(path) instanceof ToOne<?> toOne && !(toOne instanceof Embedded)) {
                context.resolveAssociation(path);
            }
        });
    }

    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        populateSubquery(subquery, false);
    }

    /**
     * Populates the subquery that the rows of a query with projections are counted from. Unlike a subquery of
     * the criteria, it has the inner joins that the query requests and the joins of its sort, which decide the
     * rows that the query lists, so that the count is the number of those rows.
     *
     * @param subquery the subquery to count the rows of
     */
    public <T> void populateCountSubquery(JpaSubQuery<T> subquery) {
        populateSubquery(subquery, true);
    }

    @SuppressWarnings("unchecked")
    private <T> void populateSubquery(JpaSubQuery<T> subquery, boolean counted) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);
        context.setLeftJoinSort(entity.getMappingContext() instanceof HibernateMappingContext mappingContext &&
                mappingContext.isLeftJoinAssociationSort());

        if (counted) {
            applyRequestedInnerJoins(Collections.emptySet());
        }

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) subquery);

        assignGroupBy(subquery, context);

        assignCriteria(subquery, root, context, entity);
        if (counted) {
            assignOrderBy(null, context, true);
        }
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
    private void assignOrderBy(CriteriaQuery<?> cq, JpaQueryContext context, boolean countOnly) {
        // A count makes the joins of the sort, to count the rows that the list pages through, and joins a collection
        // that only the sort uses. A list that fetches a collection returns each instance once, so the count of its
        // instances counts them once, and the fetch join of the collection, which the sort of the list goes through,
        // leaves out no instance. The count of a query with projections has no fetch
        Set<String> fetchPaths = countOnly && cq != null && fetchesCollection() ? fetchPaths() : Collections.emptySet();
        boolean joinedCollection = false;
        List<Query.Order> orders = new ArrayList<>(detachedCriteria.getOrders());
        if (countOnly && hibernateQuery != null) {
            // the orders that the count cleared still join what they sort by
            orders.addAll(hibernateQuery.getClearedOrders());
        }
        boolean distinctEntities = isDistinctEntities();
        List<jakarta.persistence.criteria.Order> jpaOrders = new ArrayList<>();
        for (Query.Order order : orders) {
            var propertyName = order.getProperty();
            SqlProjection sqlProjection = findSqlProjection(propertyName, false);
            Expression<?> expression;
            if (sqlProjection != null) {
                expression = context.getSelectionAlias(sqlProjection.getColumnAlias());
            } else if (isSelected(propertyName)) {
                // a distinct or grouped query can only be ordered by what it selects, so an order by what a
                // projection selects sorts by the expression of the projection and not through a join of its own
                expression = context.getFullyQualifiedExpression(propertyName);
            } else if (sortsThroughFetchedCollection(propertyName, fetchPaths)) {
                // the list sorts through the fetch join, which leaves out no instance
                continue;
            } else {
                if (countOnly && cq != null && isEntityCount()) {
                    joinFetchedAssociation(context.getRoot(), propertyName);
                }
                expression = context.getSortExpression(propertyName, endsWithForeignKey(entity, propertyName, context), countOnly);
            }
            if (countOnly) {
                // Keep the joins of the sort without ordering the aggregate: they decide the rows that the
                // list pages through, so the count has a row for each element of a collection that it sorts by
                joinedCollection |= readsCollection(expression);
                continue;
            }
            if (expression == null) {
                // an order that names nothing is a mistake that ignoring would hide
                throw new IllegalArgumentException("Invalid " + HibernateQueryArgument.SORT.value() + " property");
            }
            if (distinctEntities && !context.isColumnOfRoot(expression)) {
                distinctInMemory = true;
            }
            sortedByCollection |= projections.getProjectionList().isEmpty() && readsCollection(expression);
            boolean ascending = order.getDirection().equals(Query.Order.Direction.ASC);
            if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                Expression<String> lower = criteriaBuilder.lower((Expression<String>) expression);
                jpaOrders.add(ascending ? criteriaBuilder.asc(lower) : criteriaBuilder.desc(lower));
            } else {
                jpaOrders.add(ascending ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression));
            }
        }
        if (countOnly) {
            if (!fetchPaths.isEmpty() && joinedCollection && isEntityCount()) {
                // the list fetches a collection, so it has each instance once, whatever rows the sort joins
                ((CriteriaQuery<Long>) cq).select(criteriaBuilder.countDistinct(context.getRoot()));
            }
            // a count has the joins of its orders and no order
            return;
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
     * Whether the query requests a fetch or a join type for a collection of the queried class.
     */
    private boolean fetchesCollection() {
        for (String path : fetchPaths()) {
            int dot = path == null ? -1 : path.indexOf('.');
            String first = path == null ? null : dot < 0 ? path : path.substring(0, dot);
            if (first != null && entity.getPropertyByName(first) instanceof ToMany<?>) {
                return true;
            }
        }
        return false;
    }

    /**
     * The association paths that the query requests a fetch or a join type for.
     */
    private Set<String> fetchPaths() {
        Set<String> paths = new HashSet<>(detachedCriteria.getJoinTypes().keySet());
        if (hibernateQuery != null) {
            paths.addAll(hibernateQuery.getFetchJoinPaths());
        }
        // the query that counts for a page is a copy, which has the fetches of the criteria only
        detachedCriteria.getFetchStrategies().forEach((path, fetchType) -> {
            if (fetchType == FetchType.EAGER) {
                paths.add(path);
            }
        });
        return paths;
    }

    /**
     * Whether a sort path from the queried class goes through a collection that the query fetches. The list sorts
     * through the fetch join of the collection, which keeps the instances whose collection is empty.
     */
    private boolean sortsThroughFetchedCollection(String propertyName, Set<String> fetchPaths) {
        if (fetchPaths.isEmpty()) {
            return false;
        }
        String[] tokens = propertyName.split("\\.");
        PersistentEntity current = entity;
        String path = null;
        for (int i = 0; i < tokens.length - 1 && current != null; i++) {
            if (!(current.getPropertyByName(tokens[i]) instanceof Association<?> association)) {
                return false;
            }
            path = path == null ? tokens[i] : path + '.' + tokens[i];
            if (association instanceof ToMany<?> && fetchPaths.contains(path)) {
                return true;
            }
            current = association.getAssociatedEntity();
        }
        return false;
    }

    /**
     * Joins the association to a single instance that a sort path of a count starts with, when the list that is
     * counted fetches the association with a left join. The sort of the list reads the association through that
     * fetch join, which keeps the instances whose association is {@code null}, so the count joins it with a left join
     * as well, unless the criteria have joined it already: the sort of the list then reads the join of the criteria.
     */
    private void joinFetchedAssociation(From<?, ?> root, String propertyName) {
        int dot = propertyName.indexOf('.');
        if (dot < 0 || hibernateQuery == null) {
            return;
        }
        String path = propertyName.substring(0, dot);
        if (hibernateQuery.getFetchJoinPaths().contains(path) &&
                detachedCriteria.getJoinTypes().getOrDefault(path, JoinType.LEFT) == JoinType.LEFT &&
                entity.getPropertyByName(path) instanceof ToOne<?> toOne && !(toOne instanceof Embedded) &&
                root.getJoins().stream().noneMatch(join -> path.equals(join.getAttribute().getName()))) {
            root.join(path, JoinType.LEFT);
        }
    }

    /**
     * Whether the query counts the instances of the queried class, as {@code count()} does, and nothing else.
     */
    private boolean isEntityCount() {
        List<Query.Projection> projectionList = projections.getProjectionList();
        return projectionList.size() == 1 && projectionList.get(0).getClass() == Query.CountProjection.class;
    }

    /**
     * Whether an expression is read from a collection, through a join of the collection or through its path.
     */
    private static boolean readsCollection(Expression<?> expression) {
        Path<?> path = expression instanceof Path<?> p ? p : null;
        while (path != null) {
            if (path.getModel() instanceof Attribute<?, ?> attribute && attribute.isCollection()) {
                return true;
            }
            path = path instanceof Join<?, ?> join ? join.getParent() : path.getParentPath();
        }
        return false;
    }

    /**
     * Whether the query selects distinct entities and nothing else, as {@code listDistinct} does.
     */
    private boolean isDistinctEntities() {
        List<Query.Projection> projectionList = projections.getProjectionList();
        return !projectionList.isEmpty() && projectionList.stream().allMatch(Query.DistinctProjection.class::isInstance);
    }

    /**
     * Whether a sort path from an entity ends with a foreign key: the identifier of an association that has its
     * key in the table before it on the path. An explicit alias starts at its associated entity.
     */
    private static boolean endsWithForeignKey(PersistentEntity from, String propertyName, JpaQueryContext context) {
        String[] tokens = propertyName.split("\\.");
        PersistentEntity current = from;
        int first = 0;
        HibernateAlias alias = context.getAliasRegistry().getDefinition(tokens[0]);
        if (alias != null && alias.explicit()) {
            Expression<?> expression = context.getAliasedExpression(tokens[0]);
            current = expression == null ? null : from.getMappingContext().getPersistentEntity(expression.getJavaType().getName());
            first = 1;
        }
        for (int i = first; i < tokens.length - 1 && current != null; i++) {
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
