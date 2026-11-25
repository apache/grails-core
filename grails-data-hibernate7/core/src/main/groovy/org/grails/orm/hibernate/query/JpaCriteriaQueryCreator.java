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
    // the restrictions of the join types that the query requests for a path through a collection
    private final List<Predicate> requestedJoinRestrictions = new ArrayList<>();

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

    public JpaCriteriaQuery<?> createQuery() {
        distinctInMemory = false;
        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = cq.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);
        context.setLeftJoinSort(entity.getMappingContext() instanceof HibernateMappingContext mappingContext &&
                mappingContext.isLeftJoinAssociationSort());

        applyRequestedJoins(cq, root, projectionList.stream().noneMatch(p -> !(p instanceof Query.DistinctProjection)));

        new JpaProjectionAdapter(criteriaBuilder, context, entity).adapt(projections, (AbstractQuery<?>) cq);
        assignGroupBy(cq, context);

        // the criteria come first, so that an order reuses the join the criteria have for an association
        assignCriteria(cq, root, context, entity);
        assignOrderBy(cq, context);
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
     * A query that selects entities fetches the associations on the path as well. A path that names a property
     * that is not an association is left alone.
     */
    private void applyRequestedJoins(AbstractQuery<?> query, Root<?> root, boolean entitySelect) {
        requestedJoinRestrictions.clear();
        deferredCollectionFetches.clear();
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
            if (entitySelect) {
                String bag = bagOnPath(path);
                if (deferForRightJoin && collection >= 0 ||
                        bags == 1 && bag != null && joinedCollections.stream().anyMatch(collectionPath -> !collectionPath.equals(bag))) {
                    deferredCollectionFetches.add(path);
                    return;
                }
                // a path through a collection is fetched with left joins, so that the collection holds all of its
                // elements; the subquery decides which instances the query returns
                fetchPath(root, tokens, restricts && collection < 0 ? joinType : JoinType.LEFT, fetches);
            }
            else if (collection < 0) {
                joinProjectionPath(root, tokens, joinType, projectionJoins);
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
            if (current == null || !(current.getPropertyByName(tokens[i]) instanceof Association<?> association) ||
                    association instanceof Embedded) {
                return NOT_AN_ASSOCIATION_PATH;
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
     * Fetches every association on a path, the last one with the given join type and the ones before it with a
     * left join. An association that an earlier path already fetches is fetched once.
     */
    private void fetchPath(Root<?> root, String[] tokens, JoinType lastJoinType, Map<String, Fetch<?, ?>> fetches) {
        FetchParent<?, ?> parent = root;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            path.append(i == 0 ? "" : ".").append(tokens[i]);
            Fetch<?, ?> fetch = fetches.get(path.toString());
            if (fetch == null) {
                try {
                    fetch = parent.fetch(tokens[i], i == tokens.length - 1 ? lastJoinType : JoinType.LEFT);
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

    /** A projection uses the same to-one join chain as the entity fetch, including its left-joined prefixes. */
    private static void joinProjectionPath(Root<?> root, String[] tokens, JoinType lastJoinType, Map<String, From<?, ?>> joins) {
        From<?, ?> parent = root;
        String path = "";
        for (int i = 0; i < tokens.length; i++) {
            path = path.isEmpty() ? tokens[i] : path + '.' + tokens[i];
            From<?, ?> join = joins.get(path);
            if (join == null) {
                join = parent.join(tokens[i], i == tokens.length - 1 ? lastJoinType : JoinType.LEFT);
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

    @SuppressWarnings("unchecked")
    public <T> void populateSubquery(JpaSubQuery<T> subquery) {
        var projectionList = collectProjections();
        Class<?> javaClass = entity.getJavaClass();
        Root<?> root = subquery.from(javaClass);

        context = aliasRegistrar.createContext(parentContext, root);
        context.setLeftJoinSort(entity.getMappingContext() instanceof HibernateMappingContext mappingContext &&
                mappingContext.isLeftJoinAssociationSort());

        // a subquery fetches nothing, so the joins that it requests only decide which rows it selects
        applyRequestedJoins(subquery, root, false);

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
        boolean countOnly = isCountOnly();
        // the orders that are still in an association block come after the orders of the criteria
        List<Query.Order> orders = new ArrayList<>(detachedCriteria.getOrders());
        orders.addAll(AssociationOrders.inBlocks(detachedCriteria.getCriteria()));
        boolean distinctEntities = isDistinctEntities();
        boolean orderedBySelection = !countOnly && !distinctEntities && isOrderedBySelection();
        List<jakarta.persistence.criteria.Order> jpaOrders = new ArrayList<>();
        boolean collectionSort = false;
        for (Query.Order order : orders) {
            var propertyName = order.getProperty();
            // an order of an association block, and the context that the criteria of its block were built against
            AssociationOrder blockOrder = order instanceof AssociationOrder associationOrder ? associationOrder : null;
            JpaQueryContext blockContext =
                    blockOrder != null ? context.getAssociationContext(blockOrder.getAssociation()) : null;
            if (blockContext != null) {
                String blockProperty = blockOrder.getAssociationProperty();
                int dot = blockProperty.indexOf('.');
                if (dot > 0) {
                    String alias = blockProperty.substring(0, dot);
                    Expression<?> aliased = context.getAliasedExpression(alias);
                    if (aliased != null && aliased == blockContext.getAliasedExpression(alias)) {
                        // An enclosing alias names the same expression from either scope, without the block prefix.
                        propertyName = blockProperty;
                    }
                }
            }
            SqlProjection sqlProjection = findSqlProjection(propertyName, false);
            boolean selected = isSelected(propertyName);
            Expression<?> expression;
            if (sqlProjection != null) {
                expression = context.getSelectionAlias(sqlProjection.getColumnAlias());
            } else if (blockOrder != null && orderedBySelection && !selected) {
                // the order of an association block had no effect while it stayed in its block, and the
                // database would reject it here, so it is left out as before
                continue;
            } else if (selected && (orderedBySelection || blockOrder == null)) {
                // a distinct or grouped query can only be ordered by what it selects, so the order
                // sorts by the expression of the projection and not through a join of its own
                expression = context.getFullyQualifiedExpression(propertyName);
            } else if (blockContext != null) {
                // the order sorts by the joins that its own block made, which its path from the root does not
                // tell apart from the joins of another block of the same association
                String property = blockOrder.getAssociationProperty();
                PersistentEntity associated = blockOrder.getAssociation().getAssociation().getAssociatedEntity();
                expression = blockContext.getSortExpression(property, endsWithForeignKey(associated, property, blockContext));
            } else {
                expression = context.getSortExpression(propertyName, endsWithForeignKey(entity, propertyName, context));
            }
            if (countOnly) {
                // Keep the joins of the sort, which can restrict the rows, without ordering the aggregate.
                collectionSort |= crossesCollection(expression);
                continue;
            }
            if (expression == null) {
                if (blockOrder != null) {
                    // an order of an association block that names nothing has no effect, as before
                    continue;
                }
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
        if (collectionSort && projections.getProjectionList().size() == 1 &&
                projections.getProjectionList().getFirst().getClass() == Query.CountProjection.class) {
            // A collection sort can multiply rows, but an entity list returns each root once.
            ((CriteriaQuery<Long>) cq).select(criteriaBuilder.countDistinct(context.getRoot()));
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

    private static boolean crossesCollection(Expression<?> expression) {
        for (Path<?> path = expression instanceof Path<?> p ? p : null; path != null; path = path.getParentPath()) {
            if (path instanceof Join<?, ?> join && join.getAttribute().isCollection()) {
                return true;
            }
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
