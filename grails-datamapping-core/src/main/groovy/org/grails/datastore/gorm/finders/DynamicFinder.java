/* Copyright (C) 2010-2025 the original author or authors.
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
package org.grails.datastore.gorm.finders;

import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import jakarta.persistence.FetchType;
import jakarta.persistence.criteria.JoinType;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.grails.datastore.gorm.finders.MethodExpression.Between;
import org.grails.datastore.gorm.finders.MethodExpression.Equal;
import org.grails.datastore.gorm.finders.MethodExpression.GreaterThan;
import org.grails.datastore.gorm.finders.MethodExpression.GreaterThanEquals;
import org.grails.datastore.gorm.finders.MethodExpression.Ilike;
import org.grails.datastore.gorm.finders.MethodExpression.InList;
import org.grails.datastore.gorm.finders.MethodExpression.InRange;
import org.grails.datastore.gorm.finders.MethodExpression.IsEmpty;
import org.grails.datastore.gorm.finders.MethodExpression.IsNotEmpty;
import org.grails.datastore.gorm.finders.MethodExpression.IsNotNull;
import org.grails.datastore.gorm.finders.MethodExpression.IsNull;
import org.grails.datastore.gorm.finders.MethodExpression.LessThan;
import org.grails.datastore.gorm.finders.MethodExpression.LessThanEquals;
import org.grails.datastore.gorm.finders.MethodExpression.Like;
import org.grails.datastore.gorm.finders.MethodExpression.NotEqual;
import org.grails.datastore.gorm.finders.MethodExpression.NotInList;
import org.grails.datastore.gorm.finders.MethodExpression.Rlike;
import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.BuildableCriteria;
import org.grails.datastore.mapping.query.api.QueryArgumentsAware;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for dynamic finders.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class DynamicFinder extends AbstractFinder implements QueryBuildingFinder {

    public static final String ARGUMENT_FETCH_SIZE = "fetchSize";
    public static final String ARGUMENT_TIMEOUT = "timeout";
    public static final String ARGUMENT_READ_ONLY = "readOnly";
    public static final String ARGUMENT_FLUSH_MODE = "flushMode";
    public static final String ARGUMENT_MAX = "max";
    public static final String ARGUMENT_OFFSET = "offset";
    public static final String ARGUMENT_ORDER = "order";
    public static final String ARGUMENT_SORT = "sort";
    public static final String ORDER_DESC = "desc";
    public static final String ORDER_ASC = "asc";
    public static final String ARGUMENT_FETCH = "fetch";
    public static final String ARGUMENT_IGNORE_CASE = "ignoreCase";
    public static final String ARGUMENT_CACHE = "cache";
    public static final String ARGUMENT_LOCK = "lock";

    private static final String OPERATOR_OR = "Or";
    private static final String OPERATOR_AND = "And";
    private static final String[] DEFAULT_OPERATORS = {OPERATOR_AND, OPERATOR_OR};
    private static Pattern methodExpressinPattern;
    private static final Pattern[] DEFAULT_OPERATION_PATTERNS;
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final String NOT = "Not";
    private static final Map<String, Constructor> METHOD_EXPRESSIONS = new LinkedHashMap<String, Constructor>();

    protected final MappingContext mappingContext;
    protected Pattern pattern;

    private Pattern[] operatorPatterns;
    private String[] operators;

    static {
        DEFAULT_OPERATION_PATTERNS = new Pattern[2];
        for (int i = 0; i < DEFAULT_OPERATORS.length; i++) {
            String defaultOperator = DEFAULT_OPERATORS[i];
            DEFAULT_OPERATION_PATTERNS[i] = Pattern.compile("(\\w+)(" + defaultOperator + ")(\\p{Upper})(\\w+)");
        }

        // populate the default method expressions
        try {
            Class[] classes = {
                    Equal.class, NotEqual.class, NotInList.class, InList.class, InRange.class, Between.class, Like.class, Ilike.class, Rlike.class,
                    GreaterThanEquals.class, LessThanEquals.class, GreaterThan.class,
                    LessThan.class, IsNull.class, IsNotNull.class, IsEmpty.class,
                    IsEmpty.class, IsNotEmpty.class};
            Class[] constructorParamTypes = {Class.class, String.class};
            for (Class c : classes) {
                METHOD_EXPRESSIONS.put(c.getSimpleName(), c.getConstructor(constructorParamTypes));
            }
        } catch (SecurityException e) {
            // ignore
        } catch (NoSuchMethodException e) {
            // ignore
        }

        resetMethodExpressionPattern();
    }

    protected DynamicFinder(final Pattern pattern, final String[] operators, final Datastore datastore) {
        super(datastore);
        this.mappingContext = datastore.getMappingContext();
        this.pattern = pattern;
        this.operators = operators;
        this.operatorPatterns = new Pattern[operators.length];
        populateOperators(operators);
    }

    protected DynamicFinder(final Pattern pattern, final String[] operators, final MappingContext mappingContext) {
        super(null);
        this.mappingContext = mappingContext;
        this.pattern = pattern;
        this.operators = operators;
        this.operatorPatterns = new Pattern[operators.length];
        populateOperators(operators);
    }

    /**
     * Registers a new method expression. The Class must extends from the class {@link MethodExpression} and provide
     * a constructor that accepts a Class parameter and a String parameter.
     *
     * @param methodExpression A class that extends from {@link MethodExpression}
     */
    public static void registerNewMethodExpression(Class methodExpression) {
        try {
            METHOD_EXPRESSIONS.put(methodExpression.getSimpleName(), methodExpression.getConstructor(
                    Class.class, String.class));
            resetMethodExpressionPattern();
        } catch (SecurityException e) {
            throw new IllegalArgumentException("Class [" + methodExpression +
                    "] does not provide a constructor that takes parameters of type Class and String: " +
                    e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class [" + methodExpression +
                    "] does not provide a constructor that takes parameters of type Class and String: " +
                    e.getMessage(), e);
        }
    }

    /**
     * Builds a match specification that can be used to establish information about a dynamic finder compilation for the purposes of compilation etc.
     *
     * @param prefix         The dynamic finder prefix. For example 'findBy'
     * @param methodName     The full method name
     * @param parameterCount The number of parameters
     * @return The match specification
     */
    public static MatchSpec buildMatchSpec(String prefix, String methodName, int parameterCount) {
        String methodPattern = "(" + prefix + ")([A-Z]\\w*)";
        Matcher matcher = Pattern.compile(methodPattern).matcher(methodName);
        if (matcher.find()) {
            int totalRequiredArguments = 0;
            List<MethodExpression> expressions = new ArrayList<>();
            if (matcher.groupCount() == 2) {
                String querySequence = matcher.group(2);
                String operatorInUse;
                boolean containsOperator = false;
                String[] queryParameters;
                for (int i = 0; i < DEFAULT_OPERATORS.length; i++) {
                    Matcher currentMatcher = DEFAULT_OPERATION_PATTERNS[i].matcher(querySequence);
                    if (currentMatcher.find()) {
                        containsOperator = true;
                        operatorInUse = DEFAULT_OPERATORS[i];

                        queryParameters = querySequence.split(operatorInUse);
                        // loop through query parameters and create expressions
                        // calculating the number of arguments required for the expression
                        for (String queryParameter : queryParameters) {
                            MethodExpression currentExpression = findMethodExpression(queryParameter);
                            // add to list of expressions
                            totalRequiredArguments += currentExpression.argumentsRequired;
                            expressions.add(currentExpression);
                        }
                        break;
                    }
                }

                // otherwise there is only one expression
                if (!containsOperator && querySequence != null) {
                    MethodExpression solo = findMethodExpression(querySequence);

                    final int requiredArguments = solo.getArgumentsRequired();
                    if (requiredArguments > parameterCount) {
                        return null;
                    }

                    totalRequiredArguments += requiredArguments;
                    expressions.add(solo);
                }

                // if the total of all the arguments necessary does not equal the number of arguments
                // return null
                if (totalRequiredArguments > parameterCount) {
                    return null;
                } else {
                    return new MatchSpec(methodName, prefix, querySequence, totalRequiredArguments, expressions);
                }
            }
        }
        return null;
    }

    /**
     * Sets the pattern to use for this finder
     *
     * @param pattern A regular expression
     */
    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    /**
     * Checks whether the given method is a match
     *
     * @param methodName The method name
     * @return True if it is
     */
    public boolean isMethodMatch(String methodName) {
        return pattern.matcher(methodName.subSequence(0, methodName.length())).find();
    }

    public Object invoke(final Class clazz, String methodName, Closure additionalCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = createFinderInvocation(clazz, methodName, additionalCriteria, arguments);
        return doInvokeInternal(invocation);
    }

    public Object invoke(final Class clazz, String methodName, DetachedCriteria detachedCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = createFinderInvocation(clazz, methodName, null, arguments);
        if (detachedCriteria != null) {
            invocation.setDetachedCriteria(detachedCriteria);
        }
        return doInvokeInternal(invocation);
    }

    public DynamicFinderInvocation createFinderInvocation(Class clazz, String methodName,
                                                          Closure additionalCriteria, Object[] arguments) {

        List expressions = new ArrayList();
        if (arguments == null) {
            arguments = EMPTY_OBJECT_ARRAY;
        } else {
            Object[] tmp = new Object[arguments.length];
            System.arraycopy(arguments, 0, tmp, 0, arguments.length);
            arguments = tmp;
        }
        Matcher match = pattern.matcher(methodName);
        // find match
        match.find();

        String[] queryParameters;
        int totalRequiredArguments = 0;
        // get the sequence clauses
        final String querySequence;
        int groupCount = match.groupCount();
        if (groupCount == 6) {
            String booleanProperty = match.group(3);
            if (booleanProperty == null) {
                booleanProperty = match.group(6);
                querySequence = null;
            } else {
                querySequence = match.group(5);
            }
            Boolean arg = Boolean.TRUE;
            if (booleanProperty.matches("Not[A-Z].*")) {
                booleanProperty = booleanProperty.substring(3);
                arg = Boolean.FALSE;
            }
            MethodExpression booleanExpression = findMethodExpression(clazz, booleanProperty);
            booleanExpression.setArguments(new Object[]{arg});
            expressions.add(booleanExpression);
        } else {
            querySequence = match.group(2);
        }
        // if it contains operator and split
        boolean containsOperator = false;
        String operatorInUse = null;
        if (querySequence != null) {
            for (int i = 0; i < operators.length; i++) {
                Matcher currentMatcher = operatorPatterns[i].matcher(querySequence);
                if (currentMatcher.find()) {
                    containsOperator = true;
                    operatorInUse = operators[i];

                    queryParameters = querySequence.split(operatorInUse);

                    // loop through query parameters and create expressions
                    // calculating the number of arguments required for the expression
                    int argumentCursor = 0;
                    for (String queryParameter : queryParameters) {
                        MethodExpression currentExpression = findMethodExpression(clazz, queryParameter);
                        final int requiredArgs = currentExpression.getArgumentsRequired();
                        // populate the arguments into the GrailsExpression from the argument list
                        Object[] currentArguments = new Object[requiredArgs];
                        if ((argumentCursor + requiredArgs) > arguments.length) {
                            throw new MissingMethodException(methodName, clazz, arguments);
                        }

                        for (int k = 0; k < requiredArgs; k++, argumentCursor++) {
                            currentArguments[k] = arguments[argumentCursor];
                        }
                        currentExpression = getInitializedExpression(currentExpression, currentArguments);
                        PersistentEntity persistentEntity = mappingContext.getPersistentEntity(clazz.getName());

                        try {
                            currentExpression.convertArguments(persistentEntity);
                        } catch (ConversionException e) {
                            throw new MissingMethodException(methodName, clazz, arguments);
                        }

                        // add to list of expressions
                        totalRequiredArguments += currentExpression.argumentsRequired;
                        expressions.add(currentExpression);
                    }
                    break;
                }
            }
        }
        // otherwise there is only one expression
        if (!containsOperator && querySequence != null) {
            MethodExpression solo = findMethodExpression(clazz, querySequence);

            final int requiredArguments = solo.getArgumentsRequired();
            if (requiredArguments > arguments.length) {
                throw new MissingMethodException(methodName, clazz, arguments);
            }

            totalRequiredArguments += requiredArguments;
            Object[] soloArgs = new Object[requiredArguments];
            System.arraycopy(arguments, 0, soloArgs, 0, requiredArguments);
            solo = getInitializedExpression(solo, arguments);
            PersistentEntity persistentEntity = mappingContext.getPersistentEntity(clazz.getName());
            try {
                solo.convertArguments(persistentEntity);
            } catch (ConversionException e) {
                if (!(persistentEntity.getPropertyByName(solo.propertyName) instanceof Basic)) {
                    throw new MissingMethodException(methodName, clazz, arguments);
                }
            }
            expressions.add(solo);
        }

        // if the total of all the arguments necessary does not equal the number of arguments
        // throw exception
        if (totalRequiredArguments > arguments.length) {
            throw new MissingMethodException(methodName, clazz, arguments);
        }

        // calculate the remaining arguments
        Object[] remainingArguments = new Object[arguments.length - totalRequiredArguments];
        if (remainingArguments.length > 0) {
            for (int i = 0, j = totalRequiredArguments; i < remainingArguments.length; i++, j++) {
                remainingArguments[i] = arguments[j];
            }
        }

        return new DynamicFinderInvocation(clazz, methodName, remainingArguments,
                expressions, additionalCriteria, operatorInUse);
    }

    public Object invoke(final Class clazz, String methodName, Object[] arguments) {
        return invoke(clazz, methodName, (Closure) null, arguments);
    }

    /**
     * Populates arguments for the given query form the given map
     *
     * @param query  The query
     * @param argMap The query arguments
     */
    public static void populateArgumentsForCriteria(BuildableCriteria query, Map argMap) {
        if (argMap == null) {
            return;
        }
        String orderParam = (String) argMap.get(ARGUMENT_ORDER);

        Object fetchObj = argMap.get(ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                Object fetchValue = fetch.get(associationName);
                if (fetchValue instanceof FetchType) {
                    FetchType fetchType = (FetchType) fetchValue;
                    handleFetchType(query, associationName, fetchType);
                } else if (fetchValue instanceof JoinType) {
                    JoinType joinType = (JoinType) fetchValue;
                    query.join(associationName, joinType);
                } else {
                    FetchType fetchType = getFetchMode(fetchValue);
                    handleFetchType(query, associationName, fetchType);
                }
            }
        }

        if (argMap.containsKey(ARGUMENT_CACHE)) {
            query.cache(ClassUtils.getBooleanFromMap(ARGUMENT_CACHE, argMap));
        }

        Object sortObject = argMap.get(ARGUMENT_SORT);
        boolean ignoreCase = !argMap.containsKey(ARGUMENT_IGNORE_CASE) || ClassUtils.getBooleanFromMap(ARGUMENT_IGNORE_CASE, argMap);

        if (sortObject != null) {
            if (sortObject instanceof CharSequence) {
                final String sort = sortObject.toString();
                final Query.Order order = ORDER_DESC.equalsIgnoreCase(orderParam) ? Query.Order.desc(sort) : Query.Order.asc(sort);
                if (ignoreCase) {
                    order.ignoreCase();
                }
                query.order(order);
            } else if (sortObject instanceof Map) {
                Map sortMap = (Map) sortObject;
                for (Object key : sortMap.keySet()) {
                    Object value = sortMap.get(key);
                    String sort = key.toString();
                    final Query.Order order = ORDER_DESC.equalsIgnoreCase(orderParam) ? Query.Order.desc(sort) : Query.Order.asc(sort);
                    if (ignoreCase) {
                        order.ignoreCase();
                    }
                    query.order(order);
                }

            }
        }

        if (query instanceof QueryArgumentsAware) {
            ((QueryArgumentsAware) query).setArguments(argMap);
        }
    }

    /**
     * Populates arguments for the given query form the given map
     *
     * @param query  The query
     * @param argMap The query arguments
     */
    //TODO: Change {code}Class<? extends Object>{code} to {class<?>} once GROOVY-9460 is fixed.
    public static void populateArgumentsForCriteria(Class<? extends Object> targetClass, Query query, Map argMap) {
        if (argMap == null) {
            return;
        }

        Integer maxParam = null;
        Integer offsetParam = null;
        final ConversionService conversionService = query.getEntity().getMappingContext().getConversionService();
        if (argMap.containsKey(ARGUMENT_MAX)) {
            maxParam = conversionService.convert(argMap.get(ARGUMENT_MAX), Integer.class);
        }
        if (argMap.containsKey(ARGUMENT_OFFSET)) {
            offsetParam = conversionService.convert(argMap.get(ARGUMENT_OFFSET), Integer.class);
        }
        String orderParam = (String) argMap.get(ARGUMENT_ORDER);

        Object fetchObj = argMap.get(ARGUMENT_FETCH);
        if (fetchObj instanceof Map) {
            Map fetch = (Map) fetchObj;
            for (Object o : fetch.keySet()) {
                String associationName = (String) o;
                Object fetchValue = fetch.get(associationName);
                if (fetchValue instanceof FetchType) {
                    FetchType fetchType = (FetchType) fetchValue;
                    handleFetchType(query, associationName, fetchType);
                } else if (fetchValue instanceof JoinType) {
                    JoinType joinType = (JoinType) fetchValue;
                    query.join(associationName, joinType);
                } else {
                    FetchType fetchType = getFetchMode(fetchValue);
                    handleFetchType(query, associationName, fetchType);
                }
            }
        }

        if (argMap.containsKey(ARGUMENT_CACHE)) {
            query.cache(ClassUtils.getBooleanFromMap(ARGUMENT_CACHE, argMap));
        }
        if (argMap.containsKey(ARGUMENT_LOCK)) {
            query.lock(ClassUtils.getBooleanFromMap(ARGUMENT_LOCK, argMap));
        }

        final int max = maxParam == null ? -1 : maxParam;
        final int offset = offsetParam == null ? -1 : offsetParam;
        if (max > -1) {
            query.max(max);
        }
        if (offset > -1) {
            query.offset(offset);
        }
        Object sortObject = argMap.get(ARGUMENT_SORT);
        boolean ignoreCase = !argMap.containsKey(ARGUMENT_IGNORE_CASE) || ClassUtils.getBooleanFromMap(ARGUMENT_IGNORE_CASE, argMap);

        if (sortObject != null) {
            if (sortObject instanceof CharSequence) {
                final String sort = sortObject.toString();
                final String order = ORDER_DESC.equalsIgnoreCase(orderParam) ? ORDER_DESC : ORDER_ASC;
                addSimpleSort(query, sort, order, ignoreCase);
            } else if (sortObject instanceof Map) {
                Map sortMap = (Map) sortObject;
                applySortForMap(query, sortMap, ignoreCase);

            }
        }

        if (query instanceof QueryArgumentsAware) {
            ((QueryArgumentsAware) query).setArguments(argMap);
        }
    }

    /**
     * Applies sorting logic to the given query from the given map
     *
     * @param query      The query
     * @param sortMap    The sort map
     * @param ignoreCase Whether toi ignore case
     */
    public static void applySortForMap(Query query, Map sortMap, boolean ignoreCase) {
        for (Object key : sortMap.keySet()) {
            Object value = sortMap.get(key);
            String sort = key.toString();
            String order = value != null ? value.toString() : ORDER_ASC;

            addSimpleSort(query, sort, order, ignoreCase);
        }
    }

    /**
     * Retrieves the fetch mode for the specified instance; otherwise returns the default FetchMode.
     *
     * @param object The object, converted to a string
     * @return The FetchMode
     */
    public static FetchType getFetchMode(Object object) {
        String name = object != null ? object.toString() : "default";
        if (name.equalsIgnoreCase(FetchType.EAGER.toString()) || name.equalsIgnoreCase("join")) {
            return FetchType.EAGER;
        }
        if (name.equalsIgnoreCase(FetchType.LAZY.toString()) || name.equalsIgnoreCase("select")) {
            return FetchType.LAZY;
        }
        return FetchType.LAZY;
    }

    /**
     * Applies the given detached criteria to the given query
     *
     * @param query            The query
     * @param detachedCriteria The detached criteria
     */
    public static void applyDetachedCriteria(Query query, AbstractDetachedCriteria detachedCriteria) {
        if (detachedCriteria != null) {
            Map<String, FetchType> fetchStrategies = detachedCriteria.getFetchStrategies();
            for (Map.Entry<String, FetchType> entry : fetchStrategies.entrySet()) {
                String property = entry.getKey();
                switch (entry.getValue()) {
                    case EAGER:
                        JoinType joinType = (JoinType) detachedCriteria.getJoinTypes().get(property);
                        if (joinType != null) {
                            query.join(property, joinType);
                        } else {
                            query.join(property);
                        }
                        break;
                    case LAZY:
                        query.select(property);
                        break;
                    default:
                        // do nothing
                        break;
                }
            }
            List<Query.Criterion> criteria = detachedCriteria.getCriteria();
            for (Query.Criterion criterion : criteria) {
                query.add(criterion);
            }
            List<Query.Projection> projections = detachedCriteria.getProjections();
            for (Query.Projection projection : projections) {
                query.projections().add(projection);
            }
            List<Query.Order> orders = detachedCriteria.getOrders();
            for (Query.Order order : orders) {
                query.order(order);
            }
        }
    }

    protected abstract Object doInvokeInternal(DynamicFinderInvocation invocation);

    private static void handleFetchType(Query q, String associationName, FetchType fetchType) {
        switch (fetchType) {
            case LAZY:
                q.select(associationName);
                break;
            case EAGER:
                q.join(associationName);
                break;
            default:
                // do nothing
        }
    }

    protected MethodExpression findMethodExpression(Class clazz, String expression) {
        return findMethodExpressionInternal(clazz, expression);
    }

    protected static MethodExpression findMethodExpression(String expression) {
        return findMethodExpressionInternal(null, expression);
    }

    private static MethodExpression findMethodExpressionInternal(final Class clazz, String expression) {
        MethodExpression me = null;
        final Matcher matcher = methodExpressinPattern.matcher(expression);
        Class methodExpressionClass = Equal.class;
        Constructor methodExpressionConstructor = null;
        String clause = methodExpressionClass.getSimpleName();
        if (matcher.find()) {
            clause = matcher.group(1);
            methodExpressionConstructor = METHOD_EXPRESSIONS.get(clause);
            if (methodExpressionConstructor != null) {
                methodExpressionClass = methodExpressionConstructor.getDeclaringClass();
            }
        }

        String propertyName = calcPropertyName(expression, methodExpressionClass.getSimpleName());
        boolean negation = false;
        if (propertyName.endsWith(NOT)) {
            int i = propertyName.lastIndexOf(NOT);
            propertyName = propertyName.substring(0, i);
            negation = true;
        }

        if (!StringUtils.hasLength(propertyName)) {
            throw new IllegalArgumentException("No property name specified in clause: " + clause);
        }

        propertyName = NameUtils.decapitalizeFirstChar(propertyName);
        if (methodExpressionConstructor != null) {
            try {
                me = (MethodExpression) methodExpressionConstructor.newInstance(clazz, propertyName);
            } catch (Exception e) {
                // ignore
            }
        }
        if (me == null) {
            me = new Equal(clazz, propertyName);
        }
        if (negation) {
            final MethodExpression finalMe = me;
            return new MethodExpression(clazz, propertyName) {
                @Override
                public Query.Criterion createCriterion() {
                    return new Query.Negation().add(finalMe.createCriterion());
                }

                @Override
                public void setArguments(Object[] arguments) {
                    finalMe.setArguments(arguments);
                }

                @Override
                public int getArgumentsRequired() {
                    return finalMe.getArgumentsRequired();
                }

                @Override
                public Object[] getArguments() {
                    return finalMe.getArguments();
                }
            };
        }
        return me;
    }

    private static void handleFetchType(BuildableCriteria q, String associationName, FetchType fetchType) {
        switch (fetchType) {
            case LAZY:
                q.select(associationName);
                break;
            case EAGER:
                q.join(associationName);
                break;
            default:
                // do nothing
        }
    }

    private static void resetMethodExpressionPattern() {
        String expressionPattern = DefaultGroovyMethods.join((Iterable) METHOD_EXPRESSIONS.keySet(), "|");
        methodExpressinPattern = Pattern.compile("\\p{Upper}[\\p{Lower}\\d]+(" + expressionPattern + ")");
    }

    private static void addSimpleSort(Query q, String sort, String order, boolean ignoreCase) {
        Query.Order o;
        if (ORDER_DESC.equalsIgnoreCase(order)) {
            o = Query.Order.desc(sort);
        } else {
            o = Query.Order.asc(sort);
        }

        if (ignoreCase) {
            o = o.ignoreCase();
        }

        q.order(o);
    }

    private void populateOperators(String[] operators) {
        for (int i = 0; i < operators.length; i++) {
            operatorPatterns[i] = Pattern.compile("(\\w+)(" + operators[i] + ")(\\p{Upper})(\\w+)");
        }
    }

    protected void configureQueryWithArguments(Class clazz, Query query, Object[] arguments) {
        if (arguments.length == 0 || !(arguments[0] instanceof Map)) {
            populateArgumentsForCriteria(clazz, query, Collections.emptyMap());
            return;
        }

        Map<?, ?> argMap = (Map<?, ?>) arguments[0];
        populateArgumentsForCriteria(clazz, query, argMap);
    }

    private static String calcPropertyName(String queryParameter, String clause) {
        String propName;
        if (clause != null && !clause.equals(Equal.class.getSimpleName())) {
            int i = queryParameter.indexOf(clause);
            propName = queryParameter.substring(0, i);
        } else {
            propName = queryParameter;
        }

        return propName;
    }

    /**
     * Initializes the arguments of the specified expression with the specified arguments.  If the
     * expression is an Equal expression and the argument is null then a new expression is created
     * and returned of type IsNull.
     *
     * @param expression expression to initialize
     * @param arguments  arguments to the expression
     * @return the initialized expression
     */
    private MethodExpression getInitializedExpression(MethodExpression expression, Object[] arguments) {
//        if (expression instanceof Equal && arguments.length == 1 && arguments[0] == null) { // logic moved directly to Equal.createCriterion
//            expression = new IsNull(expression.propertyName);
//        } else {
        expression.setArguments(arguments);
//        }
        return expression;
    }

}
