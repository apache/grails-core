/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.orm;

import grails.gorm.DetachedCriteria;
import grails.gorm.MultiTenant;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaMethod;
import groovy.lang.MissingMethodException;
import groovy.util.logging.Slf4j;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.BuildableCriteria;
import org.grails.datastore.mapping.query.api.Criteria;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.grails.orm.hibernate.query.HibernateQueryConstants;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.transform.ResultTransformer;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.StandardBasicTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.grails.datastore.mapping.query.api.ProjectionList;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

/**
 * <p>Wraps the Hibernate Criteria API in a builder. The builder can be retrieved through the "createCriteria()" dynamic static
 * method of Grails domain classes (Example in Groovy):
 * <pre>
 *         def c = Account.createCriteria()
 *         def results = c {
 *             projections {
 *                 groupProperty("branch")
 *             }
 *             like("holderFirstName", "Fred%")
 *             and {
 *                 between("balance", 500, 1000)
 *                 eq("branch", "London")
 *             }
 *             maxResults(10)
 *             order("holderLastName", "desc")
 *         }
 * </pre>
 * <p>The builder can also be instantiated standalone with a SessionFactory and persistent Class instance:
 * <pre>
 *      new HibernateCriteriaBuilder(clazz, sessionFactory).list {
 *         eq("firstName", "Fred")
 *      }
 * </pre>
 *
 * @author Graeme Rocher
 */
@Slf4j
public class HibernateCriteriaBuilder extends GroovyObjectSupport implements BuildableCriteria, ProjectionList {
    /*
     * Define constants which may be used inside of criteria queries
     * to refer to standard Hibernate Type instances.
     */
    public static final BasicTypeReference<Boolean> BOOLEAN = StandardBasicTypes.BOOLEAN;
    public static final BasicTypeReference<Boolean> YES_NO = StandardBasicTypes.YES_NO;
    public static final BasicTypeReference<Byte> BYTE = StandardBasicTypes.BYTE;
    public static final BasicTypeReference<Character> CHARACTER = StandardBasicTypes.CHARACTER;
    public static final BasicTypeReference<Short> SHORT = StandardBasicTypes.SHORT;
    public static final BasicTypeReference<Integer> INTEGER = StandardBasicTypes.INTEGER;
    public static final BasicTypeReference<Long> LONG = StandardBasicTypes.LONG;
    public static final BasicTypeReference<Float> FLOAT = StandardBasicTypes.FLOAT;
    public static final BasicTypeReference<Double> DOUBLE = StandardBasicTypes.DOUBLE;
    public static final BasicTypeReference<BigDecimal> BIG_DECIMAL = StandardBasicTypes.BIG_DECIMAL;
    public static final BasicTypeReference<BigInteger> BIG_INTEGER = StandardBasicTypes.BIG_INTEGER;
    public static final BasicTypeReference<String> STRING = StandardBasicTypes.STRING;
    public static final BasicTypeReference<Boolean> NUMERIC_BOOLEAN = StandardBasicTypes.NUMERIC_BOOLEAN;
    public static final BasicTypeReference<Boolean> TRUE_FALSE = StandardBasicTypes.TRUE_FALSE;
    public static final BasicTypeReference<java.net.URL> URL = StandardBasicTypes.URL;
    public static final BasicTypeReference<Date> TIME = StandardBasicTypes.TIME;
    public static final BasicTypeReference<Date> DATE = StandardBasicTypes.DATE;
    public static final BasicTypeReference<Date> TIMESTAMP = StandardBasicTypes.TIMESTAMP;
    public static final BasicTypeReference<Calendar> CALENDAR = StandardBasicTypes.CALENDAR;
    public static final BasicTypeReference<Calendar> CALENDAR_DATE = StandardBasicTypes.CALENDAR_DATE;
    public static final BasicTypeReference<Class> CLASS = StandardBasicTypes.CLASS;
    public static final BasicTypeReference<Locale> LOCALE = StandardBasicTypes.LOCALE;
    public static final BasicTypeReference<Currency> CURRENCY = StandardBasicTypes.CURRENCY;
    public static final BasicTypeReference<TimeZone> TIMEZONE = StandardBasicTypes.TIMEZONE;
    public static final BasicTypeReference<UUID> UUID_BINARY = StandardBasicTypes.UUID_BINARY;
    public static final BasicTypeReference<UUID> UUID_CHAR = StandardBasicTypes.UUID_CHAR;
    public static final BasicTypeReference<byte[]> BINARY = StandardBasicTypes.BINARY;
    public static final BasicTypeReference<Byte[]> WRAPPER_BINARY = StandardBasicTypes.WRAPPER_BINARY;
    public static final BasicTypeReference<byte[]> IMAGE = StandardBasicTypes.IMAGE;
    public static final BasicTypeReference<Blob> BLOB = StandardBasicTypes.BLOB;
    public static final BasicTypeReference<byte[]> MATERIALIZED_BLOB = StandardBasicTypes.MATERIALIZED_BLOB;
    public static final BasicTypeReference<char[]> CHAR_ARRAY = StandardBasicTypes.CHAR_ARRAY;
    public static final BasicTypeReference<Character[]> CHARACTER_ARRAY = StandardBasicTypes.CHARACTER_ARRAY;
    public static final BasicTypeReference<String> TEXT = StandardBasicTypes.TEXT;
    public static final BasicTypeReference<Clob> CLOB = StandardBasicTypes.CLOB;
    public static final BasicTypeReference<String> MATERIALIZED_CLOB = StandardBasicTypes.MATERIALIZED_CLOB;
    public static final BasicTypeReference<Serializable> SERIALIZABLE = StandardBasicTypes.SERIALIZABLE;

    public static final String AND = "and"; // builder
    public static final String IS_NULL = "isNull"; // builder
    public static final String IS_NOT_NULL = "isNotNull"; // builder
    public static final String NOT = "not";// builder
    public static final String OR = "or"; // builder
    public static final String ID_EQUALS = "idEq"; // builder
    public static final String IS_EMPTY = "isEmpty"; //builder
    public static final String IS_NOT_EMPTY = "isNotEmpty"; //builder
    public static final String RLIKE = "rlike";//method
    public static final String BETWEEN = "between";//method
    public static final String EQUALS = "eq";//method
    public static final String EQUALS_PROPERTY = "eqProperty";//method
    public static final String GREATER_THAN = "gt";//method
    public static final String GREATER_THAN_PROPERTY = "gtProperty";//method
    public static final String GREATER_THAN_OR_EQUAL = "ge";//method
    public static final String GREATER_THAN_OR_EQUAL_PROPERTY = "geProperty";//method
    public static final String ILIKE = "ilike";//method
    public static final String IN = "in";//method
    public static final String LESS_THAN = "lt"; //method
    public static final String LESS_THAN_PROPERTY = "ltProperty";//method
    public static final String LESS_THAN_OR_EQUAL = "le";//method
    public static final String LESS_THAN_OR_EQUAL_PROPERTY = "leProperty";//method
    public static final String LIKE = "like";//method
    public static final String NOT_EQUAL = "ne";//method
    public static final String NOT_EQUAL_PROPERTY = "neProperty";//method
    public static final String SIZE_EQUALS = "sizeEq"; //method
    public static final String ORDER_DESCENDING = "desc";
    public static final String ORDER_ASCENDING = "asc";
    protected static final String ROOT_DO_CALL = "doCall";
    protected static final String ROOT_CALL = "call";
    protected static final String LIST_CALL = "list";
    protected static final String LIST_DISTINCT_CALL = "listDistinct";
    protected static final String COUNT_CALL = "count";
    protected static final String GET_CALL = "get";
    protected static final String SCROLL_CALL = "scroll";
    protected static final String SET_RESULT_TRANSFORMER_CALL = "setResultTransformer";
    protected static final String PROJECTIONS = "projections";
    private static final Logger log = LoggerFactory.getLogger(HibernateCriteriaBuilder.class);


    protected SessionFactory sessionFactory;
    protected Session hibernateSession;
    protected Class<?> targetClass;
    protected CriteriaQuery criteriaQuery;
    protected boolean uniqueResult = false;
    protected boolean participate;
    protected boolean scroll;
    protected boolean count;
    protected Query.ProjectionList projectionList = new Query.ProjectionList();
    protected List<String> aliasStack = new ArrayList<String>();
    protected Map<String, String> aliasMap = new HashMap<String, String>();
    protected static final String ALIAS = "_alias";
    protected ResultTransformer resultTransformer;
    protected int aliasCount;
    protected boolean paginationEnabledList = false;
    protected ConversionService conversionService;
    protected int defaultFlushMode;
    protected AbstractHibernateDatastore datastore;
    protected org.hibernate.query.criteria.HibernateCriteriaBuilder cb;
    protected Root root;
    protected Subquery subquery;
    protected HibernateQuery hibernateQuery;
    private boolean shouldLock;
    private boolean shouldCache;
    private boolean readOnly;
    private boolean distinct = false;

    @SuppressWarnings("rawtypes")
    public HibernateCriteriaBuilder(Class targetClass, SessionFactory sessionFactory, AbstractHibernateDatastore datastore) {
        this.targetClass = targetClass;
        setDatastore(datastore);
        this.sessionFactory = sessionFactory;
        this.cb = sessionFactory.getCriteriaBuilder();
        AbstractHibernateSession session =(AbstractHibernateSession) datastore.connect();
        hibernateQuery = new HibernateQuery(session, datastore.getMappingContext().getPersistentEntity(targetClass.getTypeName()));
        setDefaultFlushMode(GrailsHibernateTemplate.FLUSH_AUTO);
    }

    public void setDatastore(AbstractHibernateDatastore datastore) {
        this.datastore = datastore;
        if(MultiTenant.class.isAssignableFrom(targetClass) && datastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR ) {
            datastore.enableMultiTenancyFilter();
        }
    }

    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * A projection that selects a property name
     * @param propertyName The name of the property
     */
    public ProjectionList property(String propertyName) {
        hibernateQuery.projections().property(propertyName);
        return this;
    }

    public Query.ProjectionList projections() {
       return  hibernateQuery.projections();
    }


    /**
     * A projection that selects a distince property name
     * @param propertyName The property name
     */
    public ProjectionList distinct(String propertyName) {
        hibernateQuery.projections().distinct(propertyName);
        return this;
    }


    /**
     * Adds a projection that allows the criteria to return the property average value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList avg(String propertyName) {
        hibernateQuery.projections().avg(propertyName);
        return this;
    }

    /**
     * Use a join query
     *
     * @param associationPath The path of the association
     */
    public BuildableCriteria join(String associationPath) {
        join(associationPath,JoinType.INNER);
        return this;
    }

    public BuildableCriteria join(String property, JoinType joinType) {
        hibernateQuery.join(property,joinType);
        return this;
    }


    /**
     * Whether a pessimistic lock should be obtained.
     *
     * @param shouldLock True if it should
     */
    public void lock(boolean shouldLock) {
        this.shouldLock = shouldLock;
    }

    /**
     * Use a select query
     *
     * @param associationPath The path of the association
     */
    public BuildableCriteria select(String associationPath) {
        hibernateQuery.select(associationPath);
        return this;
    }

    /**
     * Whether to use the query cache
     * @param shouldCache True if the query should be cached
     */
    public BuildableCriteria cache(boolean shouldCache) {
        this.shouldCache = shouldCache;
        return this;
    }


    public BuildableCriteria maxResults(int max) {
        hibernateQuery.maxResults(max);
        return this;
    }

    /**
     * Whether to check for changes on the objects loaded
     * @param readOnly True to disable dirty checking
     */
    public BuildableCriteria readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * Calculates the property name including any alias paths
     *
     * @param propertyName The property name
     * @return The calculated property name
     */
    protected String calculatePropertyName(String propertyName) {
        return propertyName;
    }

    private String getLastAlias() {
        if (aliasStack.size() > 0) {
            return aliasStack.get(aliasStack.size() - 1).toString();
        }
        return null;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    protected DetachedCriteria convertToHibernateCriteria(QueryableCriteria<?> queryableCriteria) {
        return null;
    }

    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     */
    public void count(String propertyName) {
        count(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to return the property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public void count(String propertyName, String alias) {
        hibernateQuery.projections().countDistinct(getFullyQualifiedColumn(propertyName, alias));
    }

    private static String getFullyQualifiedColumn(String propertyName, String alias) {
        return (Objects.nonNull(alias) ? alias + "." : "") + propertyName;
    }

    public ProjectionList id() {
        hibernateQuery.projections().id();
        return this;
    }

    public ProjectionList count() {
        return hibernateQuery.projections().count();
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    public ProjectionList countDistinct(String propertyName) {
        return countDistinct(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     */
    public ProjectionList groupProperty(String propertyName) {
        return groupProperty(propertyName, null);
    }

    public ProjectionList distinct() {
        hibernateQuery.projections().distinct();
        return this;
    }

    /**
     * Adds a projection that allows the criteria to return the distinct property count
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList countDistinct(String propertyName, String alias) {
        hibernateQuery.projections().countDistinct(getFullyQualifiedColumn(propertyName,alias));
        return this;
    }


    /**
     * Adds a projection that allows the criteria's result to be grouped by a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList groupProperty(String propertyName, String alias) {
        hibernateQuery.projections().groupProperty(getFullyQualifiedColumn(propertyName,alias));
        return this;
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  maximum property value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList max(String propertyName) {
        return max(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  maximum property value
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList max(String propertyName, String alias) {
        hibernateQuery.projections().max(getFullyQualifiedColumn(propertyName,alias));
        return this;
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  minimum property value
     *
     * @param propertyName The name of the property
     */
    public ProjectionList min(String propertyName) {
        return min(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve a  minimum property value
     *
     * @param alias The alias to use
     */
    public ProjectionList min(String propertyName, String alias) {
        hibernateQuery.projections().min(getFullyQualifiedColumn(propertyName,alias));
        return this;
    }

    /**
     * Adds a projection that allows the criteria to return the row count
     *
     */
    public ProjectionList rowCount() {
        return count();
    }


    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     */
    public ProjectionList sum(String propertyName) {
        return sum(propertyName, null);
    }

    /**
     * Adds a projection that allows the criteria to retrieve the sum of the results of a property
     *
     * @param propertyName The name of the property
     * @param alias The alias to use
     */
    public ProjectionList sum(String propertyName, String alias) {
        hibernateQuery.projections().sum(getFullyQualifiedColumn(propertyName,alias));
        return this;
    }

    /**
     * Sets the fetch mode of an associated path
     *
     * @param associationPath The name of the associated path
     * @param fetchMode The fetch mode to set
     */
    public void fetchMode(String associationPath, FetchMode fetchMode) {
        if (fetchMode.equals(FetchMode.SELECT)){
            hibernateQuery.getDetachedCriteria().select(associationPath);
        } else {
            hibernateQuery.getDetachedCriteria().join(associationPath);
        }

    }

    /**
     * Sets the resultTransformer.
     * @param transformer The result transformer to use.
     */
    public void resultTransformer(ResultTransformer transformer) {
        hibernateQuery.setResultTransformer(transformer);
    }


    /**
     * Creates a Criterion that compares to class properties for equality
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria eqProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.eqProperty(propertyName,otherPropertyName);
        return this;
    }

    /**
     * Creates a Criterion that compares to class properties for !equality
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria neProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.neProperty(propertyName,otherPropertyName);
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is greater than the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria gtProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.gtProperty(propertyName,otherPropertyName);
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is greater than or equal to the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria geProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.geProperty(propertyName,otherPropertyName);
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is less than the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria ltProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.ltProperty(propertyName,otherPropertyName);
        return this;
    }

    /**
     * Creates a Criterion that tests if the first property is less than or equal to the second property
     * @param propertyName The first property name
     * @param otherPropertyName The second property name
     * @return A Criterion instance
     */
    public Criteria leProperty(String propertyName, String otherPropertyName) {
        hibernateQuery.leProperty(propertyName,otherPropertyName);
        return this;
    }

    @Override
    public Criteria allEq(Map<String, Object> propertyValues) {
        hibernateQuery.allEq(propertyValues);
        return this;
    }


    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria eqAll(String propertyName, Closure<?> propertyValue) {
        return eqAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }


    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria gtAll(String propertyName, Closure<?> propertyValue) {
        return gtAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria ltAll(String propertyName, Closure<?> propertyValue) {
        return ltAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria geAll(String propertyName, Closure<?> propertyValue) {
        return geAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Criteria leAll(String propertyName, Closure<?> propertyValue) {
        return leAll(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    /**
     * Creates a subquery criterion that ensures the given property is equal to all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria eqAll(String propertyName, @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        hibernateQuery.eqAll(propertyName,propertyValue);
        return this;
    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria gtAll(String propertyName, @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        hibernateQuery.gtAll(propertyName,propertyValue);
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, QueryableCriteria propertyValue) {
        return this;
    }

    @Override
    public Criteria gtSome(String propertyName, Closure<?> propertyValue) {
        return gtSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria geSome(String propertyName, QueryableCriteria propertyValue) {
        hibernateQuery.geSome(propertyName,propertyValue);
        return this;
    }

    @Override
    public Criteria geSome(String propertyName, Closure<?> propertyValue) {
        return geSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria ltSome(String propertyName, QueryableCriteria propertyValue) {
        hibernateQuery.ltSome(propertyName,propertyValue);
        return this;
    }

    @Override
    public Criteria ltSome(String propertyName, Closure<?> propertyValue) {
        return ltSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria leSome(String propertyName, QueryableCriteria propertyValue) {
        hibernateQuery.leSome(propertyName,propertyValue);
        return this;
    }

    @Override
    public Criteria leSome(String propertyName, Closure<?> propertyValue) {
        return leSome(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(propertyValue));
    }

    @Override
    public Criteria in(String propertyName, QueryableCriteria<?> subquery) {
        return inList(propertyName, subquery);
    }

    @Override
    public Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        hibernateQuery.in(propertyName,subquery);
        return this;
    }

    @Override
    public Criteria in(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new DetachedCriteria(targetClass).build(subquery));
    }

    @Override
    public Criteria inList(String propertyName, Closure<?> subquery) {
        return inList(propertyName, new DetachedCriteria(targetClass).build(subquery));
    }

    @Override
    public Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        hibernateQuery.notIn(propertyName,subquery);
        return this;
    }

    @Override
    public Criteria notIn(String propertyName, Closure<?> subquery) {
        return notIn(propertyName, new grails.gorm.DetachedCriteria(targetClass).build(subquery));
    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria ltAll(String propertyName, @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        hibernateQuery.ltAll(propertyName,propertyValue);
        return this;

    }

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria geAll(String propertyName, @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        hibernateQuery.geAll(propertyName,propertyValue);
        return this;

    }

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values
     *
     * @param propertyName  The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria leAll(String propertyName, @SuppressWarnings("rawtypes") QueryableCriteria propertyValue) {
        hibernateQuery.leAll(propertyName,propertyValue);
        return this;
    }

    /**
     * Creates a "greater than" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria gt(String propertyName, Object propertyValue) {
        hibernateQuery.gt(propertyName,propertyValue);
        return this;
    }

    public Criteria lte(String s, Object o) {
        return le(s,o);
    }

    /**
     * Creates a "greater than or equal to" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria ge(String propertyName, Object propertyValue) {
        hibernateQuery.ge(propertyName,propertyValue);
        return this;
    }

    /**
     * Creates a "less than" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria lt(String propertyName, Object propertyValue) {
        hibernateQuery.lt(propertyName,propertyValue);
        return this;
    }

    /**
     * Creates a "less than or equal to" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return A Criterion instance
     */
    public Criteria le(String propertyName, Object propertyValue) {
        hibernateQuery.le(propertyName,propertyValue);
        return this;
    }

    public Criteria idEquals(Object o) {
        return idEq(o);
    }

    @Override
    public Criteria exists(QueryableCriteria<?> subquery) {
        hibernateQuery.exists(subquery);
        return this;
    }

    @Override
    public Criteria notExists(QueryableCriteria<?> subquery) {
        hibernateQuery.notExits(subquery);
        return this;
    }

    public Criteria isEmpty(String property) {
        hibernateQuery.isEmpty(property);
        return this;
    }

    public Criteria isNotEmpty(String property) {
        hibernateQuery.isNotEmpty(property);
        return this;
    }

    public Criteria isNull(String property) {
        hibernateQuery.isNull(property);
        return this;
    }

    public Criteria isNotNull(String property) {
        hibernateQuery.isNotNull(property);
        return this;
    }

    @Override
    public Criteria and(Closure callable) {
        hibernateQuery.and(callable);
        return this;
    }

    @Override
    public Criteria or(Closure callable) {
        hibernateQuery.or(callable);
        return this;
    }

    @Override
    public Criteria not(Closure callable) {
        hibernateQuery.not(callable);
        return this;
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value. Case-sensitive.
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return A Criterion instance
     */
    public Criteria eq(String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, Collections.emptyMap());
    }

    public Criteria idEq(Object o) {
        return eq("id", o);
    }

    /**
     * Groovy moves the map to the first parameter if using the idiomatic form, e.g.
     * <code>eq 'firstName', 'Fred', ignoreCase: true</code>.
     * @param params optional map with customization parameters; currently only 'ignoreCase' is supported.
     * @param propertyName
     * @param propertyValue
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria eq(Map params, String propertyName, Object propertyValue) {
        return eq(propertyName, propertyValue, params);
    }

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     * Supports case-insensitive search if the <code>params</code> map contains <code>true</code>
     * under the 'ignoreCase' key.
     * @param propertyName The property name
     * @param propertyValue The property value
     * @param params optional map with customization parameters; currently only 'ignoreCase' is supported.
     *
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria eq(String propertyName, Object propertyValue, Map params) {
        if (params.get("ignoreCase") == Boolean.TRUE) {
            hibernateQuery.like(propertyName,  "%" + propertyValue.toString() + "%");
        } else {
            hibernateQuery.eq(propertyName,propertyValue);
        }
        return this;
    }



    /**
     * Creates a Criterion with from the specified property name and "like" expression
     * @param propertyName The property name
     * @param propertyValue The like value
     *
     * @return A Criterion instance
     */
    public Criteria like(String propertyName, Object propertyValue) {
        hibernateQuery.like(propertyName,propertyValue.toString());
        return this;
    }



    /**
     * Creates a Criterion with from the specified property name and "ilike" (a case sensitive version of "like") expression
     * @param propertyName The property name
     * @param propertyValue The ilike value
     *
     * @return A Criterion instance
     */
    public Criteria ilike(String propertyName, Object propertyValue) {
        hibernateQuery.ilike(propertyName,propertyValue.toString());
        return this;
    }

    /**
     * Applys a "in" contrain on the specified property
     * @param propertyName The property name
     * @param values A collection of values
     *
     * @return A Criterion instance
     */
    @SuppressWarnings("rawtypes")
    public Criteria in(String propertyName, Collection values) {
        hibernateQuery.in(propertyName,values.stream().toList());
        return this;
    }

    /**
     * Delegates to in as in is a Groovy keyword
     */
    @SuppressWarnings("rawtypes")
    public Criteria inList(String propertyName, Collection values) {
        return in(propertyName, values);
    }

    /**
     * Delegates to in as in is a Groovy keyword
     */
    public Criteria inList(String propertyName, Object[] values) {
        return in(propertyName, values);
    }

    /**
     * Applys a "in" contrain on the specified property
     * @param propertyName The property name
     * @param values A collection of values
     *
     * @return A Criterion instance
     */
    public Criteria in(String propertyName, Object[] values) {
        hibernateQuery.in(propertyName, List.of(values));
        return this;
    }

    /**
     * Orders by the specified property name (defaults to ascending)
     *
     * @param propertyName The property name to order by
     * @return A Order instance
     */
    public Criteria order(String propertyName) {
        order(new Query.Order(propertyName));
        return this;
    }

    @Override
    public Criteria order(Query.Order o) {
        hibernateQuery.order(o);
        return this;
    }

    public Criteria firstResult(int offset) {
        hibernateQuery.firstResult(offset);
        return this;
    }

    /**
     * Orders by the specified property name and direction
     *
     * @param propertyName The property name to order by
     * @param directionString Either "asc" for ascending or "desc" for descending
     *
     * @return A Order instance
     */
    public Criteria order(String propertyName, String directionString) {
        Query.Order.Direction direction = Query.Order.Direction.DESC.name().equalsIgnoreCase(directionString) ? Query.Order.Direction.DESC : Query.Order.Direction.ASC;
        hibernateQuery.order(new Query.Order(propertyName, direction));
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property by size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeEq(String propertyName, int size) {
        hibernateQuery.sizeEq(propertyName,size);
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeGt(String propertyName, int size) {
        hibernateQuery.sizeGt(propertyName,size);
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be greater than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeGe(String propertyName, int size) {
        hibernateQuery.sizeGe(propertyName,size);
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than or equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeLe(String propertyName, int size) {
        hibernateQuery.sizeLe(propertyName,size);
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeLt(String propertyName, int size) {
        hibernateQuery.sizeLt(propertyName,size);
        return this;
    }

    /**
     * Creates a Criterion with from the specified property name and "rlike" (a regular expression version of "like") expression
     *
     * @param propertyName  The property name
     * @param propertyValue The ilike value
     * @return A Criterion instance
     */
    public org.grails.datastore.mapping.query.api.Criteria rlike(String propertyName, Object propertyValue) {
        hibernateQuery.rlike(propertyName,propertyValue.toString());
        return this;
    }

    /**
     * Creates a Criterion that contrains a collection property to be not equal to the given size
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public Criteria sizeNe(String propertyName, int size) {
        hibernateQuery.sizeNe(propertyName,size);
        return this;
    }

    /**
     * Creates a "not equal" Criterion based on the specified property name and value
     * @param propertyName The property name
     * @param propertyValue The property value
     * @return The criterion object
     */
    public Criteria ne(String propertyName, Object propertyValue) {
        hibernateQuery.ne(propertyName,propertyValue);
        return this;
    }


    /**
     * Creates a "between" Criterion based on the property name and specified lo and hi values
     * @param propertyName The property name
     * @param lo The low value
     * @param hi The high value
     * @return A Criterion instance
     */
    public Criteria between(String propertyName, Object lo, Object hi) {
        hibernateQuery.between(propertyName,lo,hi);
        return this;
    }

    public Criteria gte(String s, Object o) {
        return ge(s, o);
    }


    @Override
    public Object list(@DelegatesTo(Criteria.class) Closure c) {
        hibernateQuery.setDetachedCriteria(new DetachedCriteria(targetClass));
        return invokeMethod(LIST_CALL, new Object[]{c});
    }

    public List list() {
        return hibernateQuery.list();
    }

    public Object singleResult() {
        return hibernateQuery.singleResult();
    }

    @Override
    public Object list(Map params, @DelegatesTo(Criteria.class) Closure c) {
        hibernateQuery.setDetachedCriteria(new DetachedCriteria(targetClass));
        return invokeMethod(LIST_CALL, new Object[]{params, c});
    }
    
    @Override
    public Object listDistinct(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(LIST_DISTINCT_CALL, new Object[]{c});
    }

    @Override
    public Object get(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(GET_CALL, new Object[]{c});
    }
    
    @Override
    public Object scroll(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(SCROLL_CALL, new Object[]{c});
    }

    public JoinType convertFromInt(Integer from) {
        return switch (from) {
            case 1 -> JoinType.LEFT;
            case 2 -> JoinType.RIGHT;
            default -> JoinType.INNER;
        };
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object invokeMethod(String name, Object obj) {

        Object[] args = obj.getClass().isArray() ? (Object[])obj : new Object[]{obj};

        if (paginationEnabledList && SET_RESULT_TRANSFORMER_CALL.equals(name) && args.length == 1 &&
                args[0] instanceof ResultTransformer) {
            hibernateQuery.setResultTransformer((ResultTransformer) args[0]);
            return null;
        }

        if (isCriteriaConstructionMethod(name, args)) {
            if (name.equals(GET_CALL)) {
                uniqueResult = true;
            }
            else if (name.equals(SCROLL_CALL)) {
                scroll = true;
            }
            else if (name.equals(COUNT_CALL)) {
                count = true;
            }
            else if (name.equals(LIST_DISTINCT_CALL)) {
                distinct = true;
            }


            // Check for pagination params
            if (name.equals(LIST_CALL) && args.length == 2) {
                paginationEnabledList = true;
                if (args[0] instanceof Map map ) {
                    if (map.get("max") instanceof Number max) {
                        hibernateQuery.maxResults(max.intValue());
                    }
                    if (map.get("offset") instanceof Number offset) {
                        hibernateQuery.firstResult(offset.intValue());
                    }
                }
                invokeClosureNode(args[1]);
            }
            else {
                invokeClosureNode(args[0]);
            }

            Object result;
            if (!uniqueResult) {
                if (distinct) {
                    hibernateQuery.distinct();
                    result = hibernateQuery.list();
                }
                else if (count) {
                    hibernateQuery.projections().count();
                    result = hibernateQuery.singleResult();
                }
                else if (paginationEnabledList) {
                    Map argMap = (Map)args[0];
                    final String sortField = (String) argMap.get(HibernateQueryConstants.ARGUMENT_SORT);
                    if (sortField != null) {
                        boolean ignoreCase = true;
                        Object caseArg = argMap.get(HibernateQueryConstants.ARGUMENT_IGNORE_CASE);
                        if (caseArg instanceof Boolean) {
                            ignoreCase = (Boolean) caseArg;
                        }
                        final String orderParam = (String) argMap.get(HibernateQueryConstants.ARGUMENT_ORDER);
                        final Query.Order.Direction direction = Query.Order.Direction.DESC.name().equalsIgnoreCase(orderParam) ? Query.Order.Direction.DESC : Query.Order.Direction.ASC;
                        Query.Order order ;
                        if (ignoreCase) {
                            order = new Query.Order(sortField, direction);
                            order.ignoreCase();
                        } else {
                            order = new Query.Order(sortField, direction);
                        }
                        hibernateQuery.order(order);
                    }
                    result = hibernateQuery.list();
                }
                else {
                    result = hibernateQuery.list();
                }
            }
            else {
                result = hibernateQuery.singleResult();
            }
            if (!participate) {
                closeSession();
            }
            return result;
        }


        MetaMethod metaMethod = getMetaClass().getMetaMethod(name, args);
        if (metaMethod != null) {
            return metaMethod.invoke(this, args);
        }



        if (isAssociationQueryMethod(args) || isAssociationQueryWithJoinSpecificationMethod(args)) {
            final boolean hasMoreThanOneArg = args.length > 1;
            Closure callable = hasMoreThanOneArg ? (Closure) args[1] : (Closure) args[0];
            JoinType joinType = hasMoreThanOneArg ? convertFromInt((Integer)args[0]) : convertFromInt(0);

            if (name.equals(AND)) {
                hibernateQuery.and(callable);
                return name;
            }

            if (name.equals(OR) ) {
                hibernateQuery.or(callable);
                return name;
            }

            if ( name.equals(NOT)) {
                hibernateQuery.not(callable);
                return name;
            }


            if (name.equals(PROJECTIONS) && args.length == 1 && (args[0] instanceof Closure)) {
                invokeClosureNode(callable);
                return name;
            }

            final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(targetClass, name);
            if (pd != null && pd.getReadMethod() != null) {
                final Metamodel metamodel = sessionFactory.getMetamodel();
                final EntityType<?> entityType = metamodel.entity(targetClass);
                final Attribute<?, ?> attribute = entityType.getAttribute(name);

                if (attribute.isAssociation()) {
                    Class oldTargetClass = targetClass;
                    targetClass = getClassForAssociationType(attribute);
                    if (targetClass.equals(oldTargetClass) && !hasMoreThanOneArg) {
                        joinType = JoinType.LEFT; // default to left join if joining on the same table
                    }

                    hibernateQuery.join(name,joinType);
                    hibernateQuery.in(name, new DetachedCriteria(targetClass).build(callable));
                    targetClass = oldTargetClass;

                    return name;
                }
            }
        }
        else if (args.length == 1 && args[0] != null) {
            Object value = args[0];
            if (name.equals(ID_EQUALS)) {
                return eq("id", value);
            }
            if (name.equals(IS_NULL) ||
                    name.equals(IS_NOT_NULL) ||
                    name.equals(IS_EMPTY) ||
                    name.equals(IS_NOT_EMPTY)) {
                if (!(value instanceof String)) {
                    throwRuntimeException(new IllegalArgumentException("call to [" + name + "] with value [" +
                            value + "] requires a String value."));
                }
                String propertyName = calculatePropertyName((String)value);
                if (name.equals(IS_NULL)) {
                    hibernateQuery.isNull(propertyName);
                }
                else if (name.equals(IS_NOT_NULL)) {
                    hibernateQuery.isNotNull(propertyName);
                }
                else if (name.equals(IS_EMPTY)) {
                    hibernateQuery.isEmpty(propertyName);
                }
                else {
                    hibernateQuery.isNotEmpty(propertyName);
                }
            }
        }
        throw new MissingMethodException(name, getClass(), args);
    }


    private boolean isAssociationQueryMethod(Object[] args) {
        return args.length == 1 && args[0] instanceof Closure;
    }

    private boolean isAssociationQueryWithJoinSpecificationMethod(Object[] args) {
        return args.length == 2 && (args[0] instanceof Number) && (args[1] instanceof Closure);
    }


    private boolean isCriteriaConstructionMethod(String name, Object[] args) {
        return (name.equals(LIST_CALL) && args.length == 2 && args[0] instanceof Map && args[1] instanceof Closure) ||
                (name.equals(ROOT_CALL) ||
                        name.equals(ROOT_DO_CALL) ||
                        name.equals(LIST_CALL) ||
                        name.equals(LIST_DISTINCT_CALL) ||
                        name.equals(GET_CALL) ||
                        name.equals(COUNT_CALL) ||
                        name.equals(SCROLL_CALL) && args.length == 1 && args[0] instanceof Closure);
    }



    private void invokeClosureNode(Object args) {
        Closure<?> callable = (Closure<?>)args;
        callable.setDelegate(this);
        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
    }


    /**
     * Returns the criteria instance
     * @return The criteria instance
     */
    public CriteriaQuery getInstance() {
        return criteriaQuery;
    }

    /**
     * Set whether a unique result should be returned
     * @param uniqueResult True if a unique result should be returned
     */
    public void setUniqueResult(boolean uniqueResult) {
        this.uniqueResult = uniqueResult;
    }

    protected Class getClassForAssociationType(Attribute<?, ?> type) {
        if (type instanceof PluralAttribute) {
            return ((PluralAttribute)type).getElementType().getJavaType();
        }
        return type.getJavaType();
    }

    /**
     * instances of this class are pushed onto the logicalExpressionStack
     * to represent all the unfinished "and", "or", and "not" expressions.
     */
    protected class LogicalExpression {
        public final Object name;

        public LogicalExpression(Object name) {
            this.name = name;
        }
    }

    /**
     * Throws a runtime exception where necessary to ensure the session gets closed
     */
    protected void throwRuntimeException(RuntimeException t) {
        closeSessionFollowingException();
        throw t;
    }

    private void closeSessionFollowingException() {
        closeSession();
        criteriaQuery = null;
    }

    /**
     * Closes the session if it is copen
     */
    protected void closeSession() {
        if (hibernateSession != null && hibernateSession.isOpen() && !participate) {
            hibernateSession.close();
        }
    }

    public int getDefaultFlushMode() {
        return defaultFlushMode;
    }

    public void setDefaultFlushMode(int defaultFlushMode) {
        this.defaultFlushMode = defaultFlushMode;
    }
}
