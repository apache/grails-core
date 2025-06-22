/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query;

import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.hibernate.NonUniqueResultException;
import org.hibernate.SessionFactory;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.transform.ResultTransformer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
@Slf4j
public abstract class AbstractHibernateQuery extends Query {

    public static final String SIZE_CONSTRAINT_PREFIX = "Size";

    protected static final String ALIAS = "_alias";
    protected static ConversionService conversionService = new DefaultConversionService();

    private static final Map<String, Boolean> JOIN_STATUS_CACHE = new ConcurrentHashMap<String, Boolean>();

    protected String alias;
    protected int aliasCount;
    protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<String, CriteriaAndAlias>();
    protected LinkedList<String> aliasStack = new LinkedList<String>();
    protected LinkedList<PersistentEntity> entityStack = new LinkedList<PersistentEntity>();
    protected LinkedList<Association> associationStack = new LinkedList<Association>();
    protected LinkedList aliasInstanceStack = new LinkedList();
    private boolean hasJoins = false;
    protected DetachedCriteria detachedCriteria;
    protected ProxyHandler proxyHandler = new HibernateProxyHandler();
    protected ResultTransformer resultTransformer;

    protected AbstractHibernateQuery(AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
        this.detachedCriteria = new DetachedCriteria(entity.getJavaClass());
    }

    public void setDetachedCriteria(DetachedCriteria detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
    }

    public void setResultTransformer(ResultTransformer resultTransformer) {
        this.resultTransformer = resultTransformer;
    }


    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value;
    }


    @Override
    public Query isEmpty(String property) {
        detachedCriteria.isEmpty(property);
        return this;
    }

    @Override
    public Query isNotEmpty(String property) {
        detachedCriteria.isNotEmpty(property);
        return this;
    }

    @Override
    public Query isNull(String property) {
        detachedCriteria.isNull(property);
        return this;
    }

    @Override
    public Query isNotNull(String property) {
        detachedCriteria.isNotNull(property);
        return this;
    }



    @Override
    public PersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.getLast();
        }
        return super.getEntity();
    }


    private String getAssociationPath(String propertyName) {
        if(propertyName.indexOf('.') > -1) {
            return propertyName;
        }
        else {

            StringBuilder fullPath = new StringBuilder();
            for (Association association : associationStack) {
                fullPath.append(association.getName());
                fullPath.append('.');
            }
            fullPath.append(propertyName);
            return fullPath.toString();
        }
    }

    /**
     * This is called for ANDS and is only used by DymamicFinder
     * It has the limitation of only one OR operator per Query
     * @param criterion The criterion instance
     */
    public void add(Criterion criterion) {
        detachedCriteria.add(criterion);
    }


    /**
     * This is called for ORS and is only used by DymamicFinder
     * It has the limitation of only one OR operator per Query
     * @param criterion The criterion instance
     */
    public void add(Junction currentJunction, Criterion criterion) {
        Disjunction disjunction = (Disjunction)  detachedCriteria.getCriteria()
                .stream()
                .filter(it -> it instanceof Disjunction)
                .findFirst()
                .orElse(new Disjunction());
        disjunction.add(criterion);
        detachedCriteria.add(disjunction);
    }

    @Override
    public Query eq(String property, Object value) {
        detachedCriteria.eq(property, value);
        return this;
    }

    @Override
    public Query idEq(Object value) {
        detachedCriteria.idEq(value);
        return this;
    }

    @Override
    public Query gt(String property, Object value) {
        detachedCriteria.gt(property, value);
        return this;
    }

    @Override
    public Query and(Criterion a, Criterion b) {
        and(List.of(a,b));
        return this;
    }

    public Query and(List<Criterion> criteria) {
        var conjunction = new Conjunction();
        criteria.forEach(conjunction::add);
        detachedCriteria.add(conjunction);
        return this;
    }

    public Query and(Closure closure) {
        detachedCriteria.and(closure);
        return this;
    }


    @Override
    public Query or(Criterion a, Criterion b) {
        or(List.of(a,b));
        return this;
    }

    public Query or(List<Criterion> criteria) {
        var disjunction = new Disjunction();
        criteria.forEach(disjunction::add);
        detachedCriteria.add(disjunction);
        return this;
    }


    public Query or(Closure closure) {
        detachedCriteria.or(closure);
        return this;
    }

    public Query not(Criterion a) {
        not(new Closure(AbstractHibernateQuery.this) {
            public void doCall() {
                ((DetachedCriteria) getDelegate()).add(a);
            }
        });
        return this;
    }

    public Query not(List<Criterion> criteria) {
        var conjunction = new Conjunction();
        criteria.forEach(conjunction::add);
        var negation = new Negation();
        negation.add(conjunction);
        detachedCriteria.add(negation);
        return this;
    }


    public Query not(Closure closure) {
        detachedCriteria.not(closure);
        return this;
    }

    @Override
    public Query allEq(Map<String, Object> values) {
        values.forEach((key, value) -> {
            detachedCriteria.eq(key,value);
        });
        return this;
    }

    @Override
    public Query ge(String property, Object value) {
        detachedCriteria.ge(property, value);
        return this;
    }

    @Override
    public Query le(String property, Object value) {
        detachedCriteria.le(property, value);
        return this;
    }

    @Override
    public Query gte(String property, Object value) {
        detachedCriteria.gte(property, value);
        return this;
    }

    @Override
    public Query lte(String property, Object value) {
        detachedCriteria.lte(property, value);
        return this;
    }

    @Override
    public Query lt(String property, Object value) {
        detachedCriteria.lt(property, value);
        return this;
    }

    @Override
    public Query in(String property, List values) {
        detachedCriteria.in(property,values);
        return this;
    }

    @Override
    public Query between(String property, Object start, Object end) {
        detachedCriteria.between(property,start,end);
        return this;
    }

    @Override
    public Query like(String property, String expr) {
        detachedCriteria.like(property, expr);
        return this;
    }

    @Override
    public Query ilike(String property, String expr) {
        detachedCriteria.ilike(property, expr);
        return this;
    }

    @Override
    public Query rlike(String property, String expr) {
        throw new UnsupportedOperationException("Needs RLIKE extension");
//        detachedCriteria.rlike(property, expr);
//        return this;
    }


    //TODO THIS IS USED BY AbstractCriteriaBuilder which is not the parent of
    // AbstractHibernateCriteriaBuilder
    @Override
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByName(calculatePropertyName(associationName));
        if (property != null && (property instanceof Association)) {
            String alias = generateAlias(associationName);
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias);

            Association association = (Association) property;
            if(subCriteria.criteria != null) {
                return new HibernateAssociationQuery(subCriteria.criteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, alias);
            }
        }
        throw new InvalidDataAccessApiUsageException("Cannot query association [" + calculatePropertyName(associationName) + "] of entity [" + entity + "]. Property is not an association!");
    }


    private CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        CriteriaAndAlias subCriteria = null;
        String associationPath = getAssociationPath(associationName);
        CriteriaQuery parentCriteria = getCriteriaBuilder().createQuery(entity.getJavaClass());
        if(alias == null) {
            alias = generateAlias(associationName);
        }
        else {
            CriteriaAndAlias criteriaAndAlias = createdAssociationPaths.get(alias);
            if(criteriaAndAlias != null) {
                parentCriteria = criteriaAndAlias.criteria;
                if(parentCriteria != null) {

                    alias = associationName + '_' + alias;
                    associationPath = criteriaAndAlias.associationPath + '.' + associationPath;
                }
            }
        }
        if (createdAssociationPaths.containsKey(associationName)) {
            subCriteria = createdAssociationPaths.get(associationName);
        }
        else {
            JoinType joinType = joinTypes.get(associationName);
            if(parentCriteria != null) {
//                Criteria sc = parentCriteria.createAlias(associationPath, alias, resolveJoinType(joinType));
//                subCriteria = new CriteriaAndAlias(sc, alias, associationPath);
            }
            if(subCriteria != null) {

                createdAssociationPaths.put(associationPath,subCriteria);
                createdAssociationPaths.put(alias,subCriteria);
            }
        }
        return subCriteria;
    }




    @Override
    public Query firstResult(int offset) {
        offset(offset);
        return this;
    }

    @Override
    public Query cache(boolean cache) {
        return super.cache(cache);
    }

    @Override
    public Query lock(boolean lock) {
        return super.lock(lock);
    }

    @Override
    public Query order(Order order) {
        detachedCriteria.order(order);
        return this;
    }

    @Override
    public Query join(String property) {
        detachedCriteria.join(property);
        return this;
    }

    @Override
    public Query join(String property, JoinType joinType) {
        detachedCriteria.join(property,joinType);
        return this;
    }

    @Override
    public Query select(String property) {
        detachedCriteria.select(property);
        return this;
    }

    @Override
    public List list() {
        return createQuery().getResultList();
    }



    @Override
    protected void flushBeforeQuery() {
        // do nothing
    }

    @Override
    public Object singleResult() {
        org.hibernate.query.Query query = createQuery();
        try {

            Object singleResult = query.getSingleResult();
            return proxyHandler.unwrap(singleResult);
        }
        catch (NonUniqueResultException e) {
            return proxyHandler.unwrap(query.getResultList().get(0));
        }
        catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    private final Predicate<Projection> idProjectionPredicate = projection -> projection instanceof IdProjection;
    private final Predicate<Projection> distinctProjectionPredicate = projection -> projection instanceof DistinctProjection;
    private final Predicate<Projection> countProjectionPredicate = projection -> projection instanceof CountProjection;
    private final Predicate<Projection> countDistinctProjection = projection -> projection instanceof CountDistinctProjection;
    private final Predicate<Projection> maxProjectionPredicate = projection -> projection instanceof MaxProjection;
    private final Predicate<Projection> minProjectionPredicate = projection -> projection instanceof MinProjection;
    private final Predicate<Projection> sumProjectionPredicate = projection -> projection instanceof SumProjection;
    private final Predicate<Projection> avgProjectionPredicate = projection -> projection instanceof AvgProjection;
    private final Predicate<Projection> propertyProjectionPredicate = projection -> projection instanceof PropertyProjection;

    @SuppressWarnings("unchecked")
    Predicate<Projection>[] projectionPredicates = new Predicate[] {
            idProjectionPredicate
            , propertyProjectionPredicate
            , countProjectionPredicate
            , countDistinctProjection
            , maxProjectionPredicate
            , minProjectionPredicate
            , sumProjectionPredicate
            , avgProjectionPredicate
            , distinctProjectionPredicate
    } ;

    @SafeVarargs
    private static <T> Predicate<T> combinePredicates(Predicate<T>... predicates) {
        return Arrays.stream(predicates)
                .reduce(Predicate::or)
                .orElse(x -> true);
    }

    protected org.hibernate.query.Query createQuery() {

        var projectionList = collectProjections();
        var cq = createCriteriaQuery(projectionList);
        From root = cq.from(entity.getJavaClass());
        var tablesByName = new JpaFromProvider(detachedCriteria,cq,root);


        assignProjections(projectionList, cq, tablesByName);

        List<GroupPropertyProjection> groupProjections = collectGroupProjections();
        assignGroupBy(groupProjections, root, cq, tablesByName);
        var cb = getCriteriaBuilder();
        assignOrderBy(cq, tablesByName);
        assignCriteria(cq, cb, root,tablesByName);

        org.hibernate.query.Query query = getSessionFactory()
                .getCurrentSession()
                .createQuery(cq)
                .setFirstResult(this.offset)
                .setHint("org.hibernate.cacheable", queryCache);;
        if (this.max > -1) {
            query.setMaxResults(this.max);
        }
        if (Objects.nonNull(lockResult)) {
            query.setLockMode(lockResult);
        }
        if (Objects.nonNull(resultTransformer)) {
            query.setResultTransformer(resultTransformer);
        }
        return query;
    }



    @SuppressWarnings("unchecked")
    private Map<String, JoinType> getDetachedCriteriaJoinTypes() {
        return detachedCriteria.getJoinTypes();
    }

    private JpaCriteriaQuery<?> createCriteriaQuery(List<Projection> projections) {
        var cb = getCriteriaBuilder();
        var cq = projections.stream()
                .filter( it -> !(it instanceof DistinctProjection || it instanceof DistinctPropertyProjection))
                .toList().size() > 1 ?  cb.createQuery(Object[].class) : cb.createQuery(Object.class);
        projections.stream()
                .filter( it -> it instanceof DistinctProjection || it instanceof DistinctPropertyProjection)
                .findFirst()
                .ifPresent(projection -> {
                    cq.distinct(true);
                });
        return cq;
    }

    @SuppressWarnings("unchecked")
    private List<Query.Criterion> getDetachedCriteria() {
        return detachedCriteria.getCriteria();
    }

    private List<DetachedAssociationCriteria> getDetachedAssociationCriteria() {
        return getDetachedCriteria()
                .stream()
                .map(new DetachedAssociationFunction())
                .flatMap(List::stream)
                .toList();
    }

    private List<String> collectJoinColumns() {
        List<String> joinColumns = ((Map<String, FetchType>) detachedCriteria.getFetchStrategies())
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(FetchType.EAGER))
                .map(Map.Entry::getKey)
                .toList();
        return joinColumns;
    }

    private List<GroupPropertyProjection> collectGroupProjections() {
        List<GroupPropertyProjection> groupProjections = projections().getProjectionList()
                .stream()
                .filter(GroupPropertyProjection.class::isInstance)
                .map(GroupPropertyProjection.class::cast)
                .toList();
        return groupProjections;
    }

    private List<Projection> collectProjections() {
        return projections().getProjectionList()
                .stream()
                .filter(new ProjectionPredicate())
                .toList();
    }

    private void assignCriteria(CriteriaQuery cq, HibernateCriteriaBuilder cb, From root, JpaFromProvider tablesByName) {
        List<Criterion>  criteriaList = getDetachedCriteria();
        if (!criteriaList.isEmpty()) {
            jakarta.persistence.criteria.Predicate[] predicates = PredicateGenerator.getPredicates(cb, cq, root, criteriaList, tablesByName);
            cq.where(cb.and(predicates));
        }
    }

    private void assignOrderBy(CriteriaQuery cq, JpaFromProvider tablesByName) {
        var cb = getCriteriaBuilder();
        List<Order> orders = detachedCriteria.getOrders();
        if (!orders.isEmpty()) {
            cq.orderBy(orders
                    .stream()
                    .map(order -> {
                        Path expression = tablesByName.getFullyQualifiedPath(order.getProperty());
                        if (order.isIgnoreCase() && expression.getJavaType().equals(String.class)) {
                            if (order.getDirection().equals(Order.Direction.ASC)) {
                                return cb.asc(cb.lower(expression));
                            }  else {
                                return cb.desc(cb.lower(expression));
                            }
                        } else {
                            if (order.getDirection().equals(Order.Direction.ASC)) {
                                return cb.asc(expression);
                            }  else {
                                return cb.desc(expression);
                            }
                        }

                    })
                    .toList()
            );
        }
    }

    private void assignGroupBy(List<GroupPropertyProjection> groupProjections, From root, CriteriaQuery cq, JpaFromProvider tablesByName) {
        if (!groupProjections.isEmpty()) {
            List<Expression> groupByPaths = groupProjections
                    .stream()
                    .map(groupPropertyProjection -> {
                        String propertyName = groupPropertyProjection.getPropertyName();
                        return tablesByName.getFullyQualifiedPath(propertyName);
                    })
                    .map(Expression.class::cast)
                    .toList();
            cq.groupBy(groupByPaths);
        }
    }

    private void assignProjections(List<Projection> projections, CriteriaQuery cq, JpaFromProvider tablesByName) {
        List<Expression> projectionExpressions = projections
                .stream()
                .map(projectionToJpaExpression(tablesByName))
                .filter(Objects::nonNull)
                .map(Expression.class::cast)
                .toList();
        if (projectionExpressions.size() == 1) {
            cq.select(projectionExpressions.get(0));
        } else if (projectionExpressions.size() > 1){
            cq.multiselect(projectionExpressions);
        } else {
            cq.select(tablesByName.getFullyQualifiedPath("root"));
        }
    }

    private Function<Projection, JpaExpression> projectionToJpaExpression(
            JpaFromProvider tablesByName) {
        var cb = getCriteriaBuilder();
        return projection -> {
            if (countProjectionPredicate.test(projection)) {
                return cb.count(tablesByName.getFullyQualifiedPath("root"));
            } else if (countDistinctProjection.test(projection)) {
                String propertyName = ((PropertyProjection) projection).getPropertyName();
                return cb.countDistinct(tablesByName.getFullyQualifiedPath("root." + propertyName));
            } else if (idProjectionPredicate.test(projection)) {
                return (JpaExpression) tablesByName.getFullyQualifiedPath("root.id");
            } else if (distinctProjectionPredicate.test(projection)) {
                return null;
            } else {
                String propertyName = ((PropertyProjection) projection).getPropertyName();
                Path path = tablesByName.getFullyQualifiedPath(propertyName);
                if (maxProjectionPredicate.test(projection)) {
                    return cb.max(path);
                } else if (minProjectionPredicate.test(projection)) {
                    return cb.min(path);
                } else if (avgProjectionPredicate.test(projection)) {
                    return cb.avg(path);
                } else if (sumProjectionPredicate.test(projection)) {
                    return cb.sum(path);
                } else if (propertyProjectionPredicate.test(projection)) { // keep this last!!!
                    return (JpaExpression)path;
                }
            }
            return null;
        };
    }

    private SessionFactory getSessionFactory() {
        return ((IHibernateTemplate) session.getNativeInterface()).getSessionFactory();
    }

    private HibernateCriteriaBuilder getCriteriaBuilder() {
        return getSessionFactory().getCriteriaBuilder();
    }


    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        return list();
    }

    protected String calculatePropertyName(String property) {
        if (alias == null) {
            return property;
        }
        return alias + '.' + property;
    }

    protected String generateAlias(String associationName) {
        return calculatePropertyName(associationName) + calculatePropertyName(ALIAS) + aliasCount++;
    }

    public Query in(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.inList(propertyName,subquery);
        return this;
    }


    protected class HibernateAssociationQuery extends AssociationQuery {

        protected String alias;
        protected CriteriaQuery assocationCriteria;

        public HibernateAssociationQuery(CriteriaQuery criteria, AbstractHibernateSession session, PersistentEntity associatedEntity, Association association, String alias) {
            super(session, associatedEntity, association);
            this.alias = alias;
            assocationCriteria = criteria;
        }



        @Override
        public Query order(Order order) {
            return this;
        }

        @Override
        public Query isEmpty(String property) {
            return this;
        }


        @Override
        public Query isNotEmpty(String property) {
            return this;
        }

        @Override
        public Query isNull(String property) {
            return this;
        }

        @Override
        public Query isNotNull(String property) {
            return this;
        }

        @Override
        public void add(Criterion criterion) {
        }

        @Override
        public Junction disjunction() {
            return null;
        }

        @Override
        public Junction negation() {
            return null;
        }

        @Override
        public Query eq(String property, Object value) {
            return this;
        }

        @Override
        public Query idEq(Object value) {
            return this;
        }

        @Override
        public Query gt(String property, Object value) {
            return this;
        }

        @Override
        public Query and(Criterion a, Criterion b) {
              return this;
        }

        @Override
        public Query or(Criterion a, Criterion b) {
            return this;
        }

        @Override
        public Query allEq(Map<String, Object> values) {
            return this;
        }

        @Override
        public Query ge(String property, Object value) {
            return this;
        }

        @Override
        public Query le(String property, Object value) {
            return this;
        }

        @Override
        public Query gte(String property, Object value) {
            return this;
        }

        @Override
        public Query lte(String property, Object value) {
            return this;
        }

        @Override
        public Query lt(String property, Object value) {
            return this;
        }

        @Override
        public Query in(String property, List values) {
            return this;
        }

        @Override
        public Query between(String property, Object start, Object end) {
            return this;
        }

        @Override
        public Query like(String property, String expr) {
            return this;
        }

        @Override
        public Query ilike(String property, String expr) {
            return this;
        }

        @Override
        public Query rlike(String property, String expr) {
            return this;
        }
    }

    protected class CriteriaAndAlias {
        protected CriteriaQuery criteria;
        protected String alias;
        protected String associationPath;


        public CriteriaAndAlias(CriteriaQuery criteria, String alias, String associationPath) {
            this.criteria = criteria;
            this.alias = alias;
            this.associationPath = associationPath;
        }
    }
}
