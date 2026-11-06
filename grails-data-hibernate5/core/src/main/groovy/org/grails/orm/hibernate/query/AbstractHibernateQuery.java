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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.JoinType;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.LockMode;
import org.hibernate.NonUniqueResultException;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.type.BasicType;
import org.hibernate.type.TypeResolver;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.util.ReflectionUtils;

import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractHibernateQuery extends Query {

    public static final String SIZE_CONSTRAINT_PREFIX = "Size";

    protected static final String ALIAS = "_alias";
    protected static ConversionService conversionService = new DefaultConversionService();
    protected static Field opField = ReflectionUtils.findField(SimpleExpression.class, "op");
    private static final Map<String, Boolean> JOIN_STATUS_CACHE = new ConcurrentHashMap<>();

    static {
        ReflectionUtils.makeAccessible(opField);
    }

    protected Criteria criteria;
    protected org.hibernate.criterion.DetachedCriteria detachedCriteria;
    protected AbstractHibernateQuery.HibernateProjectionList hibernateProjectionList;
    protected String alias;
    protected int aliasCount;
    protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<>();
    /**
     * The joins that the query needs but has not made yet, in the order in which it came to need
     * them. They are made by {@link #applyDeferredJoins()}, when the query is executed.
     */
    private final List<CriteriaAndAlias> pendingJoins = new ArrayList<>();
    /**
     * The orders that have not been added to the criteria yet. They are added by
     * {@link #applyDeferredJoins()}, when the joins that they can use are known.
     */
    private final List<Order> pendingOrders = new ArrayList<>();
    /**
     * The association paths that the query requests an inner or a right join for and that it is
     * restricted to with a subquery, because they end in a collection or pass through one. See
     * {@link #applyDeferredJoins()}.
     */
    private final Set<String> subqueryJoinedPaths = new HashSet<>();
    /**
     * The join types that the query requested for an association before it requested a fetch mode for
     * it. The fetch mode replaces the join type, but the join that criteria on the association make
     * still has it: see {@link #createJoin(CriteriaAndAlias)}.
     */
    private final Map<String, JoinType> supersededJoinTypes = new HashMap<>();
    protected LinkedList<String> aliasStack = new LinkedList<>();
    protected LinkedList<PersistentEntity> entityStack = new LinkedList<>();
    protected LinkedList<Association> associationStack = new LinkedList<>();
    protected LinkedList aliasInstanceStack = new LinkedList();
    private boolean hasJoins = false;
    protected ProxyHandler proxyHandler = new HibernateProxyHandler();
    protected final AbstractHibernateCriterionAdapter abstractHibernateCriterionAdapter;

    protected AbstractHibernateQuery(Criteria criteria, AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
        this.criteria = criteria;
        if (entity != null) {
            initializeJoinStatus();
        }
        this.abstractHibernateCriterionAdapter = createHibernateCriterionAdapter();
    }

    protected AbstractHibernateQuery(DetachedCriteria criteria, PersistentEntity entity) {
        super(null, entity);
        this.detachedCriteria = criteria;
        this.abstractHibernateCriterionAdapter = createHibernateCriterionAdapter();
        if (entity != null) {
            initializeJoinStatus();
        }
    }

    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value;
    }

    protected void initializeJoinStatus() {
        Boolean cachedStatus = JOIN_STATUS_CACHE.get(entity.getName());
        if (cachedStatus != null) hasJoins = cachedStatus;
        else {
            for (Association a : entity.getAssociations()) {
                if (a.getFetchStrategy() == FetchType.EAGER) hasJoins = true;
            }
        }
    }

    protected AbstractHibernateQuery(Criteria subCriteria, AbstractHibernateSession session, PersistentEntity associatedEntity, String newAlias) {
        this(subCriteria, session, associatedEntity);
        alias = newAlias;
    }

    @Override
    public Query isEmpty(String property) {
        org.hibernate.criterion.Criterion criterion = Restrictions.isEmpty(calculatePropertyName(property));
        addToCriteria(criterion);
        return this;
    }

    @Override
    public Query isNotEmpty(String property) {
        addToCriteria(Restrictions.isNotEmpty(calculatePropertyName(property)));
        return this;
    }

    @Override
    public Query isNull(String property) {
        addToCriteria(Restrictions.isNull(calculatePropertyName(property)));
        return this;
    }

    @Override
    public Query isNotNull(String property) {
        addToCriteria(Restrictions.isNotNull(calculatePropertyName(property)));
        return this;
    }

    @Override
    public void add(Criterion criterion) {
        if (criterion instanceof FunctionCallingCriterion) {
            org.hibernate.criterion.Criterion sqlRestriction = getRestrictionForFunctionCall((FunctionCallingCriterion) criterion, getEntity());
            if (sqlRestriction != null) {
                addToCriteria(sqlRestriction);
            }
        }
        else if (criterion instanceof PropertyCriterion) {
            PropertyCriterion pc = (PropertyCriterion) criterion;
            Object value = pc.getValue();
            if (value instanceof QueryableCriteria) {
                setDetachedCriteriaValue((QueryableCriteria) value, pc);
            } else {
                if (!(value instanceof DetachedCriteria)) {
                    doTypeConversionIfNeccessary(getEntity(), pc);
                }
            }
        }
        if (criterion instanceof DetachedAssociationCriteria) {
            DetachedAssociationCriteria associationCriteria = (DetachedAssociationCriteria) criterion;

            Association association = associationCriteria.getAssociation();
            List<Query.Criterion> criteria = associationCriteria.getCriteria();

            if (association instanceof Embedded) {
                String associationName = association.getName();
                if (getCurrentAlias() != null) {
                    associationName = getCurrentAlias() + '.' + associationName;
                }
                for (Criterion c : criteria) {
                    final org.hibernate.criterion.Criterion hibernateCriterion = getHibernateCriterionAdapter().toHibernateCriterion(this, c, associationName);
                    if (hibernateCriterion != null) {
                        addToCriteria(hibernateCriterion);
                    }
                }
            }
            else {

                CriteriaAndAlias criteriaAndAlias = getCriteriaAndAlias(associationCriteria);

                if (criteriaAndAlias.criteria != null) {
                    aliasInstanceStack.add(criteriaAndAlias.criteria);
                }
                else if (criteriaAndAlias.detachedCriteria != null) {
                    aliasInstanceStack.add(criteriaAndAlias.detachedCriteria);
                }
                aliasStack.add(criteriaAndAlias.alias);
                associationStack.add(association);
                entityStack.add(association.getAssociatedEntity());

                try {
                    @SuppressWarnings("unchecked")
                    List<Criterion> associationCriteriaList = criteria;
                    for (Criterion c : associationCriteriaList) {
                        add(c);
                    }
                }
                finally {
                    aliasInstanceStack.removeLast();
                    aliasStack.removeLast();
                    entityStack.removeLast();
                    associationStack.removeLast();
                }
            }

        }
        else {

            final org.hibernate.criterion.Criterion hibernateCriterion = getHibernateCriterionAdapter().toHibernateCriterion(this, criterion, getCurrentAlias());
            if (hibernateCriterion != null) {
                addToCriteria(hibernateCriterion);
            }
        }
    }

    @Override
    public PersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.getLast();
        }
        return super.getEntity();
    }

    protected String getAssociationPath(String propertyName) {
        if (propertyName.indexOf('.') > -1) {
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

    protected String getCurrentAlias() {
        if (alias != null) {
            return alias;
        }

        if (aliasStack.isEmpty()) {
            return null;
        }

        return aliasStack.getLast();
    }

    @SuppressWarnings("unchecked")
    static void doTypeConversionIfNeccessary(PersistentEntity entity, PropertyCriterion pc) {
        // ignore Size related constraints
        if (pc.getClass().getSimpleName().startsWith(SIZE_CONSTRAINT_PREFIX)) {
            return;
        }

        String property = pc.getProperty();
        Object value = pc.getValue();
        PersistentProperty p = entity.getPropertyByName(property);
        if (p != null && !p.getType().isInstance(value)) {
            pc.setValue(conversionService.convert(value, p.getType()));
        }
    }

    org.hibernate.criterion.Criterion getRestrictionForFunctionCall(FunctionCallingCriterion criterion, PersistentEntity entity) {
        org.hibernate.criterion.Criterion sqlRestriction;

        SessionFactory sessionFactory = ((IHibernateTemplate) session.getNativeInterface()).getSessionFactory();
        String property = criterion.getProperty();
        Criterion datastoreCriterion = criterion.getPropertyCriterion();
        PersistentProperty pp = entity.getPropertyByName(property);

        if (pp == null) throw new InvalidDataAccessResourceUsageException(
             "Cannot execute function defined in query [" + criterion.getFunctionName() +
             "] on non-existent property [" + property + "] of [" + entity.getJavaClass() + "]");

        String functionName = criterion.getFunctionName();

        Dialect dialect = getDialect(sessionFactory);
        SQLFunction sqlFunction = dialect.getFunctions().get(functionName);
        if (sqlFunction != null) {
            TypeResolver typeResolver = getTypeResolver(sessionFactory);
            BasicType basic = typeResolver.basic(pp.getType().getName());
            if (basic != null && datastoreCriterion instanceof PropertyCriterion) {

                PropertyCriterion pc = (PropertyCriterion) datastoreCriterion;
                final org.hibernate.criterion.Criterion hibernateCriterion = getHibernateCriterionAdapter().toHibernateCriterion(this, datastoreCriterion, alias);
                if (hibernateCriterion instanceof SimpleExpression) {
                    SimpleExpression expr = (SimpleExpression) hibernateCriterion;
                    Object op = ReflectionUtils.getField(opField, expr);
                    PropertyMapping mapping = getEntityPersister(entity.getJavaClass().getName(), sessionFactory);
                    String[] columns;
                    if (alias != null) {
                        columns = mapping.toColumns(alias, property);
                    }
                    else {
                        columns = mapping.toColumns(property);
                    }
                    String root = render(basic, Arrays.asList(columns), sessionFactory, sqlFunction);
                    Object value = pc.getValue();
                    if (value != null) {
                        sqlRestriction = Restrictions.sqlRestriction(root + op + "?", value, typeResolver.basic(value.getClass().getName()));
                    }
                    else {
                        sqlRestriction = Restrictions.sqlRestriction(root + op + "?", value, basic);
                    }
                }
                else {
                    throw new InvalidDataAccessResourceUsageException("Unsupported function [" + functionName + "] defined in query for property [" + property + "] with type [" + pp.getType() + "]");
                }
            }
            else {
                throw new InvalidDataAccessResourceUsageException("Unsupported function [" + functionName + "] defined in query for property [" + property + "] with type [" + pp.getType() + "]");
            }
        }
        else {
            throw new InvalidDataAccessResourceUsageException("Unsupported function defined in query [" + functionName + "]");
        }
        return sqlRestriction;
    }

    protected abstract String render(BasicType basic, List<String> asList, SessionFactory sessionFactory, SQLFunction sqlFunction);

    protected abstract PropertyMapping getEntityPersister(String name, SessionFactory sessionFactory);

    protected abstract TypeResolver getTypeResolver(SessionFactory sessionFactory);

    protected abstract Dialect getDialect(SessionFactory sessionFactory);

    @Override
    public Junction disjunction() {
        final org.hibernate.criterion.Disjunction disjunction = Restrictions.disjunction();
        addToCriteria(disjunction);
        return new HibernateJunction(disjunction, alias);
    }

    @Override
    public Junction negation() {
        final org.hibernate.criterion.Disjunction disjunction = Restrictions.disjunction();
        addToCriteria(Restrictions.not(disjunction));
        return new HibernateJunction(disjunction, alias);
    }

    @Override
    public Query eq(String property, Object value) {
        addToCriteria(Restrictions.eq(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query idEq(Object value) {
        addToCriteria(Restrictions.idEq(value));
        return this;
    }

    @Override
    public Query gt(String property, Object value) {
        addToCriteria(Restrictions.gt(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query and(Criterion a, Criterion b) {
        AbstractHibernateCriterionAdapter adapter = getHibernateCriterionAdapter();
        addToCriteria(Restrictions.and(adapter.toHibernateCriterion(this, a, alias), adapter.toHibernateCriterion(this, a, alias)));
        return this;
    }

    @Override
    public Query or(Criterion a, Criterion b) {
        AbstractHibernateCriterionAdapter adapter = getHibernateCriterionAdapter();
        addToCriteria(Restrictions.or(adapter.toHibernateCriterion(this, a, alias), adapter.toHibernateCriterion(this, b, alias)));
        return this;
    }

    @Override
    public Query allEq(Map<String, Object> values) {
        addToCriteria(Restrictions.allEq(values));
        return this;
    }

    @Override
    public Query ge(String property, Object value) {
        addToCriteria(Restrictions.ge(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query le(String property, Object value) {
        addToCriteria(Restrictions.le(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query gte(String property, Object value) {
        addToCriteria(Restrictions.ge(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query lte(String property, Object value) {
        addToCriteria(Restrictions.le(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query lt(String property, Object value) {
        addToCriteria(Restrictions.lt(calculatePropertyName(property), value));
        return this;
    }

    @Override
    public Query in(String property, List values) {
        addToCriteria(Restrictions.in(calculatePropertyName(property), values));
        return this;
    }

    @Override
    public Query between(String property, Object start, Object end) {
        addToCriteria(Restrictions.between(calculatePropertyName(property), start, end));
        return this;
    }

    @Override
    public Query like(String property, String expr) {
        addToCriteria(Restrictions.like(calculatePropertyName(property), calculatePropertyName(expr)));
        return this;
    }

    @Override
    public Query ilike(String property, String expr) {
        addToCriteria(Restrictions.ilike(calculatePropertyName(property), calculatePropertyName(expr)));
        return this;
    }

    @Override
    public Query rlike(String property, String expr) {
        addToCriteria(createRlikeExpression(calculatePropertyName(property), calculatePropertyName(expr)));
        return this;
    }

    @Override
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByName(calculatePropertyName(associationName));
        if (property != null && (property instanceof Association)) {
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, generateAlias(associationName));

            Association association = (Association) property;
            if (subCriteria.criteria != null) {
                return new HibernateAssociationQuery(subCriteria.criteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, subCriteria.alias);
            }
            else if (subCriteria.detachedCriteria != null) {
                return new HibernateAssociationQuery(subCriteria.detachedCriteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, subCriteria.alias);
            }
        }
        throw new InvalidDataAccessApiUsageException("Cannot query association [" + calculatePropertyName(associationName) + "] of entity [" + entity + "]. Property is not an association!");
    }

    protected CriteriaAndAlias getCriteriaAndAlias(DetachedAssociationCriteria associationCriteria) {
        String associationPath = associationCriteria.getAssociationPath();
        String alias = associationCriteria.getAlias();

        if (associationPath == null) {
            associationPath = associationCriteria.getAssociation().getName();
        }
        return getOrCreateAlias(associationPath, alias);
    }

    /**
     * Returns the join of an association that criteria of the query use. A join that the query
     * already has for the association is reused. The join is an inner join unless the query requests
     * another join type for the association, and is made when the query is executed: see
     * {@link #applyDeferredJoins()}.
     *
     * @param associationName The name of the association, or its path
     * @param alias The alias for the join, or the alias of the join that the association starts from
     * @return The join
     */
    protected CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        CriteriaAndAlias join = getOrRegisterJoin(associationName, alias);
        join.usedByCriteria = true;
        return join;
    }

    private CriteriaAndAlias getOrRegisterJoin(String associationName, String alias) {
        String associationPath = getAssociationPath(associationName);
        Criteria parentCriteria = criteria;
        if (alias == null) {
            alias = generateAlias(associationName);
        }
        else {
            CriteriaAndAlias criteriaAndAlias = createdAssociationPaths.get(alias);
            if (criteriaAndAlias != null) {
                if (associationPath.equals(criteriaAndAlias.associationPath)) {
                    // the alias of the join of this association
                    return criteriaAndAlias;
                }
                // the alias of the join that the association starts from
                parentCriteria = criteriaAndAlias.criteria;
                if (parentCriteria != null) {
                    alias = associationName + '_' + alias;
                    associationPath = criteriaAndAlias.associationPath + '.' + associationPath;
                }
            }
        }
        CriteriaAndAlias join = createdAssociationPaths.get(associationPath);
        return join != null ? join : registerJoin(parentCriteria, associationPath, alias);
    }

    /**
     * Returns the join of the association at the given path from the root entity. A join that the
     * query already has for the path is reused.
     */
    private CriteriaAndAlias getOrRegisterJoinAt(String associationPath, String associationName) {
        CriteriaAndAlias join = createdAssociationPaths.get(associationPath);
        return join != null ? join : registerJoin(criteria, associationPath, generateAlias(associationName));
    }

    /**
     * Registers a join under its association path and its alias. The alias can be used at once, in
     * criteria, projections and orders. The join itself is made by {@link #applyDeferredJoins()}.
     */
    private CriteriaAndAlias registerJoin(Criteria parentCriteria, String associationPath, String alias) {
        CriteriaAndAlias join = parentCriteria != null ?
                new CriteriaAndAlias(parentCriteria, alias, associationPath) :
                new CriteriaAndAlias(detachedCriteria, alias, associationPath);
        createdAssociationPaths.put(associationPath, join);
        createdAssociationPaths.put(alias, join);
        pendingJoins.add(join);
        return join;
    }

    /**
     * Makes a join, with the join type that the query requests for the association. Without a
     * requested join type, a join that criteria use is an inner join, and a join that only a sort, a
     * projection or the path to a requested association uses is a left join, which keeps the rows
     * where the association is null. A join that criteria use keeps a join type that the query
     * requested before a fetch mode replaced it, as it did when the join was made at once. A right
     * join of a collection is made as an inner join: it would return a row without an instance for
     * each element that no instance has.
     * <p>
     * A left join of a collection is kept from fetching the collection when criteria use it, since
     * its rows lack the elements that the criteria leave out, and when only a sort or a projection
     * uses it, unless the query fetches the collection or another one: see
     * {@link GrailsHibernateUtil#getSortJoinType(Criteria, String, boolean)} and
     * {@link GrailsHibernateUtil#keepsSortedCollectionUnfetched(Criteria, String)}.
     */
    private void createJoin(CriteriaAndAlias join) {
        String associationPath = join.associationPath;
        boolean collection = getAssociation(associationPath) instanceof ToMany;
        // a path that the query is restricted to with a subquery is joined, and fetched, with a left join
        JoinType joinType = subqueryJoinedPaths.contains(associationPath) ? JoinType.LEFT : joinTypes.get(associationPath);
        if (joinType == null && join.usedByCriteria) {
            joinType = supersededJoinTypes.get(associationPath);
        }
        org.hibernate.sql.JoinType hibernateJoinType;
        if (joinType == null) {
            joinType = join.usedByCriteria ? JoinType.INNER : JoinType.LEFT;
            hibernateJoinType = join.usedByCriteria || join.criteria == null ? resolveJoinType(joinType) :
                    GrailsHibernateUtil.getSortJoinType(join.criteria, associationPath, collection);
        }
        else {
            if (joinType == JoinType.RIGHT && collection) {
                joinType = JoinType.INNER;
            }
            hibernateJoinType = resolveJoinType(joinType);
        }
        if (join.criteria != null) {
            join.criteria.createAlias(associationPath, join.alias, hibernateJoinType);
            if (hibernateJoinType == org.hibernate.sql.JoinType.LEFT_OUTER_JOIN && collection &&
                    (join.usedByCriteria || GrailsHibernateUtil.keepsSortedCollectionUnfetched(join.criteria, associationPath))) {
                GrailsHibernateUtil.keepCollectionUnfetched(join.criteria, join.alias);
            }
        }
        else if (join.detachedCriteria != null) {
            join.detachedCriteria.createAlias(associationPath, join.alias, hibernateJoinType);
        }
    }

    private org.hibernate.sql.JoinType resolveJoinType(JoinType joinType) {
        if (joinType == null) {
            return org.hibernate.sql.JoinType.INNER_JOIN;
        }
        switch (joinType) {
            case LEFT:
                return org.hibernate.sql.JoinType.LEFT_OUTER_JOIN;
            case RIGHT:
                return org.hibernate.sql.JoinType.RIGHT_OUTER_JOIN;
            default:
                return org.hibernate.sql.JoinType.INNER_JOIN;
        }
    }

    @Override
    public ProjectionList projections() {
        if (hibernateProjectionList == null) {
            hibernateProjectionList = new HibernateProjectionList();
        }
        return hibernateProjectionList;
    }

    @Override
    public Query max(int max) {
        if (criteria != null)
            criteria.setMaxResults(max);
        return this;
    }

    @Override
    public Query maxResults(int max) {
        if (criteria != null)
            criteria.setMaxResults(max);
        return this;
    }

    @Override
    public Query offset(int offset) {
        if (criteria != null)
            criteria.setFirstResult(offset);
        return this;
    }

    @Override
    public Query firstResult(int offset) {
        offset(offset);
        return this;
    }

    @Override
    public Query cache(boolean cache) {
        criteria.setCacheable(cache);

        return super.cache(cache);
    }

    @Override
    public Query lock(boolean lock) {
        criteria.setCacheable(false);
        criteria.setLockMode(LockMode.PESSIMISTIC_WRITE);
        return super.lock(lock);
    }

    @Override
    public Query order(Order order) {
        super.order(order);
        pendingOrders.add(order);
        return this;
    }

    /**
     * Adds an order to the criteria. This happens when the query is executed, so that a sort by a
     * property of an association uses the join that the query has for the association by then,
     * whether the sort or the criteria came first.
     */
    private void applyOrder(Order order) {
        String property = order.getProperty();
        int i = property.indexOf('.');
        if (i < 0) {
            addSimpleOrder(order, property);
            return;
        }

        String sortHead = property.substring(0, i);
        if (!createdAssociationPaths.containsKey(sortHead)) {
            PersistentProperty persistentProperty = entity.getPropertyByName(sortHead);
            if (!(persistentProperty instanceof Association)) {
                // neither an alias of this query nor an association: the sort is ignored
                return;
            }
            if (persistentProperty instanceof Embedded) {
                // a component path, which Hibernate resolves against the entity itself
                addSimpleOrder(order, property);
                return;
            }
        }

        addAliasedOrder(order, calculateSortPropertyName(property));
    }

    /**
     * Qualifies a sort property that crosses one or more associations with the alias of the last
     * association on its path, since Hibernate resolves a property of a joined association only
     * through the alias of the join. The head of the path is either an alias defined by a where
     * query or an association of the entity. A join that the query has for an association, for
     * example for a where query criterion, is reused; an association that it has no join for is
     * joined for the sort.
     */
    private String calculateSortPropertyName(String property) {
        String[] tokens = property.split("\\.");
        PersistentEntity currentEntity = entity;
        String currentAlias = null;
        StringBuilder associationPath = new StringBuilder();
        int firstAssociation = 0;

        CriteriaAndAlias head = createdAssociationPaths.get(tokens[0]);
        if (head != null) {
            currentAlias = head.alias;
            associationPath.append(head.associationPath);
            currentEntity = getAssociatedEntity(head.associationPath);
            firstAssociation = 1;
        }

        for (int i = firstAssociation; i < tokens.length - 1; i++) {
            String token = tokens[i];
            PersistentProperty persistentProperty = currentEntity != null ? currentEntity.getPropertyByName(token) : null;
            if (!(persistentProperty instanceof Association) || persistentProperty instanceof Embedded) {
                // the rest of the path is a component path or a property, which Hibernate resolves itself
                StringBuilder remainder = new StringBuilder(currentAlias == null ? "" : currentAlias);
                for (int j = i; j < tokens.length; j++) {
                    if (remainder.length() > 0) {
                        remainder.append('.');
                    }
                    remainder.append(tokens[j]);
                }
                return remainder.toString();
            }

            if (associationPath.length() > 0) {
                associationPath.append('.');
            }
            associationPath.append(token);
            currentAlias = getOrRegisterJoinAt(associationPath.toString(), token).alias;
            currentEntity = ((Association) persistentProperty).getAssociatedEntity();
        }
        return currentAlias + '.' + tokens[tokens.length - 1];
    }

    /**
     * Makes the joins of the query and adds its orders. This happens when the query is executed,
     * because the rest of the query can still change before that: criteria passed to {@code list} are
     * added after the criteria and the sort of a where query, and the {@code fetch} argument of
     * {@code list}, which can request a join type, is read last. Criteria, projections and orders only
     * need the alias of a join, which is known as soon as the join is registered. A query that is used
     * as a subquery is not executed itself: this happens once its criteria have been added, when its
     * detached criteria are handed to the query that embeds them.
     * <p>
     * Each association is joined once, by its path from the root entity, with the join type that
     * {@link #createJoin(CriteriaAndAlias)} describes. A sort must not change which rows the query
     * returns or what it fetches, so an association that only a sort uses is joined with a left
     * join, and a collection is not fetched through that join. A requested join type other than a
     * left join is applied whether or not anything else in the query uses the association: a fetch
     * mode alone always joins with an outer join.
     */
    protected void applyDeferredJoins() {
        for (Map.Entry<String, JoinType> requestedJoin : joinTypes.entrySet()) {
            if (requestedJoin.getValue() != JoinType.LEFT) {
                applyRequestedJoin(requestedJoin.getKey(), requestedJoin.getValue());
            }
        }
        for (Order order : pendingOrders) {
            applyOrder(order);
        }
        pendingOrders.clear();
        for (CriteriaAndAlias join : pendingJoins) {
            createJoin(join);
        }
        pendingJoins.clear();
    }

    /**
     * Applies a join type that the query requests for an association it has not joined, by joining
     * each association on the path. A path that does not consist of associations to other entities
     * is left as it is.
     * <p>
     * Hibernate fetches a collection only through a left join, and a join through a collection
     * returns an instance once for each element of the collection. A path that ends in a collection,
     * or passes through one, that nothing else in the query joins is therefore joined in a subquery,
     * which restricts the query to the instances that the join returns a row for without changing
     * the number of its rows: see {@link GrailsHibernateUtil#joinedInSubquery(String, String, String)}.
     * A collection at the end of the path keeps the left join of its fetch mode, or the left join that
     * a sort by the collection needs. The associations before the collection are joined, so that they
     * are fetched. A collection of a basic type can only be at the end of the path.
     */
    private void applyRequestedJoin(String associationPath, JoinType joinType) {
        String[] tokens = associationPath.split("\\.");
        int last = tokens.length - 1;
        Association[] associations = new Association[tokens.length];
        PersistentEntity currentEntity = entity;
        for (int i = 0; i < tokens.length; i++) {
            PersistentProperty persistentProperty = currentEntity != null ? currentEntity.getPropertyByName(tokens[i]) : null;
            if (!(persistentProperty instanceof Association) || persistentProperty instanceof Embedded ||
                    (persistentProperty instanceof Basic && i != last)) {
                return;
            }
            associations[i] = (Association) persistentProperty;
            currentEntity = associations[i].getAssociatedEntity();
        }

        String[] paths = new String[tokens.length];
        int unjoinedCollection = -1;
        for (int i = 0; i < tokens.length; i++) {
            paths[i] = i == 0 ? tokens[i] : paths[i - 1] + '.' + tokens[i];
            if (unjoinedCollection < 0 && associations[i] instanceof ToMany && !createdAssociationPaths.containsKey(paths[i])) {
                unjoinedCollection = i;
            }
        }
        if (unjoinedCollection >= 0 && subqueryJoinedPaths.add(associationPath)) {
            String rootAlias = criteria != null ? criteria.getAlias() : detachedCriteria.getAlias();
            addToCriteria(GrailsHibernateUtil.joinedInSubquery(entity.getName(), rootAlias, associationPath));
        }
        int joined = unjoinedCollection < 0 ? tokens.length : unjoinedCollection;
        for (int i = 0; i < joined; i++) {
            getOrRegisterJoinAt(paths[i], tokens[i]);
        }
    }

    /**
     * Returns the entity at the end of an association path from the root entity, or null when
     * the path does not consist of associations only.
     */
    private PersistentEntity getAssociatedEntity(String associationPath) {
        Association association = getAssociation(associationPath);
        return association != null ? association.getAssociatedEntity() : null;
    }

    /**
     * Returns the association at the end of an association path from the root entity, or null when
     * the path does not consist of associations only.
     */
    private Association getAssociation(String associationPath) {
        String path = associationPath;
        if (alias != null && path.startsWith(alias + '.')) {
            path = path.substring(alias.length() + 1);
        }
        PersistentEntity currentEntity = entity;
        Association association = null;
        for (String token : path.split("\\.")) {
            PersistentProperty persistentProperty = currentEntity != null ? currentEntity.getPropertyByName(token) : null;
            if (!(persistentProperty instanceof Association)) {
                return null;
            }
            association = (Association) persistentProperty;
            currentEntity = association.getAssociatedEntity();
        }
        return association;
    }

    private void addAliasedOrder(Order order, String aliasedProperty) {
        org.hibernate.criterion.Order hibernateOrder = order.getDirection() == Order.Direction.ASC ?
                org.hibernate.criterion.Order.asc(aliasedProperty) :
                org.hibernate.criterion.Order.desc(aliasedProperty);
        hibernateOrder = order.isIgnoreCase() ? hibernateOrder.ignoreCase() : hibernateOrder;
        if (criteria != null) {
            criteria.addOrder(hibernateOrder);
        }
        else if (detachedCriteria != null) {
            detachedCriteria.addOrder(hibernateOrder);
        }
    }

    private void addSimpleOrder(Order order, String property) {
        Criteria c = criteria;
        if (c != null) {
            addOrderToCriteria(c, property, order);
        } else {
            DetachedCriteria dc = detachedCriteria;
            addOrderToDetachedCriteria(dc, property, order);
        }
    }

    private void addOrderToDetachedCriteria(DetachedCriteria dc, String property, Order order) {
        if (dc != null) {
            org.hibernate.criterion.Order hibernateOrder = order.getDirection() == Order.Direction.ASC ?
                    org.hibernate.criterion.Order.asc(calculatePropertyName(property)) :
                    org.hibernate.criterion.Order.desc(calculatePropertyName(property));
            dc.addOrder(order.isIgnoreCase() ? hibernateOrder.ignoreCase() : hibernateOrder);

        }
    }

    private void addOrderToCriteria(Criteria c, String property, Order order) {
        org.hibernate.criterion.Order hibernateOrder = order.getDirection() == Order.Direction.ASC ?
                org.hibernate.criterion.Order.asc(calculatePropertyName(property)) :
                org.hibernate.criterion.Order.desc(calculatePropertyName(property));

        c.addOrder(order.isIgnoreCase() ? hibernateOrder.ignoreCase() : hibernateOrder);
    }

    private String calculateProjectionPropertyName(String propertyName) {
        int firstDot = propertyName.indexOf('.');
        if (firstDot < 0) {
            return calculatePropertyName(propertyName);
        }

        PersistentEntity currentEntity = getEntity();
        String currentAlias = null;
        StringBuilder associationPath = new StringBuilder();
        String[] tokens = propertyName.split("\\.");

        for (int i = 0; i < tokens.length - 1; i++) {
            String token = tokens[i];
            PersistentProperty persistentProperty = currentEntity != null ? currentEntity.getPropertyByName(token) : null;
            if (!(persistentProperty instanceof Association) || persistentProperty instanceof Embedded) {
                if (currentAlias == null) {
                    return calculatePropertyName(propertyName);
                }
                // the rest of the path is a component path or a property, which Hibernate resolves through the alias
                return currentAlias + '.' + String.join(".", Arrays.copyOfRange(tokens, i, tokens.length));
            }

            if (associationPath.length() > 0) {
                associationPath.append('.');
            }
            associationPath.append(token);

            // A join that only a projection uses is a left join, so that rows
            // with null associations are preserved in the result set.
            currentAlias = getOrRegisterJoin(associationPath.toString(), generateAlias(token)).alias;
            currentEntity = ((Association) persistentProperty).getAssociatedEntity();
        }

        if (currentAlias == null) {
            return calculatePropertyName(propertyName);
        }
        return currentAlias + '.' + tokens[tokens.length - 1];
    }

    private Query.Projection normalizeProjectionPropertyPath(Query.Projection projection) {
        if (!(projection instanceof Query.PropertyProjection)) {
            return projection;
        }

        String propertyName = ((Query.PropertyProjection) projection).getPropertyName();
        String normalizedPropertyName = calculateProjectionPropertyName(propertyName);
        if (propertyName.equals(normalizedPropertyName)) {
            return projection;
        }

        if (projection instanceof Query.DistinctPropertyProjection) {
            return org.grails.datastore.mapping.query.Projections.distinct(normalizedPropertyName);
        }
        if (projection instanceof Query.CountDistinctProjection) {
            return org.grails.datastore.mapping.query.Projections.countDistinct(normalizedPropertyName);
        }
        if (projection instanceof Query.GroupPropertyProjection) {
            return org.grails.datastore.mapping.query.Projections.groupProperty(normalizedPropertyName);
        }
        if (projection instanceof Query.SumProjection) {
            return org.grails.datastore.mapping.query.Projections.sum(normalizedPropertyName);
        }
        if (projection instanceof Query.MinProjection) {
            return org.grails.datastore.mapping.query.Projections.min(normalizedPropertyName);
        }
        if (projection instanceof Query.MaxProjection) {
            return org.grails.datastore.mapping.query.Projections.max(normalizedPropertyName);
        }
        if (projection instanceof Query.AvgProjection) {
            return org.grails.datastore.mapping.query.Projections.avg(normalizedPropertyName);
        }
        return org.grails.datastore.mapping.query.Projections.property(normalizedPropertyName);
    }

    @Override
    public Query join(String property) {
        this.hasJoins = true;
        supersedeJoinType(property);
        if (criteria != null)
            criteria.setFetchMode(property, FetchMode.JOIN);
        else if (detachedCriteria != null)
            detachedCriteria.setFetchMode(property, FetchMode.JOIN);
        return this;
    }

    @Override
    public Query join(String property, JoinType joinType) {
        this.hasJoins = true;
        this.joinTypes.put(property, joinType);
        this.supersededJoinTypes.remove(property);
        if (criteria != null)
            criteria.setFetchMode(property, FetchMode.JOIN);
        else if (detachedCriteria != null)
            detachedCriteria.setFetchMode(property, FetchMode.JOIN);
        return this;
    }

    /**
     * Replaces a join type that the query requested for an association with the fetch mode that it
     * now requests. The join that criteria on the association make keeps the join type.
     */
    private void supersedeJoinType(String property) {
        JoinType joinType = this.joinTypes.remove(property);
        if (joinType != null) {
            this.supersededJoinTypes.put(property, joinType);
        }
    }

    @Override
    public Query select(String property) {
        this.hasJoins = true;
        supersedeJoinType(property);
        if (criteria != null)
            criteria.setFetchMode(property, FetchMode.SELECT);
        else if (detachedCriteria != null)
            detachedCriteria.setFetchMode(property, FetchMode.SELECT);
        return this;
    }

    @Override
    public List list() {
        if (criteria == null) throw new IllegalStateException("Cannot execute query using a detached criteria instance");

        int projectionLength = 0;
        if (hibernateProjectionList != null) {
            org.hibernate.criterion.ProjectionList projectionList = hibernateProjectionList.getHibernateProjectionList();
            projectionLength = projectionList.getLength();
            if (projectionLength > 0) {
                criteria.setProjection(projectionList);
            }
        }

        if (projectionLength < 2) {
            criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
        }

        applyDefaultSortOrderAndCaching();
        applyFetchStrategies();

        return listForCriteria();
    }

    public List listForCriteria() {
        Datastore datastore = session.getDatastore();
        ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
        if (publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this));
        }

        applyDeferredJoins();
        List results = criteria.list();
        if (publisher != null) {
            publisher.publishEvent(new PostQueryEvent(datastore, this, results));
        }
        return results;
    }

    protected void applyDefaultSortOrderAndCaching() {
        if (this.orderBy.isEmpty() && entity != null) {
            // don't apply default sorting, if projections present
            if (hibernateProjectionList != null && !hibernateProjectionList.isEmpty()) return;

            Mapping mapping = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if (mapping != null) {
                if (queryCache == null && mapping.getCache() != null && mapping.getCache().isEnabled()) {
                    criteria.setCacheable(true);
                }

                Map sortMap = mapping.getSort().getNamesAndDirections();
                DynamicFinder.applySortForMap(this, sortMap, true);

            }
        }
    }

    protected void applyFetchStrategies() {
        for (Map.Entry<String, FetchType> entry : fetchStrategies.entrySet()) {
            switch (entry.getValue()) {
                case EAGER:
                    if (criteria != null)
                        criteria.setFetchMode(entry.getKey(), FetchMode.JOIN);
                    else if (detachedCriteria != null)
                        detachedCriteria.setFetchMode(entry.getKey(), FetchMode.JOIN);
                    break;
                case LAZY:
                    if (criteria != null)
                        criteria.setFetchMode(entry.getKey(), FetchMode.SELECT);
                    else if (detachedCriteria != null)
                        detachedCriteria.setFetchMode(entry.getKey(), FetchMode.SELECT);
                    break;
            }
        }
    }

    @Override
    protected void flushBeforeQuery() {
        // do nothing
    }

    @Override
    public Object singleResult() {
        if (criteria == null) throw new IllegalStateException("Cannot execute query using a detached criteria instance");

        if (hibernateProjectionList != null) {
            criteria.setProjection(hibernateProjectionList.getHibernateProjectionList());
        }
        criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
        applyDefaultSortOrderAndCaching();
        applyFetchStrategies();

        Datastore datastore = session.getDatastore();
        ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
        if (publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this));
        }
        applyDeferredJoins();

        Object result;
        if (hasJoins) {
            try {
                result = proxyHandler.unwrap(criteria.uniqueResult());;
            } catch (NonUniqueResultException e) {
                result = singleResultViaListCall();
            }
        }
        else {
            result = singleResultViaListCall();
        }
        if (publisher != null) {
            publisher.publishEvent(new PostQueryEvent(datastore, this, Collections.singletonList(result)));
        }
        return result;
    }

    private Object singleResultViaListCall() {
        criteria.setMaxResults(1);
        if (hibernateProjectionList != null && hibernateProjectionList.isRowCount()) {
            criteria.setFirstResult(0);
        }
        List results = criteria.list();
        if (results.size() > 0) {
            return proxyHandler.unwrap(results.get(0));
        }
        return null;
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        return list();
    }

    String handleAssociationQuery(Association<?> association, List<Criterion> criteriaList) {
        return getCriteriaAndAlias(association).alias;
    }

    String handleAssociationQuery(Association<?> association, List<Criterion> criteriaList, String alias) {
        String associationName = calculatePropertyName(association.getName());
        return getOrCreateAlias(associationName, alias).alias;
    }

    protected CriteriaAndAlias getCriteriaAndAlias(Association<?> association) {
        String associationName = calculatePropertyName(association.getName());
        String newAlias = generateAlias(associationName);
        return getOrCreateAlias(associationName, newAlias);
    }

    protected void addToCriteria(org.hibernate.criterion.Criterion criterion) {
        if (criterion == null) {
            return;
        }

        if (aliasInstanceStack.isEmpty()) {
            if (criteria != null) {
                criteria.add(criterion);

            }
            else if (detachedCriteria != null) {
                detachedCriteria.add(criterion);
            }
        }
        else {
            Object criteriaObject = aliasInstanceStack.getLast();
            if (criteriaObject instanceof Criteria)
                ((Criteria) criteriaObject).add(criterion);
            else if (criteriaObject instanceof DetachedCriteria) {
                ((DetachedCriteria) criteriaObject).add(criterion);
            }
        }
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

    protected abstract void setDetachedCriteriaValue(QueryableCriteria value, PropertyCriterion pc);

    protected AbstractHibernateCriterionAdapter getHibernateCriterionAdapter() {
        return this.abstractHibernateCriterionAdapter;
    }

    protected abstract AbstractHibernateCriterionAdapter createHibernateCriterionAdapter();

    protected abstract org.hibernate.criterion.Criterion createRlikeExpression(String propertyName, String value);

    protected class HibernateJunction extends Junction {

        protected org.hibernate.criterion.Junction hibernateJunction;
        protected String alias;

        public HibernateJunction(org.hibernate.criterion.Junction junction, String alias) {
            hibernateJunction = junction;
            this.alias = alias;
        }

        @Override
        public Junction add(Criterion c) {
            if (c != null) {
                if (c instanceof FunctionCallingCriterion) {
                    org.hibernate.criterion.Criterion sqlRestriction = getRestrictionForFunctionCall((FunctionCallingCriterion) c, entity);
                    if (sqlRestriction != null) {
                        hibernateJunction.add(sqlRestriction);
                    }
                }
                else {
                    AbstractHibernateCriterionAdapter adapter = getHibernateCriterionAdapter();
                    org.hibernate.criterion.Criterion criterion = adapter.toHibernateCriterion(AbstractHibernateQuery.this, c, alias);
                    if (criterion != null) {
                        hibernateJunction.add(criterion);
                    }
                }
            }
            return this;
        }
    }

    protected class HibernateProjectionList extends ProjectionList {

        org.hibernate.criterion.ProjectionList projectionList = Projections.projectionList();
        private boolean rowCount = false;

        public boolean isRowCount() {
            return rowCount;
        }

        public org.hibernate.criterion.ProjectionList getHibernateProjectionList() {
            return projectionList;
        }

        @Override
        public boolean isEmpty() {
            return projectionList.getLength() == 0;
        }

        @Override
        public ProjectionList add(Projection p) {
            projectionList.add(new HibernateProjectionAdapter(normalizeProjectionPropertyPath(p)).toHibernateProjection());
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList countDistinct(String property) {
            projectionList.add(Projections.countDistinct(calculateProjectionPropertyName(property)));
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList distinct(String property) {
            projectionList.add(Projections.distinct(Projections.property(calculateProjectionPropertyName(property))));
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList rowCount() {
            projectionList.add(Projections.rowCount());
            this.rowCount = true;
            return this;
        }

        @Override
        public ProjectionList id() {
            projectionList.add(Projections.id());
            return this;
        }

        @Override
        public ProjectionList count() {
            projectionList.add(Projections.rowCount());
            this.rowCount = true;
            return this;
        }

        @Override
        public ProjectionList property(String name) {
            projectionList.add(Projections.property(calculateProjectionPropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList sum(String name) {
            projectionList.add(Projections.sum(calculateProjectionPropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList min(String name) {
            projectionList.add(Projections.min(calculateProjectionPropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList max(String name) {
            projectionList.add(Projections.max(calculateProjectionPropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList avg(String name) {
            projectionList.add(Projections.avg(calculateProjectionPropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList distinct() {
            if (criteria != null)
                criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
            else if (detachedCriteria != null)
                detachedCriteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
            return this;
        }
    }

    protected class HibernateAssociationQuery extends AssociationQuery {

        protected String alias;
        protected org.hibernate.criterion.Junction hibernateJunction;
        protected Criteria assocationCriteria;
        protected DetachedCriteria detachedAssocationCriteria;

        public HibernateAssociationQuery(Criteria criteria, AbstractHibernateSession session, PersistentEntity associatedEntity, Association association, String alias) {
            super(session, associatedEntity, association);
            this.alias = alias;
            assocationCriteria = criteria;
        }

        public HibernateAssociationQuery(DetachedCriteria criteria, AbstractHibernateSession session, PersistentEntity associatedEntity, Association association, String alias) {
            super(session, associatedEntity, association);
            this.alias = alias;
            detachedAssocationCriteria = criteria;
        }

        @Override
        public Query order(Order order) {

            Order.Direction direction = order.getDirection();
            switch (direction) {
                case ASC:
                    assocationCriteria.addOrder(org.hibernate.criterion.Order.asc(order.getProperty()));
                case DESC:
                    assocationCriteria.addOrder(org.hibernate.criterion.Order.desc(order.getProperty()));
            }
            return super.order(order);
        }

        @Override
        public Query isEmpty(String property) {
            org.hibernate.criterion.Criterion criterion = Restrictions.isEmpty(calculatePropertyName(property));
            addToCriteria(criterion);
            return this;
        }

        protected void addToCriteria(org.hibernate.criterion.Criterion criterion) {
            if (hibernateJunction != null) {
                hibernateJunction.add(criterion);
            }
            else if (assocationCriteria != null) {
                assocationCriteria.add(criterion);
            }
            else if (detachedAssocationCriteria != null) {
                detachedAssocationCriteria.add(criterion);
            }
        }

        @Override
        public Query isNotEmpty(String property) {
            addToCriteria(Restrictions.isNotEmpty(calculatePropertyName(property)));
            return this;
        }

        @Override
        public Query isNull(String property) {
            addToCriteria(Restrictions.isNull(calculatePropertyName(property)));
            return this;
        }

        @Override
        public Query isNotNull(String property) {
            addToCriteria(Restrictions.isNotNull(calculatePropertyName(property)));
            return this;
        }

        @Override
        public void add(Criterion criterion) {
            final org.hibernate.criterion.Criterion hibernateCriterion = getHibernateCriterionAdapter().toHibernateCriterion(AbstractHibernateQuery.this, criterion, alias);
            if (hibernateCriterion != null) {
                addToCriteria(hibernateCriterion);
            }
        }

        @Override
        public Junction disjunction() {
            final org.hibernate.criterion.Disjunction disjunction = Restrictions.disjunction();
            addToCriteria(disjunction);
            return new HibernateJunction(disjunction, alias);
        }

        @Override
        public Junction negation() {
            final org.hibernate.criterion.Disjunction disjunction = Restrictions.disjunction();
            addToCriteria(Restrictions.not(disjunction));
            return new HibernateJunction(disjunction, alias);
        }

        @Override
        public Query eq(String property, Object value) {
            addToCriteria(Restrictions.eq(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query idEq(Object value) {
            addToCriteria(Restrictions.idEq(value));
            return this;
        }

        @Override
        public Query gt(String property, Object value) {
            addToCriteria(Restrictions.gt(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query and(Criterion a, Criterion b) {
            AbstractHibernateCriterionAdapter adapter = getHibernateCriterionAdapter();
            addToCriteria(Restrictions.and(adapter.toHibernateCriterion(AbstractHibernateQuery.this, a, alias), adapter.toHibernateCriterion(AbstractHibernateQuery.this, b, alias)));
            return this;
        }

        @Override
        public Query or(Criterion a, Criterion b) {
            AbstractHibernateCriterionAdapter adapter = getHibernateCriterionAdapter();
            addToCriteria(Restrictions.or(adapter.toHibernateCriterion(AbstractHibernateQuery.this, a, alias), adapter.toHibernateCriterion(AbstractHibernateQuery.this, b, alias)));
            return this;
        }

        @Override
        public Query allEq(Map<String, Object> values) {
            addToCriteria(Restrictions.allEq(values));
            return this;
        }

        @Override
        public Query ge(String property, Object value) {
            addToCriteria(Restrictions.ge(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query le(String property, Object value) {
            addToCriteria(Restrictions.le(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query gte(String property, Object value) {
            addToCriteria(Restrictions.ge(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query lte(String property, Object value) {
            addToCriteria(Restrictions.le(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query lt(String property, Object value) {
            addToCriteria(Restrictions.lt(calculatePropertyName(property), value));
            return this;
        }

        @Override
        public Query in(String property, List values) {
            addToCriteria(Restrictions.in(calculatePropertyName(property), values));
            return this;
        }

        @Override
        public Query between(String property, Object start, Object end) {
            addToCriteria(Restrictions.between(calculatePropertyName(property), start, end));
            return this;
        }

        @Override
        public Query like(String property, String expr) {
            addToCriteria(Restrictions.like(calculatePropertyName(property), calculatePropertyName(expr)));
            return this;
        }

        @Override
        public Query ilike(String property, String expr) {
            addToCriteria(Restrictions.ilike(calculatePropertyName(property), calculatePropertyName(expr)));
            return this;
        }

        @Override
        public Query rlike(String property, String expr) {
            addToCriteria(createRlikeExpression(calculatePropertyName(property), calculatePropertyName(expr)));
            return this;
        }
    }

    protected class CriteriaAndAlias {
        protected DetachedCriteria detachedCriteria;
        protected Criteria criteria;
        protected String alias;
        protected String associationPath;
        /**
         * Whether criteria of the query use the join, which makes it an inner join unless the query
         * requests another join type for the association.
         */
        private boolean usedByCriteria;

        public CriteriaAndAlias(DetachedCriteria detachedCriteria, String alias, String associationPath) {
            this.detachedCriteria = detachedCriteria;
            this.alias = alias;
            this.associationPath = associationPath;
        }

        public CriteriaAndAlias(Criteria criteria, String alias, String associationPath) {
            this.criteria = criteria;
            this.alias = alias;
            this.associationPath = associationPath;
        }
    }
}
