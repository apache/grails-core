/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.query.jpa

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.GenericConversionService
import org.springframework.dao.InvalidDataAccessResourceUsageException

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.AbstractPersistentEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Query.AvgProjection
import org.grails.datastore.mapping.query.Query.Between
import org.grails.datastore.mapping.query.Query.Conjunction
import org.grails.datastore.mapping.query.Query.CountDistinctProjection
import org.grails.datastore.mapping.query.Query.CountProjection
import org.grails.datastore.mapping.query.Query.Criterion
import org.grails.datastore.mapping.query.Query.Disjunction
import org.grails.datastore.mapping.query.Query.Equals
import org.grails.datastore.mapping.query.Query.EqualsAll
import org.grails.datastore.mapping.query.Query.EqualsProperty
import org.grails.datastore.mapping.query.Query.GreaterThan
import org.grails.datastore.mapping.query.Query.GreaterThanAll
import org.grails.datastore.mapping.query.Query.GreaterThanEquals
import org.grails.datastore.mapping.query.Query.GreaterThanEqualsAll
import org.grails.datastore.mapping.query.Query.GreaterThanEqualsProperty
import org.grails.datastore.mapping.query.Query.GreaterThanEqualsSome
import org.grails.datastore.mapping.query.Query.GreaterThanProperty
import org.grails.datastore.mapping.query.Query.GreaterThanSome
import org.grails.datastore.mapping.query.Query.ILike
import org.grails.datastore.mapping.query.Query.IdEquals
import org.grails.datastore.mapping.query.Query.IdProjection
import org.grails.datastore.mapping.query.Query.In
import org.grails.datastore.mapping.query.Query.IsEmpty
import org.grails.datastore.mapping.query.Query.IsNotEmpty
import org.grails.datastore.mapping.query.Query.IsNotNull
import org.grails.datastore.mapping.query.Query.IsNull
import org.grails.datastore.mapping.query.Query.Junction
import org.grails.datastore.mapping.query.Query.LessThan
import org.grails.datastore.mapping.query.Query.LessThanAll
import org.grails.datastore.mapping.query.Query.LessThanEquals
import org.grails.datastore.mapping.query.Query.LessThanEqualsAll
import org.grails.datastore.mapping.query.Query.LessThanEqualsProperty
import org.grails.datastore.mapping.query.Query.LessThanEqualsSome
import org.grails.datastore.mapping.query.Query.LessThanProperty
import org.grails.datastore.mapping.query.Query.LessThanSome
import org.grails.datastore.mapping.query.Query.Like
import org.grails.datastore.mapping.query.Query.MaxProjection
import org.grails.datastore.mapping.query.Query.MinProjection
import org.grails.datastore.mapping.query.Query.Negation
import org.grails.datastore.mapping.query.Query.NotEquals
import org.grails.datastore.mapping.query.Query.NotEqualsAll
import org.grails.datastore.mapping.query.Query.NotEqualsProperty
import org.grails.datastore.mapping.query.Query.NotIn
import org.grails.datastore.mapping.query.Query.Order
import org.grails.datastore.mapping.query.Query.Projection
import org.grails.datastore.mapping.query.Query.ProjectionList
import org.grails.datastore.mapping.query.Query.PropertyComparisonCriterion
import org.grails.datastore.mapping.query.Query.PropertyCriterion
import org.grails.datastore.mapping.query.Query.PropertyProjection
import org.grails.datastore.mapping.query.Query.SizeEquals
import org.grails.datastore.mapping.query.Query.SizeGreaterThan
import org.grails.datastore.mapping.query.Query.SizeGreaterThanEquals
import org.grails.datastore.mapping.query.Query.SizeLessThan
import org.grails.datastore.mapping.query.Query.SizeLessThanEquals
import org.grails.datastore.mapping.query.Query.SizeNotEquals
import org.grails.datastore.mapping.query.Query.SubqueryCriterion
import org.grails.datastore.mapping.query.Query.SumProjection
import org.grails.datastore.mapping.query.api.AssociationCriteria
import org.grails.datastore.mapping.query.api.QueryableCriteria

/**
 * Builds JPA 1.0 String-based queries from the Query model
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
class JpaQueryBuilder {

    private static final String DISTINCT_CLAUSE = 'DISTINCT '
    private static final String SELECT_CLAUSE = 'SELECT '
    private static final String AS_CLAUSE = ' AS '
    private static final String FROM_CLAUSE = ' FROM '
    private static final String ORDER_BY_CLAUSE = ' ORDER BY '
    private static final String WHERE_CLAUSE = ' WHERE '
    private static final char COMMA = ','
    private static final char CLOSE_BRACKET = ')'
    private static final char OPEN_BRACKET = '('
    private static final char SPACE = ' '
    private static final char QUESTIONMARK = '?'
    private static final char DOT = '.'
    public static final String NOT_CLAUSE = ' NOT'
    public static final String LOGICAL_AND = ' AND '
    public static final String UPDATE_CLAUSE = 'UPDATE '
    public static final String DELETE_CLAUSE = 'DELETE FROM '

    public static final String LOGICAL_OR = ' OR '
    private static final Map<Class, QueryHandler> queryHandlers = new HashMap<>()
    public static final String PARAMETER_NAME_PREFIX = 'p'
    private static final String PARAMETER_PREFIX = ':p'
    private PersistentEntity entity
    private Junction criteria
    private ProjectionList projectionList = new ProjectionList()
    private List<Order> orders = Collections.emptyList()
    private String logicalName
    private ConversionService conversionService = new GenericConversionService()
    private boolean hibernateCompatible

    JpaQueryBuilder(QueryableCriteria criteria) {
        this(criteria.getPersistentEntity(), criteria.getCriteria())
    }

    JpaQueryBuilder(PersistentEntity entity, List<Criterion> criteria) {
        this(entity, new Conjunction(criteria))
    }

    JpaQueryBuilder(PersistentEntity entity, List<Criterion> criteria, ProjectionList projectionList) {
        this(entity, new Conjunction(criteria), projectionList)
    }

    JpaQueryBuilder(PersistentEntity entity, List<Criterion> criteria, ProjectionList projectionList, List<Order> orders) {
        this(entity, new Conjunction(criteria), projectionList, orders)
    }

    JpaQueryBuilder(PersistentEntity entity, Junction criteria) {
        if (entity == null) {
            throw new ConfigurationException('No persistent entity specified for JPA query builder')
        }
        this.entity = entity
        this.criteria = criteria
        this.logicalName = entity.getDecapitalizedName()
    }

    JpaQueryBuilder(PersistentEntity entity, Junction criteria, ProjectionList projectionList) {
        this(entity, criteria)
        this.projectionList = projectionList
    }

    JpaQueryBuilder(PersistentEntity entity, Junction criteria, ProjectionList projectionList, List<Order> orders) {
        this(entity, criteria, projectionList)
        this.orders = orders
    }

    void setHibernateCompatible(boolean hibernateCompatible) {
        this.hibernateCompatible = hibernateCompatible
    }

    void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService
    }

    /**
     * Builds an UPDATE statement.
     *
     * @param propertiesToUpdate THe properties to update
     * @return The JpaQueryInfo object
     */
    JpaQueryInfo buildUpdate(Map<String, Object> propertiesToUpdate) {
        if (propertiesToUpdate.isEmpty()) {
            throw new InvalidDataAccessResourceUsageException('No properties specified to update')
        }
        StringBuilder queryString = new StringBuilder(UPDATE_CLAUSE).append(entity.getName()).append(SPACE).append(logicalName)

        List parameters = new ArrayList()
        buildUpdateStatement(queryString, propertiesToUpdate, parameters, hibernateCompatible)
        StringBuilder whereClause = new StringBuilder()
        buildWhereClause(entity, criteria, queryString, whereClause, logicalName, false, parameters)
        return new JpaQueryInfo(queryString.toString(), parameters)
    }

    /**
     * Builds a DELETE statement
     *
     * @return The JpaQueryInfo
     */
    JpaQueryInfo buildDelete() {
        StringBuilder queryString = new StringBuilder(DELETE_CLAUSE).append(entity.getName()).append(SPACE).append(logicalName)
        StringBuilder whereClause = new StringBuilder()
        List parameters = buildWhereClause(entity, criteria, queryString, whereClause, logicalName, false)
        return new JpaQueryInfo(queryString.toString(), parameters)
    }

    /**
     * Builds  SELECT statement
     *
     * @return The JpaQueryInfo
     */
    JpaQueryInfo buildSelect() {
        StringBuilder queryString = new StringBuilder(SELECT_CLAUSE)

        buildSelectClause(queryString)

        StringBuilder whereClause = new StringBuilder()
        List parameters = null
        if (!criteria.isEmpty()) {
            parameters = buildWhereClause(entity, criteria, queryString, whereClause, logicalName, true)
        }

        appendOrder(queryString, logicalName)
        return new JpaQueryInfo(queryString.toString(), parameters)
    }

    private void buildSelectClause(StringBuilder queryString) {
        ProjectionList projectionList = this.projectionList
        String logicalName = this.logicalName
        PersistentEntity entity = this.entity
        buildSelect(queryString, projectionList.getProjectionList(), logicalName, entity)

        queryString.append(FROM_CLAUSE)
                .append(entity.getName())
                .append(AS_CLAUSE)
                .append(logicalName)
    }

    @PackageScope
    static void buildSelect(StringBuilder queryString, List<Projection> projectionList, String logicalName, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            queryString.append(DISTINCT_CLAUSE)
                    .append(logicalName)
        }
        else {
            for (Iterator i = projectionList.iterator(); i.hasNext();) {
                Projection projection = (Projection) i.next()
                if (projection instanceof CountProjection) {
                    queryString.append('COUNT(')
                            .append(logicalName)
                            .append(CLOSE_BRACKET)
                }
                else if (projection instanceof IdProjection) {
                    queryString.append(logicalName)
                            .append(DOT)
                            .append(entity.getIdentity().getName())
                }
                else if (projection instanceof PropertyProjection) {
                    PropertyProjection pp = (PropertyProjection) projection
                    if (projection instanceof AvgProjection) {
                        queryString.append('AVG(')
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET)
                    }
                    else if (projection instanceof SumProjection) {
                        queryString.append('SUM(')
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET)
                    }
                    else if (projection instanceof MinProjection) {
                        queryString.append('MIN(')
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET)
                    }
                    else if (projection instanceof MaxProjection) {
                        queryString.append('MAX(')
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET)
                    }
                    else if (projection instanceof CountDistinctProjection) {
                        queryString.append('COUNT(DISTINCT ')
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET)
                    }
                    else {
                        queryString.append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                    }
                }

                if (i.hasNext()) {
                    queryString.append(COMMA)
                }
            }
        }
    }

    static int appendCriteriaForOperator(StringBuilder q,
                                                String logicalName, final String name, int position, String operator, boolean hibernateCompatible) {
        q.append(logicalName)
            .append(DOT)
            .append(name)
            .append(operator)
            .append(PARAMETER_PREFIX)
            .append(++position)
        return position
    }

    static {

        queryHandlers.put(AssociationQuery, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {

                if (!allowJoins) {
                    throw new InvalidDataAccessResourceUsageException('Joins cannot be used in a DELETE or UPDATE operation')
                }
                AssociationQuery aq = (AssociationQuery) criterion
                final Association<?> association = aq.getAssociation()
                Junction associationCriteria = aq.getCriteria()
                List<Criterion> associationCriteriaList = associationCriteria.getCriteria()

                return handleAssociationCriteria(q, whereClause, logicalName, position, parameters, conversionService, allowJoins, association, associationCriteria, associationCriteriaList, hibernateCompatible)
            }
        })

        queryHandlers.put(Negation, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {

                whereClause.append(NOT_CLAUSE)
                           .append(OPEN_BRACKET)

                final Negation negation = (Negation) criterion
                position = buildWhereClauseForCriterion(entity, negation, q, whereClause, logicalName, negation.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible)
                whereClause.append(CLOSE_BRACKET)

                return position
            }
        })

        queryHandlers.put(Conjunction, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                whereClause.append(OPEN_BRACKET)

                final Conjunction conjunction = (Conjunction) criterion
                position = buildWhereClauseForCriterion(entity, conjunction, q, whereClause, logicalName, conjunction.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible)
                whereClause.append(CLOSE_BRACKET)

                return position
            }
        })

        queryHandlers.put(Disjunction, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                whereClause.append(OPEN_BRACKET)

                final Disjunction disjunction = (Disjunction) criterion
                position = buildWhereClauseForCriterion(entity, disjunction, q, whereClause, logicalName, disjunction.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible)
                whereClause.append(CLOSE_BRACKET)

                return position
            }
        })

        queryHandlers.put(Equals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Equals eq = (Equals) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, Equals)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, '=', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(EqualsProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                EqualsProperty eq = (EqualsProperty) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, EqualsProperty)
                validateProperty(entity, otherProperty, EqualsProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '=')
                return position
            }
        })

        queryHandlers.put(NotEqualsProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                PropertyComparisonCriterion eq = (PropertyComparisonCriterion) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, NotEqualsProperty)
                validateProperty(entity, otherProperty, NotEqualsProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '!=')
                return position
            }
        })

        queryHandlers.put(GreaterThanProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                PropertyComparisonCriterion eq = (PropertyComparisonCriterion) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, GreaterThanProperty)
                validateProperty(entity, otherProperty, GreaterThanProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '>')
                return position
            }
        })

        queryHandlers.put(GreaterThanEqualsProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                PropertyComparisonCriterion eq = (PropertyComparisonCriterion) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, GreaterThanEqualsProperty)
                validateProperty(entity, otherProperty, GreaterThanEqualsProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '>=')
                return position
            }
        })

        queryHandlers.put(LessThanProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                PropertyComparisonCriterion eq = (PropertyComparisonCriterion) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, LessThanProperty)
                validateProperty(entity, otherProperty, LessThanProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '<')
                return position
            }
        })

        queryHandlers.put(LessThanEqualsProperty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                PropertyComparisonCriterion eq = (PropertyComparisonCriterion) criterion
                final String propertyName = eq.getProperty()
                String otherProperty = eq.getOtherProperty()

                validateProperty(entity, propertyName, LessThanEqualsProperty)
                validateProperty(entity, otherProperty, LessThanEqualsProperty)
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, '<=')
                return position
            }
        })

        queryHandlers.put(IsNull, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                IsNull isNull = (IsNull) criterion
                final String name = isNull.getProperty()
                validateProperty(entity, name, IsNull)
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(' IS NULL ')

                return position
            }
        })

        queryHandlers.put(IsNotNull, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                IsNotNull isNotNull = (IsNotNull) criterion
                final String name = isNotNull.getProperty()
                validateProperty(entity, name, IsNotNull)
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(' IS NOT NULL ')

                return position
            }
        })

        queryHandlers.put(IsEmpty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                IsEmpty isEmpty = (IsEmpty) criterion
                final String name = isEmpty.getProperty()
                validateProperty(entity, name, IsEmpty)
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(' IS EMPTY ')

                return position
            }
        })

        queryHandlers.put(IsNotEmpty, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                IsNotEmpty isNotEmpty = (IsNotEmpty) criterion
                final String name = isNotEmpty.getProperty()
                validateProperty(entity, name, IsNotEmpty)
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(' IS NOT EMPTY ')

                return position
            }
        })

        queryHandlers.put(IdEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                IdEquals eq = (IdEquals) criterion
                PersistentProperty prop = entity.getIdentity()
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, prop.getName(), position, '=', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(NotEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                NotEquals eq = (NotEquals) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, NotEquals)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' != ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(GreaterThan, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                GreaterThan eq = (GreaterThan) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, GreaterThan)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' > ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(LessThanEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                LessThanEquals eq = (LessThanEquals) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, LessThanEquals)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' <= ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(GreaterThanEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                GreaterThanEquals eq = (GreaterThanEquals) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, GreaterThanEquals)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' >= ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(Between, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Between between = (Between) criterion
                final Object from = between.getFrom()
                final Object to = between.getTo()

                final String name = between.getProperty()
                PersistentProperty prop = validateProperty(entity, name, Between)
                Class propType = prop.getType()
                final String qualifiedName = logicalName + DOT + name
                whereClause.append(OPEN_BRACKET)
                           .append(qualifiedName)
                           .append(' >= ')
                           .append(PARAMETER_PREFIX)
                           .append(++position)
                whereClause.append(' AND ')
                           .append(qualifiedName)
                           .append(' <= ')
                           .append(PARAMETER_PREFIX)
                           .append(++position)
                           .append(CLOSE_BRACKET)

                parameters.add(conversionService.convert(from, propType))
                parameters.add(conversionService.convert(to, propType))
                return position
            }
        })

        queryHandlers.put(LessThan, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                LessThan eq = (LessThan) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, LessThan)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' < ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(Like, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Like eq = (Like) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, Like)
                Class propType = prop.getType()
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, ' like ', hibernateCompatible)
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(ILike, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                ILike eq = (ILike) criterion
                final String name = eq.getProperty()
                PersistentProperty prop = validateProperty(entity, name, ILike)
                Class propType = prop.getType()
                whereClause.append('lower(')
                    .append(logicalName)
                    .append(DOT)
                    .append(name)
                    .append(')')
                    .append(' like lower(')
                    .append(PARAMETER_PREFIX)
                    .append(++position)
                    .append(')')
                parameters.add(conversionService.convert(eq.getValue(), propType))
                return position
            }
        })

        queryHandlers.put(In, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                In inQuery = (In) criterion
                final String name = inQuery.getProperty()
                PersistentProperty prop = validateProperty(entity, name, In)
                Class propType = prop.getType()
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(' IN (')
                QueryableCriteria subquery = inQuery.getSubquery()
                if (subquery != null) {
                    buildSubQuery(q, whereClause, position, parameters, conversionService, allowJoins, hibernateCompatible, subquery)
                }
                else {
                    for (Iterator i = inQuery.getValues().iterator(); i.hasNext();) {
                        Object val = i.next()
                        whereClause.append(PARAMETER_PREFIX)
                        whereClause.append(++position)
                        if (i.hasNext()) {
                            whereClause.append(COMMA)
                        }
                        parameters.add(conversionService.convert(val, propType))
                    }
                }
                whereClause.append(CLOSE_BRACKET)

                return position
            }
        })

        queryHandlers.put(NotIn, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                NotIn notIn = (NotIn) criterion
                String comparisonExpression = ' NOT IN ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, notIn, comparisonExpression)
            }
        })

        queryHandlers.put(EqualsAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                EqualsAll equalsAll = (EqualsAll) criterion
                String comparisonExpression = ' = ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(NotEqualsAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion equalsAll = (SubqueryCriterion) criterion
                String comparisonExpression = ' != ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(GreaterThanAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion equalsAll = (SubqueryCriterion) criterion
                String comparisonExpression = ' > ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(GreaterThanSome, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion equalsAll = (SubqueryCriterion) criterion
                String comparisonExpression = ' > SOME ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(GreaterThanEqualsAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion equalsAll = (SubqueryCriterion) criterion
                String comparisonExpression = ' >= ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(GreaterThanEqualsSome, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion equalsAll = (SubqueryCriterion) criterion
                String comparisonExpression = ' >= SOME ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression)
            }
        })

        queryHandlers.put(LessThanAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion subqueryCriterion = (SubqueryCriterion) criterion
                String comparisonExpression = ' < ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression)
            }
        })

        queryHandlers.put(LessThanSome, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion subqueryCriterion = (SubqueryCriterion) criterion
                String comparisonExpression = ' < SOME ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression)
            }
        })

        queryHandlers.put(LessThanEqualsAll, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion subqueryCriterion = (SubqueryCriterion) criterion
                String comparisonExpression = ' <= ALL ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression)
            }
        })

        queryHandlers.put(LessThanEqualsSome, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                SubqueryCriterion subqueryCriterion = (SubqueryCriterion) criterion
                String comparisonExpression = ' <= SOME ('
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression)
            }
        })

        queryHandlers.put(SizeEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '=', hibernateCompatible)
            }
        })

        queryHandlers.put(SizeNotEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '!=', hibernateCompatible)
            }
        })

        queryHandlers.put(SizeGreaterThan, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '>', hibernateCompatible)
            }
        })

        queryHandlers.put(SizeGreaterThanEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '>=', hibernateCompatible)
            }
        })

        queryHandlers.put(SizeLessThan, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '<', hibernateCompatible)
            }
        })

        queryHandlers.put(SizeLessThanEquals, new QueryHandler() {
            int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                return handleSizeComparison(entity, (PropertyCriterion) criterion, whereClause, logicalName, position, parameters, conversionService, '<=', hibernateCompatible)
            }
        })

    }

    protected static int handleSubQuery(PersistentEntity entity, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible, SubqueryCriterion equalsAll, String comparisonExpression) {
        final String name = equalsAll.getProperty()
        validateProperty(entity, name, In)
        QueryableCriteria subquery = equalsAll.getValue()
        whereClause.append(logicalName)
                .append(DOT)
                .append(name)
                .append(comparisonExpression)
        buildSubQuery(q, whereClause, position, parameters, conversionService, allowJoins, hibernateCompatible, subquery)
        whereClause.append(CLOSE_BRACKET)
        return position
    }

    protected static int handleSizeComparison(PersistentEntity entity, PropertyCriterion criterion, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, String operator, boolean hibernateCompatible) {
        final String name = criterion.getProperty()
        validateProperty(entity, name, criterion.getClass())
        Object value = criterion.getValue()
        int size = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString())
        whereClause.append('SIZE(')
                   .append(logicalName)
                   .append(DOT)
                   .append(name)
                   .append(') ')
                   .append(operator)
                   .append(PARAMETER_PREFIX)
                   .append(++position)
        parameters.add(size)
        return position
    }

    protected static void buildSubQuery(StringBuilder q, StringBuilder whereClause, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible, QueryableCriteria subquery) {
        PersistentEntity associatedEntity = subquery.getPersistentEntity()
        String associatedEntityName = associatedEntity.getName()
        String associatedEntityLogicalName = associatedEntity.getDecapitalizedName() + position
        whereClause.append('SELECT ')
        buildSelect(whereClause, subquery.getProjections(), associatedEntityLogicalName, associatedEntity)
        whereClause.append(' FROM ')
                .append(associatedEntityName)
                .append(' ')
                .append(associatedEntityLogicalName)
                .append(' WHERE ')
        List<Criterion> criteria = subquery.getCriteria()
        for (Criterion subCriteria : criteria) {
            QueryHandler queryHandler = queryHandlers.get(subCriteria.getClass())
            queryHandler.handle(associatedEntity, subCriteria, q, whereClause, associatedEntityLogicalName, position, parameters, conversionService, allowJoins, hibernateCompatible)
        }
    }

    @PackageScope
    static int handleAssociationCriteria(StringBuilder query, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, Association<?> association, Junction associationCriteria, List<Criterion> associationCriteriaList, boolean hibernateCompatible) {
        if (association instanceof ToOne) {
            final String associationName = association.getName()
            logicalName = logicalName + DOT + associationName
            return buildWhereClauseForCriterion(association.getAssociatedEntity(), associationCriteria, query, whereClause, logicalName, associationCriteriaList, position, parameters, conversionService, allowJoins, hibernateCompatible)
        }

        if (association != null) {
            final String associationName = association.getName()
            // TODO: Allow customization of join strategy!
            String joinType = ' INNER JOIN '
            query.append(joinType)
                .append(logicalName)
                .append(DOT)
                .append(associationName)
                .append(SPACE)
                .append(associationName)

            return buildWhereClauseForCriterion(association.getAssociatedEntity(), associationCriteria, query, whereClause, associationName, associationCriteriaList, position, parameters, conversionService, allowJoins, hibernateCompatible)
        }

        return position
    }

    private void buildUpdateStatement(StringBuilder queryString, Map<String, Object> propertiesToUpdate, List parameters, boolean hibernateCompatible) {
        queryString.append(SPACE).append('SET')

        // keys need to be sorted before query is built
        Set<String> keys = new TreeSet<>(propertiesToUpdate.keySet())

        Iterator<String> iterator = keys.iterator()
        while (iterator.hasNext()) {
            String propertyName = iterator.next()
            PersistentProperty prop = entity.getPropertyByName(propertyName)
            if (prop == null) throw new InvalidDataAccessResourceUsageException("Property '" + propertyName + "' of class '" + entity.getName() + "' specified in update does not exist")

            parameters.add(propertiesToUpdate.get(propertyName))
            queryString.append(SPACE).append(logicalName).append(DOT).append(propertyName).append('=')
            queryString.append(PARAMETER_PREFIX).append(parameters.size())
            if (iterator.hasNext()) {
                queryString.append(COMMA)
            }
        }
    }

    @PackageScope
    static void appendPropertyComparison(StringBuilder q, String logicalName, String propertyName, String otherProperty, String operator) {
        q.append(logicalName)
            .append(DOT)
            .append(propertyName)
            .append(operator)
            .append(logicalName)
            .append(DOT)
            .append(otherProperty)
    }

    @PackageScope
    static PersistentProperty validateProperty(PersistentEntity entity, String name, Class criterionType) {
        PersistentProperty identity = entity.getIdentity()
        if (identity != null && identity.getName().equals(name)) {
            return identity
        }
        PersistentProperty[] compositeIdentity = ((AbstractPersistentEntity) entity).getCompositeIdentity()
        if (compositeIdentity != null) {
            for (PersistentProperty property in compositeIdentity) {
                if (property.getName().equals(name)) {
                    return property
                }
            }
        }
        PersistentProperty prop = entity.getPropertyByName(name)
        if (prop == null) {
            throw new InvalidDataAccessResourceUsageException('Cannot use [' +
                  criterionType.getSimpleName() + '] criterion on non-existent property: ' + name)
        }
        return prop
    }

    @PackageScope
    static interface QueryHandler {
        int handle(PersistentEntity entity, Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible)
    }

    private List buildWhereClause(PersistentEntity entity, Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName, boolean allowJoins) {
        List parameters = new ArrayList()
        return buildWhereClause(entity, criteria, q, whereClause, logicalName, allowJoins, parameters)
    }

    private List buildWhereClause(PersistentEntity entity, Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName, boolean allowJoins, List parameters) {
        if (!criteria.isEmpty()) {
            int position = parameters.size()
            final List<Criterion> criterionList = criteria.getCriteria()
            whereClause.append(WHERE_CLAUSE)
            if (criteria instanceof Negation) {
                whereClause.append(NOT_CLAUSE)
            }
            whereClause.append(OPEN_BRACKET)
            position = buildWhereClauseForCriterion(entity, criteria, q, whereClause, logicalName,
                    criterionList, position, parameters,
                    conversionService, allowJoins, this.hibernateCompatible)
            q.append(whereClause.toString())
            q.append(CLOSE_BRACKET)
        }
        return parameters
    }

    protected void appendOrder(StringBuilder queryString, String logicalName) {
        if (!orders.isEmpty()) {
            queryString.append(ORDER_BY_CLAUSE)
            for (Order order : orders) {
                queryString.append(logicalName)
                           .append(DOT)
                           .append(order.getProperty())
                           .append(SPACE)
                           .append(order.getDirection().toString())
                           .append(SPACE)
            }
        }
    }

    @PackageScope
    static int buildWhereClauseForCriterion(PersistentEntity entity,
                                            Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName,
                                            final List<Criterion> criterionList, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
        for (Iterator<Criterion> iterator = criterionList.iterator(); iterator.hasNext();) {
            Criterion criterion = iterator.next()

            final String operator = criteria instanceof Conjunction ? LOGICAL_AND : LOGICAL_OR
            QueryHandler qh = queryHandlers.get(criterion.getClass())
            if (qh != null) {

                position = qh.handle(entity, criterion, q, whereClause, logicalName,
                        position, parameters, conversionService, allowJoins, hibernateCompatible)
            }
            else if (criterion instanceof AssociationCriteria) {

                if (!allowJoins) {
                    throw new InvalidDataAccessResourceUsageException('Joins cannot be used in a DELETE or UPDATE operation')
                }
                AssociationCriteria ac = (AssociationCriteria) criterion
                Association association = ac.getAssociation()
                List<Criterion> associationCriteriaList = ac.getCriteria()
                handleAssociationCriteria(q, whereClause, logicalName, position, parameters, conversionService, allowJoins, association, new Conjunction(), associationCriteriaList, hibernateCompatible)
            }
            else {
                throw new InvalidDataAccessResourceUsageException('Queries of type ' + criterion.getClass().getSimpleName() + ' are not supported by this implementation')
            }

            if (iterator.hasNext()) {
                whereClause.append(operator)
            }
        }

        return position
    }

}
