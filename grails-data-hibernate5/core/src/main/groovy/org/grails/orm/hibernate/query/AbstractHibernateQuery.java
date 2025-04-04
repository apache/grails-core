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

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.JoinType;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;
import org.hibernate.*;
import org.hibernate.criterion.*;
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
    private static final Map<String, Boolean> JOIN_STATUS_CACHE = new ConcurrentHashMap<String, Boolean>();
    static {
        ReflectionUtils.makeAccessible(opField);
    }

    protected Criteria criteria;
    protected org.hibernate.criterion.DetachedCriteria detachedCriteria;
    protected AbstractHibernateQuery.HibernateProjectionList hibernateProjectionList;
    protected String alias;
    protected int aliasCount;
    protected Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<String, CriteriaAndAlias>();
    protected LinkedList<String> aliasStack = new LinkedList<String>();
    protected LinkedList<PersistentEntity> entityStack = new LinkedList<PersistentEntity>();
    protected LinkedList<Association> associationStack = new LinkedList<Association>();
    protected LinkedList aliasInstanceStack = new LinkedList();
    private boolean hasJoins = false;
    protected ProxyHandler proxyHandler = new HibernateProxyHandler();
    protected final AbstractHibernateCriterionAdapter abstractHibernateCriterionAdapter;

    protected AbstractHibernateQuery(Criteria criteria, AbstractHibernateSession session, PersistentEntity entity) {
        super(session, entity);
        this.criteria = criteria;
        if(entity != null) {
            initializeJoinStatus();
        }
        this.abstractHibernateCriterionAdapter = createHibernateCriterionAdapter();
    }

    protected AbstractHibernateQuery(DetachedCriteria criteria, PersistentEntity entity) {
        super(null, entity);
        this.detachedCriteria = criteria;
        this.abstractHibernateCriterionAdapter = createHibernateCriterionAdapter();
        if(entity != null) {
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
        if(cachedStatus != null) hasJoins = cachedStatus;
        else {
            for(Association a : entity.getAssociations()) {
                if( a.getFetchStrategy() == FetchType.EAGER ) hasJoins = true;
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

            if(association instanceof Embedded) {
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

                if(criteriaAndAlias.criteria != null) {
                    aliasInstanceStack.add(criteriaAndAlias.criteria);
                }
                else if(criteriaAndAlias.detachedCriteria != null) {
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

        SessionFactory sessionFactory = ((IHibernateTemplate)session.getNativeInterface()).getSessionFactory();
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
                final org.hibernate.criterion.Criterion hibernateCriterion = getHibernateCriterionAdapter().toHibernateCriterion(this, datastoreCriterion,alias);
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
                    throw new InvalidDataAccessResourceUsageException("Unsupported function ["+functionName+"] defined in query for property ["+property+"] with type ["+pp.getType()+"]");
                }
            }
            else {
                throw new InvalidDataAccessResourceUsageException("Unsupported function ["+functionName+"] defined in query for property ["+property+"] with type ["+pp.getType()+"]");
            }
        }
        else {
            throw new InvalidDataAccessResourceUsageException("Unsupported function defined in query ["+functionName+"]");
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
            String alias = generateAlias(associationName);
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias);

            Association association = (Association) property;
            if(subCriteria.criteria != null) {
                return new HibernateAssociationQuery(subCriteria.criteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, alias);
            }
            else if(subCriteria.detachedCriteria != null) {
                return new HibernateAssociationQuery(subCriteria.detachedCriteria, (AbstractHibernateSession) getSession(), association.getAssociatedEntity(), association, alias);
            }
        }
        throw new InvalidDataAccessApiUsageException("Cannot query association [" + calculatePropertyName(associationName) + "] of entity [" + entity + "]. Property is not an association!");
    }

    protected CriteriaAndAlias getCriteriaAndAlias(DetachedAssociationCriteria associationCriteria) {
        String associationPath = associationCriteria.getAssociationPath();
        String alias = associationCriteria.getAlias();

        if(associationPath == null) {
            associationPath = associationCriteria.getAssociation().getName();
        }
        return getOrCreateAlias(associationPath, alias);
    }

    protected CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        CriteriaAndAlias subCriteria = null;
        String associationPath = getAssociationPath(associationName);
        Criteria parentCriteria = criteria;
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
                Criteria sc = parentCriteria.createAlias(associationPath, alias, resolveJoinType(joinType));
                subCriteria = new CriteriaAndAlias(sc, alias, associationPath);
            }
            else if(detachedCriteria != null) {
                DetachedCriteria sc = detachedCriteria.createAlias(associationPath, alias, resolveJoinType(joinType));
                subCriteria = new CriteriaAndAlias(sc, alias, associationPath);
            }
            if(subCriteria != null) {

                createdAssociationPaths.put(associationPath,subCriteria);
                createdAssociationPaths.put(alias,subCriteria);
            }
        }
        return subCriteria;
    }

    private org.hibernate.sql.JoinType resolveJoinType(JoinType joinType) {
        if(joinType  == null) {
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
        if(criteria != null)
            criteria.setMaxResults(max);
        return this;
    }

    @Override
    public Query maxResults(int max) {
        if(criteria != null)
            criteria.setMaxResults(max);
        return this;
    }

    @Override
    public Query offset(int offset) {
        if(criteria != null)
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

        String property = order.getProperty();

        int i = property.indexOf('.');
        if(i > -1) {

            String sortHead = property.substring(0,i);
            String sortTail = property.substring(i + 1);

            if(createdAssociationPaths.containsKey(sortHead)) {
                CriteriaAndAlias criteriaAndAlias = createdAssociationPaths.get(sortHead);
                Criteria criteria = criteriaAndAlias.criteria;
                org.hibernate.criterion.Order hibernateOrder = order.getDirection() == Order.Direction.ASC ?
                        org.hibernate.criterion.Order.asc(property) :
                        org.hibernate.criterion.Order.desc(property);

                criteria.addOrder(order.isIgnoreCase() ? hibernateOrder.ignoreCase() : hibernateOrder);
            }
            else {

                PersistentProperty persistentProperty = entity.getPropertyByName(sortHead);

                if(persistentProperty instanceof Association) {
                    Association a = (Association) persistentProperty;
                    if(persistentProperty instanceof Embedded) {
                        addSimpleOrder(order, property);
                    }
                    else {
                        if(criteria != null) {
                            Criteria subCriteria = criteria.createCriteria(sortHead);
                            addOrderToCriteria(subCriteria, sortTail, order);
                        }
                        else if(detachedCriteria != null) {
                            DetachedCriteria subDetachedCriteria = detachedCriteria.createCriteria(sortHead);
                            addOrderToDetachedCriteria(subDetachedCriteria, sortTail, order);
                        }
                    }
                }
            }


        }
        else {
            addSimpleOrder(order, property);
        }

        return this;
    }

    private void addSimpleOrder(Order order, String property) {
        Criteria c = criteria;
        if(c != null) {
            addOrderToCriteria(c, property, order);
        }else {
            DetachedCriteria dc = detachedCriteria;
            addOrderToDetachedCriteria(dc, property, order);
        }
    }

    private void addOrderToDetachedCriteria(DetachedCriteria dc, String property, Order order) {
        if(dc != null) {
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

    @Override
    public Query join(String property) {
        this.hasJoins = true;
        if(criteria != null)
            criteria.setFetchMode(property, FetchMode.JOIN);
        else if(detachedCriteria != null)
            detachedCriteria.setFetchMode(property, FetchMode.JOIN);
        return this;
    }

    @Override
    public Query select(String property) {
        this.hasJoins = true;
        if(criteria != null)
            criteria.setFetchMode(property, FetchMode.SELECT);
        else if(detachedCriteria != null)
            detachedCriteria.setFetchMode(property, FetchMode.SELECT);
        return this;
    }

    @Override
    public List list() {
        if(criteria == null) throw new IllegalStateException("Cannot execute query using a detached criteria instance");

        int projectionLength = 0;
        if (hibernateProjectionList != null) {
            org.hibernate.criterion.ProjectionList projectionList = hibernateProjectionList.getHibernateProjectionList();
            projectionLength = projectionList.getLength();
            if(projectionLength > 0) {
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
        if(publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this));
        }

        List results = criteria.list();
        if(publisher != null) {
            publisher.publishEvent(new PostQueryEvent(datastore, this, results));
        }
        return results;
    }

    protected void applyDefaultSortOrderAndCaching() {
        if(this.orderBy.isEmpty() && entity != null) {
            // don't apply default sorting, if projections present
            if(hibernateProjectionList != null && !hibernateProjectionList.isEmpty()) return;

            Mapping mapping = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if(mapping != null) {
                if(queryCache == null && mapping.getCache() != null && mapping.getCache().isEnabled()) {
                    criteria.setCacheable(true);
                }

                Map sortMap = mapping.getSort().getNamesAndDirections();
                DynamicFinder.applySortForMap(this, sortMap, true);

            }
        }
    }

    protected void applyFetchStrategies() {
        for (Map.Entry<String, FetchType> entry : fetchStrategies.entrySet()) {
            switch(entry.getValue()) {
                case EAGER:
                    if(criteria != null)
                        criteria.setFetchMode(entry.getKey(), FetchMode.JOIN);
                    else if(detachedCriteria != null)
                        detachedCriteria.setFetchMode(entry.getKey(), FetchMode.JOIN);
                    break;
                case LAZY:
                    if(criteria != null)
                        criteria.setFetchMode(entry.getKey(), FetchMode.SELECT);
                    else if(detachedCriteria != null)
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
        if(criteria == null) throw new IllegalStateException("Cannot execute query using a detached criteria instance");

        if (hibernateProjectionList != null) {
            criteria.setProjection(hibernateProjectionList.getHibernateProjectionList());
        }
        criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
        applyDefaultSortOrderAndCaching();
        applyFetchStrategies();

        Datastore datastore = session.getDatastore();
        ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
        if(publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this));
        }

        Object result;
        if(hasJoins) {
            try {
                result = proxyHandler.unwrap(criteria.uniqueResult());;
            } catch (NonUniqueResultException e) {
                result = singleResultViaListCall();
            }
        }
        else {
            result = singleResultViaListCall();
        }
        if(publisher != null) {
            publisher.publishEvent(new PostQueryEvent(datastore, this, Collections.singletonList(result)));
        }
        return result;
    }

    private Object singleResultViaListCall() {
        criteria.setMaxResults(1);
        if(hibernateProjectionList != null && hibernateProjectionList.isRowCount()) {
            criteria.setFirstResult(0);
        }
        List results = criteria.list();
        if(results.size()>0) {
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
            if(criteria != null) {
                criteria.add(criterion);

            }
            else if(detachedCriteria != null) {
                detachedCriteria.add(criterion);
            }
        }
        else {
            Object criteriaObject = aliasInstanceStack.getLast();
            if(criteriaObject instanceof Criteria)
                ((Criteria)criteriaObject).add(criterion);
            else if(criteriaObject instanceof DetachedCriteria) {
                ((DetachedCriteria)criteriaObject).add(criterion);
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
            projectionList.add(new HibernateProjectionAdapter(p).toHibernateProjection());
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList countDistinct(String property) {
            projectionList.add(Projections.countDistinct(calculatePropertyName(property)));
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList distinct(String property) {
            projectionList.add(Projections.distinct(Projections.property(calculatePropertyName(property))));
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
            projectionList.add(Projections.property(calculatePropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList sum(String name) {
            projectionList.add(Projections.sum(calculatePropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList min(String name) {
            projectionList.add(Projections.min(calculatePropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList max(String name) {
            projectionList.add(Projections.max(calculatePropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList avg(String name) {
            projectionList.add(Projections.avg(calculatePropertyName(name)));
            return this;
        }

        @Override
        public ProjectionList distinct() {
            if(criteria != null)
                criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
            else if(detachedCriteria != null)
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
            switch(direction) {
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
           else if(assocationCriteria != null) {
               assocationCriteria.add(criterion);
           }
           else if(detachedAssocationCriteria != null) {
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
