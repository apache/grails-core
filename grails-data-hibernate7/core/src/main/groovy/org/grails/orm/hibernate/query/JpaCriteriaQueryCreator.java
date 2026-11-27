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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.core.convert.ConversionService;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
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
    private final Set<String> deferredCollectionFetches = new HashSet<>();
    // whether the list that a count is made for fetches a collection, so that Hibernate lists each instance once
    private boolean listsInstancesOnce;
    // the restrictions of the join types that the query requests for a path through a collection
    private final List<Predicate> requestedJoinRestrictions = new ArrayList<>();
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

        applyRequestedJoins(cq, root, projectionList.stream().noneMatch(p -> !(p instanceof Query.DistinctProjection)), isCountOnly());

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) cq);
        assignGroupBy(cq, context);

        // the criteria come first, so that an order reuses the join the criteria have for an association
        assignCriteria(cq, root, context, entity);
        assignOrderBy(cq, context, isCountOnly());
        return cq;
    }

    /**
     * Applies the joins that the query requests for an association, with the {@code fetch} argument or with
     * {@code join}. A fetch mode alone, and a requested left join, fetch the association with a left join, which
     * never leaves out an instance. Any other requested join type also decides which instances the query returns
     * and counts, whether or not anything else in the query uses the association:
     * <ul>
     * <li>For a path of associations to a single instance, every association on the path is joined, the last one
     * with the requested join type, so an inner join leaves out the instances for which the path ends early.</li>
     * <li>A path that ends in a collection, or passes through one, restricts the query to the instances for which
     * an inner join of the path returns a row, with a correlated subquery: a join through a collection would
     * return an instance once for each of its elements, and a collection fetched from such rows would lack the
     * elements that the rest of the path leaves out.</li>
     * </ul>
     * A query that selects entities fetches the associations on the path as well. The count of such a query joins
     * the paths that the list fetches in the same way, without fetching them, so that a sort reads the same joins,
     * and records whether the list fetches a collection. A path that names a property that is not an association is
     * left alone.
     *
     * @param countsList whether the query counts the instances that the query with the same criteria lists
     */
    private void applyRequestedJoins(AbstractQuery<?> query, Root<?> root, boolean entitySelect, boolean countsList) {
        requestedJoinRestrictions.clear();
        deferredCollectionFetches.clear();
        listsInstancesOnce = false;
        // a fetch mode that is requested after a join type replaces it, so the fetch strategies say what is joined
        Map<String, JoinType> requested = new LinkedHashMap<>();
        collectRequestedFetches(detachedCriteria, "", requested);
        Set<String> joinedCollections = new HashSet<>();
        requested.keySet().forEach(path -> collectCollectionPaths(path, joinedCollections));
        long bags = requested.keySet().stream().map(this::bagOnPath).filter(Objects::nonNull).distinct().count();
        // A collection fetch makes Hibernate deduplicate entity results, including unmatched null rows of a
        // right join. Load those collections separately, retaining the existing two-bag restriction.
        boolean deferForRightJoin = bags < 2 && requested.entrySet().stream().anyMatch(entry ->
                entry.getValue() == JoinType.RIGHT && firstCollection(entry.getKey().split("\\.")) == -1);
        for (Query.Order order : detachedCriteria.getOrders()) {
            int dot = order.getProperty().lastIndexOf('.');
            if (dot > 0) {
                collectCollectionPaths(order.getProperty().substring(0, dot), joinedCollections);
            }
        }

        Set<String> aliasedPaths = new HashSet<>();
        for (Query.Criterion criterion : detachedCriteria.getCriteria()) {
            if (criterion instanceof HibernateAlias alias && alias.explicit()) {
                aliasedPaths.add(alias.path());
            }
        }
        Map<String, Fetch<?, ?>> fetches = new HashMap<>();
        Map<String, From<?, ?>> projectionJoins = new HashMap<>();
        requested.forEach((path, joinType) -> {
            if (aliasedPaths.contains(path)) {
                // the join of an alias that the query declares, which the criteria that use the alias make
                return;
            }
            String[] tokens = path.split("\\.");
            int collection = firstCollection(tokens);
            if (collection == NOT_AN_ASSOCIATION_PATH) {
                return;
            }
            boolean restricts = joinType != JoinType.LEFT;
            if (restricts && collection >= 0) {
                requestedJoinRestrictions.add(joinedInSubquery(query, root, tokens));
            }
            String bag = bagOnPath(path);
            boolean deferred = deferForRightJoin && collection >= 0 ||
                    bags == 1 && bag != null && joinedCollections.stream().anyMatch(collectionPath -> !collectionPath.equals(bag));
            if (entitySelect) {
                if (deferred) {
                    deferredCollectionFetches.add(path);
                    return;
                }
                // a path through a collection is fetched with left joins, so that the collection holds all of its
                // elements; the subquery decides which instances the query returns
                fetchPath(root, tokens, collection, requested, fetches);
            }
            else if (collection < 0 || countsList && !deferred) {
                // A count joins what the list fetches. The list fetches a collection, so Hibernate lists each of its
                // instances once, and the count counts instances
                joinProjectionPath(root, tokens, collection, requested, projectionJoins);
                listsInstancesOnce |= countsList && collection >= 0;
            }
        });
    }

    private static final int NOT_AN_ASSOCIATION_PATH = -2;

    private static void collectRequestedFetches(DetachedCriteria<?> owner, String prefix, Map<String, JoinType> requested) {
        CriteriaVisitor.walk(owner.getCriteria(), new CriteriaVisitor() {
            @Override
            public void block(DetachedAssociationCriteria<?> block) {
                collectRequestedFetches(block, prefix + block.getAssociationPath() + '.', requested);
            }
        });
        owner.getFetchStrategies().forEach((path, fetchType) -> {
            String wholePath = prefix + path;
            if (fetchType == FetchType.EAGER) {
                requested.put(wholePath, owner.getJoinTypes().getOrDefault(path, JoinType.LEFT));
            }
            else {
                requested.remove(wholePath);
            }
        });
    }

    Set<String> getDeferredCollectionFetches() {
        return Collections.unmodifiableSet(deferredCollectionFetches);
    }

    private void collectCollectionPaths(String path, Set<String> paths) {
        PersistentEntity current = entity;
        String prefix = "";
        for (String token : path.split("\\.")) {
            if (current == null || !(current.getPropertyByName(token) instanceof Association<?> association)) {
                return;
            }
            prefix = prefix.isEmpty() ? token : prefix + '.' + token;
            if (association instanceof ToMany) {
                paths.add(prefix);
            }
            current = association.getAssociatedEntity();
        }
    }

    private String bagOnPath(String path) {
        PersistentEntity current = entity;
        String prefix = "";
        for (String token : path.split("\\.")) {
            if (current == null || !(current.getPropertyByName(token) instanceof Association<?> association)) {
                return null;
            }
            prefix = prefix.isEmpty() ? token : prefix + '.' + token;
            if (association instanceof ToMany && association.getType() == Collection.class) {
                return prefix;
            }
            current = association.getAssociatedEntity();
        }
        return null;
    }

    /**
     * Returns the index of the first collection on a path of associations from the entity, {@code -1} when the
     * path has none, or {@link #NOT_AN_ASSOCIATION_PATH} when the path names something else than associations.
     */
    private int firstCollection(String[] tokens) {
        PersistentEntity current = entity;
        int collection = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (current == null || !(current.getPropertyByName(tokens[i]) instanceof Association<?> association)) {
                return NOT_AN_ASSOCIATION_PATH;
            }
            if (association instanceof Embedded) {
                // a component has no join of its own; a path goes through it to an association that it holds
                if (i == tokens.length - 1) {
                    return NOT_AN_ASSOCIATION_PATH;
                }
                current = association.getAssociatedEntity();
                continue;
            }
            if (association instanceof ToMany && collection < 0) {
                collection = i;
            }
            if (association instanceof Basic) {
                // a collection of a basic type has no entity to go on from
                return i == tokens.length - 1 ? collection : NOT_AN_ASSOCIATION_PATH;
            }
            current = association.getAssociatedEntity();
        }
        return collection;
    }

    /**
     * Fetches each to-one prefix with its own requested type, or LEFT when none was requested, independently of
     * which path reaches it first. From the first collection onwards, LEFT keeps every fetched element.
     */
    private void fetchPath(Root<?> root, String[] tokens, int collection, Map<String, JoinType> requested, Map<String, Fetch<?, ?>> fetches) {
        FetchParent<?, ?> parent = root;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            path.append(i == 0 ? "" : ".").append(tokens[i]);
            Fetch<?, ?> fetch = fetches.get(path.toString());
            if (fetch == null) {
                try {
                    JoinType type = collection < 0 || i < collection ? requested.getOrDefault(path.toString(), JoinType.LEFT) : JoinType.LEFT;
                    fetch = parent.fetch(tokens[i], type);
                }
                catch (IllegalArgumentException ignored) {
                    // not a fetchable association; the plain join handling remains in place
                    return;
                }
                fetches.put(path.toString(), fetch);
            }
            parent = fetch;
        }
    }

    /**
     * A projection and a count join a path as the entity fetch does: each to-one prefix with its requested type, and
     * from the first collection onwards with left joins.
     */
    private static void joinProjectionPath(Root<?> root, String[] tokens, int collection, Map<String, JoinType> requested, Map<String, From<?, ?>> joins) {
        From<?, ?> parent = root;
        String path = "";
        for (int i = 0; i < tokens.length; i++) {
            path = path.isEmpty() ? tokens[i] : path + '.' + tokens[i];
            From<?, ?> join = joins.get(path);
            if (join == null) {
                JoinType type = collection < 0 || i < collection ? requested.getOrDefault(path, JoinType.LEFT) : JoinType.LEFT;
                join = parent.join(tokens[i], type);
                joins.put(path, join);
            }
            parent = join;
        }
    }

    /**
     * Returns the restriction to the instances for which an inner join of an association path returns a row.
     */
    private Predicate joinedInSubquery(AbstractQuery<?> query, Root<?> root, String[] tokens) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<?> correlated = subquery.from(entity.getJavaClass());
        From<?, ?> from = correlated;
        for (String token : tokens) {
            from = from.join(token, JoinType.INNER);
        }
        subquery.select(criteriaBuilder.literal(1)).where(criteriaBuilder.equal(correlated, root));
        return criteriaBuilder.exists(subquery);
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

        // a subquery fetches nothing, so the joins that it requests only decide which rows it selects
        applyRequestedJoins(subquery, root, false, false);

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
            } else {
                expression = context.getSortExpression(propertyName, endsWithForeignKey(entity, propertyName, context), countOnly);
            }
            if (countOnly) {
                // Keep the joins of the sort without ordering the aggregate: they decide the rows that the
                // list pages through, so the count has a row for each element of a collection that it sorts by
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
            if (listsInstancesOnce && cq != null && projections.getProjectionList().size() == 1 &&
                    projections.getProjectionList().get(0).getClass() == Query.CountProjection.class) {
                // The list fetches a collection, and Hibernate lists each instance of such a query once, so its total
                // counts instances and not the rows of the joins
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
                requestedJoinRestrictions.add(0, predicate);
            }
        }
        if (!requestedJoinRestrictions.isEmpty()) {
            cq.where(requestedJoinRestrictions.toArray(new Predicate[0]));
            requestedJoinRestrictions.clear();
        }
    }
}
