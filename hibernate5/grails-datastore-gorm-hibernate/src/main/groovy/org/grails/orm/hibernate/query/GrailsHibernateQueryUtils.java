package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.cfg.AbstractGrailsDomainBinder;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.criterion.Order;
import org.hibernate.query.Query;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.ReflectionUtils;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.*;
import java.lang.reflect.Method;
import java.util.Map;


/**
 * Utility methods for configuring Hibernate queries
 *
 * @author Graeme Rocher
 * @since 4.0
 */
public class GrailsHibernateQueryUtils {

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param c      The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    @Deprecated
    public static void populateArgumentsForCriteria(PersistentEntity entity, Criteria c, Map argMap, ConversionService conversionService, boolean useDefaultMapping) {
        Integer maxParam = null;
        Integer offsetParam = null;
        if (argMap.containsKey(DynamicFinder.ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_OFFSET), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            c.setFetchSize(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_FETCH_SIZE), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            c.setTimeout(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_TIMEOUT), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            c.setFlushMode(convertFlushMode(argMap.get(DynamicFinder.ARGUMENT_FLUSH_MODE)));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            c.setReadOnly(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_READ_ONLY, argMap));
        }
        String orderParam = (String) argMap.get(DynamicFinder.ARGUMENT_ORDER);
        Object fetchObj = argMap.get(DynamicFinder.ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                c.setFetchMode(associationName, getFetchMode(fetch.get(associationName)));
            }
        }

        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            c.setMaxResults(max);
        }
        if (offset > -1) {
            c.setFirstResult(offset);
        }
        if (ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_LOCK, argMap)) {
            c.setLockMode(LockMode.PESSIMISTIC_WRITE);
            c.setCacheable(false);
        } else {
            if (argMap.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
                c.setCacheable(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, argMap));
            } else {
                cacheCriteriaByMapping(entity.getJavaClass(), c);
            }
        }

        final Object sortObj = argMap.get(DynamicFinder.ARGUMENT_SORT);
        if (sortObj != null) {
            boolean ignoreCase = true;
            Object caseArg = argMap.get(DynamicFinder.ARGUMENT_IGNORE_CASE);
            if (caseArg instanceof Boolean) {
                ignoreCase = (Boolean) caseArg;
            }
            if (sortObj instanceof Map) {
                Map sortMap = (Map) sortObj;
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(c, entity, (String) sort, order, ignoreCase);
                }
            } else {
                final String sort = (String) sortObj;
                final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase(orderParam) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                addOrderPossiblyNested(c, entity, sort, order, ignoreCase);
            }
        } else if (useDefaultMapping) {
            Mapping m = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if (m != null) {
                Map sortMap = m.getSort().getNamesAndDirections();
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(c, entity, (String) sort, order, true);
                }
            }
        }
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param query  The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(
            PersistentEntity entity,
            CriteriaQuery query,
            Root queryRoot,
            CriteriaBuilder criteriaBuilder,
            Map argMap,
            ConversionService conversionService,
            boolean useDefaultMapping) {
        String orderParam = (String) argMap.get(DynamicFinder.ARGUMENT_ORDER);
        Object fetchObj = argMap.get(DynamicFinder.ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;

                final FetchMode fetchMode = getFetchMode(fetch.get(associationName));
                if (fetchMode == FetchMode.JOIN) {
                    queryRoot.join(associationName);
                }
            }
        }

        final Object sortObj = argMap.get(DynamicFinder.ARGUMENT_SORT);
        if (sortObj != null) {
            boolean ignoreCase = true;
            Object caseArg = argMap.get(DynamicFinder.ARGUMENT_IGNORE_CASE);
            if (caseArg instanceof Boolean) {
                ignoreCase = (Boolean) caseArg;
            }
            if (sortObj instanceof Map) {
                Map sortMap = (Map) sortObj;
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(query,queryRoot, criteriaBuilder, entity, (String) sort, order, ignoreCase);
                }
            } else {
                final String sort = (String) sortObj;
                final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase(orderParam) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                addOrderPossiblyNested(query, queryRoot, criteriaBuilder,entity, sort, order, ignoreCase);
            }
        } else if (useDefaultMapping) {
            Mapping m = AbstractGrailsDomainBinder.getMapping(entity.getJavaClass());
            if (m != null) {
                Map sortMap = m.getSort().getNamesAndDirections();
                for (Object sort : sortMap.keySet()) {
                    final String order = DynamicFinder.ORDER_DESC.equalsIgnoreCase((String) sortMap.get(sort)) ? DynamicFinder.ORDER_DESC : DynamicFinder.ORDER_ASC;
                    addOrderPossiblyNested(query,queryRoot, criteriaBuilder, entity, (String) sort, order, true);
                }
            }
        }
    }

    /**
     * Populates criteria arguments for the given target class and arguments map
     *
     * @param entity The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     * @param query  The criteria instance
     * @param argMap The arguments map
     */
    @SuppressWarnings("rawtypes")
    public static void populateArgumentsForCriteria(
            PersistentEntity entity,
            Query query,
            Map argMap,
            ConversionService conversionService,
            boolean useDefaultMapping) {
        Integer maxParam = null;
        Integer offsetParam = null;
        if (argMap.containsKey(DynamicFinder.ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_OFFSET), Integer.class);
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            query.setFetchSize(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_FETCH_SIZE), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            query.setTimeout(conversionService.convert(argMap.get(DynamicFinder.ARGUMENT_TIMEOUT), Integer.class));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            query.setHibernateFlushMode(convertFlushMode(argMap.get(DynamicFinder.ARGUMENT_FLUSH_MODE)));
        }
        if (argMap.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            query.setReadOnly(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_READ_ONLY, argMap));
        }

        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            query.setMaxResults(max);
        }
        if (offset > -1) {
            query.setFirstResult(offset);
        }
        if (ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_LOCK, argMap)) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            query.setCacheable(false);
        } else {
            if (argMap.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
                query.setCacheable(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, argMap));
            } else {
                cacheCriteriaByMapping(entity.getJavaClass(), query);
            }
        }

    }

    /**
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     */
    private static void addOrderPossiblyNested(Criteria c, PersistentEntity entity, String sort, String order, boolean ignoreCase) {
        int firstDotPos = sort.indexOf(".");
        if (firstDotPos == -1) {
            addOrder(c, sort, order, ignoreCase);
        } else { // nested property
            String sortHead = sort.substring(0, firstDotPos);
            String sortTail = sort.substring(firstDotPos + 1);
            PersistentProperty property = entity.getPropertyByName(sortHead);
            if (property instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so no more recursion needed
                addOrder(c, sort, order, ignoreCase);
            } else if (property instanceof Association) {
                Association a = (Association) property;
                Criteria subCriteria = c.createCriteria(sortHead);
                PersistentEntity associatedEntity = a.getAssociatedEntity();
                Class<?> propertyTargetClass = associatedEntity.getJavaClass();
                cacheCriteriaByMapping(propertyTargetClass, subCriteria);
                addOrderPossiblyNested(subCriteria, associatedEntity, sortTail, order, ignoreCase); // Recurse on nested sort
            }
        }
    }


    /**
     * Add order to criteria, creating necessary subCriteria if nested sort property (ie. sort:'nested.property').
     */
    private static void addOrderPossiblyNested(CriteriaQuery query,
                                               From queryRoot,
                                               CriteriaBuilder criteriaBuilder,
                                               PersistentEntity entity,
                                               String sort,
                                               String order,
                                               boolean ignoreCase) {
        int firstDotPos = sort.indexOf(".");
        if (firstDotPos == -1) {
            final PersistentProperty<? extends Property> property = entity.getPropertyByName(sort);
            ignoreCase = isIgnoreCaseProperty(ignoreCase, property);
            addOrder(entity, query, queryRoot, criteriaBuilder, sort, order, ignoreCase);
        } else { // nested property
            String sortHead = sort.substring(0, firstDotPos);
            String sortTail = sort.substring(firstDotPos + 1);
            final PersistentProperty<? extends Property> property = entity.getPropertyByName(sortHead);
            if (property instanceof Embedded) {
                // embedded objects cannot reference entities (at time of writing), so no more recursion needed
                final PersistentProperty<? extends Property> associatedProperty = ((Embedded<?>) property).getAssociatedEntity().getPropertyByName(sortTail);
                ignoreCase = isIgnoreCaseProperty(ignoreCase, associatedProperty);
                addOrder(entity, query, queryRoot, criteriaBuilder, sort, order, ignoreCase);
            } else if (property instanceof Association) {
                final Association<? extends Property> a = (Association<? extends Property>) property;
                final Join join = queryRoot.join(sortHead);
                PersistentEntity associatedEntity = a.getAssociatedEntity();
                Class<?> propertyTargetClass = associatedEntity.getJavaClass();
                addOrderPossiblyNested(query, join, criteriaBuilder, associatedEntity, sortTail, order, ignoreCase); // Recurse on nested sort
            }
        }
    }

    private static boolean isIgnoreCaseProperty(boolean ignoreCase, PersistentProperty<? extends Property> persistentProperty) {
        if (ignoreCase && persistentProperty != null && persistentProperty.getType() != String.class) {
            ignoreCase = false;
        }
        return ignoreCase;
    }

    /**
     * Add order directly to criteria.
     */
    private static void addOrder(PersistentEntity entity,
                                 CriteriaQuery query,
                                 From queryRoot,
                                 CriteriaBuilder criteriaBuilder,
                                 String sort, String order, boolean ignoreCase) {
        if (sort.equalsIgnoreCase(entity.getIdentity().getName())) {
            Expression path = queryRoot;

            if (ignoreCase) {
                path = criteriaBuilder.upper(path);
            }
            if (DynamicFinder.ORDER_DESC.equals(order)) {
                query.orderBy(criteriaBuilder.desc(path));
            } else {
                query.orderBy(criteriaBuilder.asc(path));
            }
        } else {
            Expression path = queryRoot.get(sort);

            if (ignoreCase) {
                path = criteriaBuilder.upper(path);
            }
            if (DynamicFinder.ORDER_DESC.equals(order)) {
                query.orderBy(criteriaBuilder.desc(path));
            } else {
                query.orderBy(criteriaBuilder.asc(path));
            }
        }
    }

    /**
     * Configures the criteria instance to cache based on the configured mapping.
     *
     * @param targetClass The target class
     * @param criteria    The criteria
     */
    private static void cacheCriteriaByMapping(Class<?> targetClass, Criteria criteria) {
        Mapping m = AbstractGrailsDomainBinder.getMapping(targetClass);
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
            criteria.setCacheable(true);
        }
    }

    /**
     * Configures the criteria instance to cache based on the configured mapping.
     *
     * @param targetClass The target class
     * @param criteria    The criteria
     */
    private static void cacheCriteriaByMapping(Class<?> targetClass, Query criteria) {
        Mapping m = AbstractGrailsDomainBinder.getMapping(targetClass);
        if (m != null && m.getCache() != null && m.getCache().getEnabled()) {
            criteria.setCacheable(true);
        }
    }

    private static FlushMode convertFlushMode(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof FlushMode) {
            return (FlushMode) object;
        }
        try {
            return FlushMode.valueOf(object.toString());
        } catch (IllegalArgumentException e) {
            return FlushMode.COMMIT;
        }
    }

    /**
     * Add order directly to criteria.
     */
    private static void addOrder(Criteria c, String sort, String order, boolean ignoreCase) {
        if (DynamicFinder.ORDER_DESC.equals(order)) {
            c.addOrder(ignoreCase ? Order.desc(sort).ignoreCase() : Order.desc(sort));
        } else {
            c.addOrder(ignoreCase ? Order.asc(sort).ignoreCase() : Order.asc(sort));
        }
    }

    /**
     * Retrieves the fetch mode for the specified instance; otherwise returns the default FetchMode.
     *
     * @param object The object, converted to a string
     * @return The FetchMode
     */
    public static FetchMode getFetchMode(Object object) {
        String name = object != null ? object.toString() : "default";
        if (name.equalsIgnoreCase(FetchMode.JOIN.toString()) || name.equalsIgnoreCase("eager")) {
            return FetchMode.JOIN;
        }
        if (name.equalsIgnoreCase(FetchMode.SELECT.toString()) || name.equalsIgnoreCase("lazy")) {
            return FetchMode.SELECT;
        }
        return FetchMode.DEFAULT;
    }

}
