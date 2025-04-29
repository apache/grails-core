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
package org.grails.datastore.mapping.query.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.grails.datastore.mapping.model.AbstractPersistentEntity;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.AssociationCriteria;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Builds JPA 1.0 String-based queries from the Query model
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JpaQueryBuilder {
    private static final String DISTINCT_CLAUSE = "DISTINCT ";
    private static final String SELECT_CLAUSE = "SELECT ";
    private static final String AS_CLAUSE = " AS ";
    private static final String FROM_CLAUSE = " FROM ";
    private static final String ORDER_BY_CLAUSE = " ORDER BY ";
    private static final String WHERE_CLAUSE = " WHERE ";
    private static final char COMMA = ',';
    private static final char CLOSE_BRACKET = ')';
    private static final char OPEN_BRACKET = '(';
    private static final char SPACE = ' ';
    private static final char QUESTIONMARK = '?';
    private static final char DOT = '.';
    public static final String NOT_CLAUSE = " NOT";
    public static final String LOGICAL_AND = " AND ";
    public static final String UPDATE_CLAUSE = "UPDATE ";
    public static final String DELETE_CLAUSE = "DELETE ";

    public static final String LOGICAL_OR = " OR ";
    private static final Map<Class, QueryHandler> queryHandlers = new HashMap<>();
    public static final String PARAMETER_NAME_PREFIX = "p";
    private static final String PARAMETER_PREFIX = ":p";
    private PersistentEntity entity;
    private Query.Junction criteria;
    private Query.ProjectionList projectionList = new Query.ProjectionList();
    private List<Query.Order> orders= Collections.emptyList();
    private String logicalName;
    private ConversionService conversionService = new GenericConversionService();
    private boolean hibernateCompatible;

    public JpaQueryBuilder(QueryableCriteria criteria) {
        this(criteria.getPersistentEntity(), criteria.getCriteria());
    }

    public JpaQueryBuilder(PersistentEntity entity, List<Query.Criterion> criteria) {
        this(entity, new Query.Conjunction(criteria));
    }

    public JpaQueryBuilder(PersistentEntity entity,List<Query.Criterion> criteria, Query.ProjectionList projectionList) {
        this(entity, new Query.Conjunction(criteria), projectionList);
    }

    public JpaQueryBuilder(PersistentEntity entity, List<Query.Criterion> criteria, Query.ProjectionList projectionList, List<Query.Order> orders) {
        this(entity, new Query.Conjunction(criteria), projectionList, orders);
    }

    public JpaQueryBuilder(PersistentEntity entity, Query.Junction criteria) {
        this.entity = entity;
        this.criteria = criteria;
        this.logicalName = entity.getDecapitalizedName();
    }

    public JpaQueryBuilder(PersistentEntity entity, Query.Junction criteria, Query.ProjectionList projectionList) {
        this(entity, criteria);
        this.projectionList = projectionList;
    }

    public JpaQueryBuilder(PersistentEntity entity, Query.Junction criteria, Query.ProjectionList projectionList, List<Query.Order> orders) {
        this(entity, criteria, projectionList);
        this.orders = orders;
    }

    public void setHibernateCompatible(boolean hibernateCompatible) {
        this.hibernateCompatible = hibernateCompatible;
    }

    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Builds an UPDATE statement.
     *
     * @param propertiesToUpdate THe properties to update
     * @return The JpaQueryInfo object
     */
    public JpaQueryInfo buildUpdate(Map<String, Object> propertiesToUpdate) {
        if (propertiesToUpdate.isEmpty()) {
            throw new InvalidDataAccessResourceUsageException("No properties specified to update");
        }
        StringBuilder queryString = new StringBuilder(UPDATE_CLAUSE).append(entity.getName()).append(SPACE).append(logicalName);

        List parameters = new ArrayList();
        buildUpdateStatement(queryString, propertiesToUpdate, parameters, hibernateCompatible);
        StringBuilder whereClause = new StringBuilder();
        buildWhereClause(entity, criteria, queryString, whereClause, logicalName, false, parameters);
        return new JpaQueryInfo(queryString.toString(), parameters);
    }

    /**
     * Builds a DELETE statement
     *
     * @return The JpaQueryInfo
     */
    public JpaQueryInfo buildDelete() {
        StringBuilder queryString = new StringBuilder(DELETE_CLAUSE).append(entity.getName()).append(SPACE).append(logicalName);
        StringBuilder whereClause = new StringBuilder();
        List parameters = buildWhereClause(entity, criteria, queryString, whereClause, logicalName, false);
        return new JpaQueryInfo(queryString.toString(), parameters);
    }

    /**
     * Builds  SELECT statement
     *
     * @return The JpaQueryInfo
     */
    public JpaQueryInfo buildSelect() {
        StringBuilder queryString = new StringBuilder(SELECT_CLAUSE);

        buildSelectClause(queryString);

        StringBuilder whereClause= new StringBuilder();
        List parameters = null;
        if (!criteria.isEmpty()) {
            parameters = buildWhereClause(entity, criteria, queryString, whereClause,logicalName, true);
        }

        appendOrder(queryString, logicalName);
        return new JpaQueryInfo(queryString.toString(), parameters);
    }

    private void buildSelectClause(StringBuilder queryString) {
        Query.ProjectionList projectionList = this.projectionList;
        String logicalName = this.logicalName;
        PersistentEntity entity = this.entity;
        buildSelect(queryString, projectionList.getProjectionList(), logicalName, entity);

        queryString.append(FROM_CLAUSE)
                .append(entity.getName())
                .append(AS_CLAUSE )
                .append(logicalName);
    }

    private static void buildSelect(StringBuilder queryString, List<Query.Projection> projectionList, String logicalName, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            queryString.append(DISTINCT_CLAUSE)
                    .append(logicalName);
        }
        else {
            for (Iterator i = projectionList.iterator(); i.hasNext();) {
                Query.Projection projection = (Query.Projection) i.next();
                if (projection instanceof Query.CountProjection) {
                    queryString.append("COUNT(")
                            .append(logicalName)
                            .append(CLOSE_BRACKET);
                }
                else if (projection instanceof Query.IdProjection) {
                    queryString.append(logicalName)
                            .append(DOT)
                            .append(entity.getIdentity().getName());
                }
                else if (projection instanceof Query.PropertyProjection) {
                    Query.PropertyProjection pp = (Query.PropertyProjection) projection;
                    if (projection instanceof Query.AvgProjection) {
                        queryString.append("AVG(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    }
                    else if (projection instanceof Query.SumProjection) {
                        queryString.append("SUM(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    }
                    else if (projection instanceof Query.MinProjection) {
                        queryString.append("MIN(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    }
                    else if (projection instanceof Query.MaxProjection) {
                        queryString.append("MAX(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    }
                    else if (projection instanceof Query.CountDistinctProjection) {
                        queryString.append("COUNT(DISTINCT ")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    }
                    else {
                        queryString.append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName());
                    }
                }

                if (i.hasNext()) {
                    queryString.append(COMMA);
                }
            }
        }
    }

    static public int appendCriteriaForOperator(StringBuilder q,
                                                String logicalName, final String name, int position, String operator, boolean hibernateCompatible) {
        q.append(logicalName)
         .append(DOT)
         .append(name)
         .append(operator)
         .append(PARAMETER_PREFIX)
         .append(++position);
        return position;
    }

    static {

        queryHandlers.put(AssociationQuery.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {

                if (!allowJoins) {
                    throw new InvalidDataAccessResourceUsageException("Joins cannot be used in a DELETE or UPDATE operation");
                }
                AssociationQuery aq = (AssociationQuery) criterion;
                final Association<?> association = aq.getAssociation();
                Query.Junction associationCriteria = aq.getCriteria();
                List<Query.Criterion> associationCriteriaList = associationCriteria.getCriteria();

                return handleAssociationCriteria(q, whereClause, logicalName, position, parameters, conversionService, allowJoins, association, associationCriteria, associationCriteriaList, hibernateCompatible);
            }
        });

        queryHandlers.put(Query.Negation.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {

                whereClause.append(NOT_CLAUSE)
                           .append(OPEN_BRACKET);

                final Query.Negation negation = (Query.Negation)criterion;
                position = buildWhereClauseForCriterion(entity, negation, q, whereClause, logicalName, negation.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible);
                whereClause.append(CLOSE_BRACKET);

                return position;
            }
        });

        queryHandlers.put(Query.Conjunction.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                whereClause.append(OPEN_BRACKET);

                final Query.Conjunction conjunction = (Query.Conjunction)criterion;
                position = buildWhereClauseForCriterion(entity, conjunction, q, whereClause, logicalName, conjunction.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible);
                whereClause.append(CLOSE_BRACKET);

                return position;
            }
        });

        queryHandlers.put(Query.Disjunction.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion,
                              StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters,
                              ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                whereClause.append(OPEN_BRACKET);

                final Query.Disjunction disjunction = (Query.Disjunction)criterion;
                position = buildWhereClauseForCriterion(entity, disjunction, q,whereClause,  logicalName, disjunction.getCriteria(), position, parameters, conversionService, allowJoins, hibernateCompatible);
                whereClause.append(CLOSE_BRACKET);

                return position;
            }
        });

        queryHandlers.put(Query.Equals.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.Equals eq = (Query.Equals) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.Equals.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, "=", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

       queryHandlers.put(Query.EqualsProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.EqualsProperty eq = (Query.EqualsProperty) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.EqualsProperty.class);
                validateProperty(entity, otherProperty, Query.EqualsProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, "=");
                return position;
            }
        });

       queryHandlers.put(Query.NotEqualsProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.PropertyComparisonCriterion eq = (Query.PropertyComparisonCriterion) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.NotEqualsProperty.class);
                validateProperty(entity, otherProperty, Query.NotEqualsProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, "!=");
                return position;
            }
        });

       queryHandlers.put(Query.GreaterThanProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.PropertyComparisonCriterion eq = (Query.PropertyComparisonCriterion) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.GreaterThanProperty.class);
                validateProperty(entity, otherProperty, Query.GreaterThanProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, ">");
                return position;
            }
        });

       queryHandlers.put(Query.GreaterThanEqualsProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.PropertyComparisonCriterion eq = (Query.PropertyComparisonCriterion) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.GreaterThanEqualsProperty.class);
                validateProperty(entity, otherProperty, Query.GreaterThanEqualsProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, ">=");
                return position;
            }
        });

       queryHandlers.put(Query.LessThanProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.PropertyComparisonCriterion eq = (Query.PropertyComparisonCriterion) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.LessThanProperty.class);
                validateProperty(entity, otherProperty, Query.LessThanProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, "<");
                return position;
            }
        });

       queryHandlers.put(Query.LessThanEqualsProperty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.PropertyComparisonCriterion eq = (Query.PropertyComparisonCriterion) criterion;
                final String propertyName = eq.getProperty();
                String otherProperty = eq.getOtherProperty();

                validateProperty(entity, propertyName, Query.LessThanEqualsProperty.class);
                validateProperty(entity, otherProperty, Query.LessThanEqualsProperty.class);
                appendPropertyComparison(whereClause, logicalName, propertyName, otherProperty, "<=");
                return position;
            }
        });

        queryHandlers.put(Query.IsNull.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IsNull isNull = (Query.IsNull) criterion;
                final String name = isNull.getProperty();
                validateProperty(entity, name, Query.IsNull.class);
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IS NULL ");

                return position;
            }
        });

        queryHandlers.put(Query.IsNotNull.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IsNotNull isNotNull = (Query.IsNotNull) criterion;
                final String name = isNotNull.getProperty();
                validateProperty(entity, name, Query.IsNotNull.class);
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IS NOT NULL ");

                return position;
            }
        });

        queryHandlers.put(Query.IsEmpty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IsEmpty isEmpty = (Query.IsEmpty) criterion;
                final String name = isEmpty.getProperty();
                validateProperty(entity, name, Query.IsEmpty.class);
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IS EMPTY ");

                return position;
            }
        });

        queryHandlers.put(Query.IsNotEmpty.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IsNotEmpty isNotEmpty = (Query.IsNotEmpty) criterion;
                final String name = isNotEmpty.getProperty();
                validateProperty(entity, name, Query.IsNotEmpty.class);
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IS EMPTY ");

                return position;
            }
        });

        queryHandlers.put(Query.IsNotNull.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IsNotNull isNotNull = (Query.IsNotNull) criterion;
                final String name = isNotNull.getProperty();
                validateProperty(entity, name, Query.IsNotNull.class);
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IS NOT NULL ");

                return position;
            }
        });

        queryHandlers.put(Query.IdEquals.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.IdEquals eq = (Query.IdEquals) criterion;
                PersistentProperty prop = entity.getIdentity();
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, prop.getName(), position, "=", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.NotEquals.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.NotEquals eq = (Query.NotEquals) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.NotEquals.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " != ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.GreaterThan.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.GreaterThan eq = (Query.GreaterThan) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.GreaterThan.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " > ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.LessThanEquals.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.LessThanEquals eq = (Query.LessThanEquals) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.LessThanEquals.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " <= ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.GreaterThanEquals.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.GreaterThanEquals eq = (Query.GreaterThanEquals) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.GreaterThanEquals.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " >= ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.Between.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.Between between = (Query.Between) criterion;
                final Object from = between.getFrom();
                final Object to = between.getTo();

                final String name = between.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.Between.class);
                Class propType = prop.getType();
                final String qualifiedName = logicalName + DOT + name;
                whereClause.append(OPEN_BRACKET)
                           .append(qualifiedName)
                           .append(" >= ")
                           .append(PARAMETER_PREFIX)
                           .append(++position);
                whereClause.append(" AND ")
                           .append(qualifiedName)
                           .append(" <= ")
                           .append(PARAMETER_PREFIX)
                           .append(++position)
                           .append(CLOSE_BRACKET);

                parameters.add(conversionService.convert( from, propType ));
                parameters.add(conversionService.convert( to, propType ));
                return position;
            }
        });

        queryHandlers.put(Query.LessThan.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.LessThan eq = (Query.LessThan) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.LessThan.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " < ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.Like.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.Like eq = (Query.Like) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.Like.class);
                Class propType = prop.getType();
                position = appendCriteriaForOperator(whereClause, logicalName, name, position, " like ", hibernateCompatible);
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.ILike.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.ILike eq = (Query.ILike) criterion;
                final String name = eq.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.ILike.class);
                Class propType = prop.getType();
                whereClause.append("lower(")
                 .append(logicalName)
                 .append(DOT)
                 .append(name)
                 .append(")")
                 .append(" like lower(")
                 .append(PARAMETER_PREFIX)
                 .append(++position)
                 .append(")");
                parameters.add(conversionService.convert( eq.getValue(), propType ));
                return position;
            }
        });

        queryHandlers.put(Query.In.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.In inQuery = (Query.In) criterion;
                final String name = inQuery.getProperty();
                PersistentProperty prop = validateProperty(entity, name, Query.In.class);
                Class propType = prop.getType();
                whereClause.append(logicalName)
                           .append(DOT)
                           .append(name)
                           .append(" IN (");
                QueryableCriteria subquery = inQuery.getSubquery();
                if(subquery != null) {
                    buildSubQuery(q, whereClause, position, parameters, conversionService, allowJoins, hibernateCompatible, subquery);
                }
                else {
                    for (Iterator i = inQuery.getValues().iterator(); i.hasNext();) {
                        Object val = i.next();
                        whereClause.append(PARAMETER_PREFIX);
                        whereClause.append(++position);
                        if (i.hasNext()) {
                            whereClause.append(COMMA);
                        }
                        parameters.add(conversionService.convert(val, propType));
                    }
                }
                whereClause.append(CLOSE_BRACKET);

                return position;
            }
        });

        queryHandlers.put(Query.NotIn.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.NotIn notIn = (Query.NotIn) criterion;
                String comparisonExpression = " NOT IN (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, notIn, comparisonExpression);
            }
        });

        queryHandlers.put(Query.EqualsAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.EqualsAll equalsAll = (Query.EqualsAll) criterion;
                String comparisonExpression = " = ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.NotEqualsAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion equalsAll = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " != ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.GreaterThanAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion equalsAll = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " > ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.GreaterThanSome.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion equalsAll = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " > SOME (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.GreaterThanEqualsAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion equalsAll = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " >= ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.GreaterThanEqualsSome.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion equalsAll = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " >= SOME (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, equalsAll, comparisonExpression);
            }
        });

        queryHandlers.put(Query.LessThanAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion subqueryCriterion = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " < ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression);
            }
        });

        queryHandlers.put(Query.LessThanSome.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion subqueryCriterion = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " < SOME (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression);
            }
        });

        queryHandlers.put(Query.LessThanEqualsAll.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion subqueryCriterion = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " <= ALL (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression);
            }
        });

        queryHandlers.put(Query.LessThanEqualsSome.class, new QueryHandler() {
            public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
                Query.SubqueryCriterion subqueryCriterion = (Query.SubqueryCriterion) criterion;
                String comparisonExpression = " <= SOME (";
                return handleSubQuery(entity, q, whereClause, logicalName, position, parameters, conversionService, allowJoins, hibernateCompatible, subqueryCriterion, comparisonExpression);
            }
        });

    }

    protected static int handleSubQuery(PersistentEntity entity, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible, Query.SubqueryCriterion equalsAll, String comparisonExpression) {
        final String name = equalsAll.getProperty();
        validateProperty(entity, name, Query.In.class);
        QueryableCriteria subquery = equalsAll.getValue();
        whereClause.append(logicalName)
                .append(DOT)
                .append(name)
                .append(comparisonExpression);
        buildSubQuery(q, whereClause, position, parameters, conversionService, allowJoins, hibernateCompatible, subquery);
        whereClause.append(CLOSE_BRACKET);
        return position;
    }

    protected static void buildSubQuery(StringBuilder q, StringBuilder whereClause, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible, QueryableCriteria subquery) {
        PersistentEntity associatedEntity = subquery.getPersistentEntity();
        String associatedEntityName = associatedEntity.getName();
        String associatedEntityLogicalName = associatedEntity.getDecapitalizedName() + position;
        whereClause.append("SELECT ");
        buildSelect(whereClause, subquery.getProjections(), associatedEntityLogicalName, associatedEntity);
        whereClause.append(" FROM ")
                .append(associatedEntityName)
                .append(' ')
                .append(associatedEntityLogicalName)
                .append(" WHERE ");
        List<Query.Criterion> criteria = subquery.getCriteria();
        for (Query.Criterion subCriteria : criteria) {
            QueryHandler queryHandler = queryHandlers.get(subCriteria.getClass());
            queryHandler.handle(associatedEntity, subCriteria, q, whereClause, associatedEntityLogicalName, position, parameters, conversionService, allowJoins, hibernateCompatible);
        }
    }

    private static int handleAssociationCriteria(StringBuilder query, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, Association<?> association, Query.Junction associationCriteria, List<Query.Criterion> associationCriteriaList, boolean hibernateCompatible) {
        if (association instanceof ToOne) {
            final String associationName = association.getName();
            logicalName = logicalName + DOT + associationName;
            return buildWhereClauseForCriterion(association.getAssociatedEntity(), associationCriteria, query, whereClause, logicalName, associationCriteriaList, position, parameters, conversionService, allowJoins, hibernateCompatible);
        }

        if (association != null) {
            final String associationName = association.getName();
            // TODO: Allow customization of join strategy!
            String joinType = " INNER JOIN ";
            query.append(joinType)
             .append(logicalName)
             .append(DOT)
             .append(associationName)
             .append(SPACE)
             .append(associationName);

            return buildWhereClauseForCriterion(association.getAssociatedEntity(), associationCriteria, query, whereClause, associationName, associationCriteriaList, position, parameters, conversionService, allowJoins, hibernateCompatible);
        }

        return position;
    }

    private void buildUpdateStatement(StringBuilder queryString, Map<String, Object> propertiesToUpdate, List parameters, boolean hibernateCompatible) {
        queryString.append(SPACE).append("SET");

        // keys need to be sorted before query is built
        Set<String> keys = new TreeSet<String>(propertiesToUpdate.keySet());

        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            PersistentProperty prop = entity.getPropertyByName(propertyName);
            if (prop == null) throw new InvalidDataAccessResourceUsageException("Property '"+propertyName+"' of class '"+entity.getName()+"' specified in update does not exist");

            parameters.add(propertiesToUpdate.get(propertyName));
            queryString.append(SPACE).append(logicalName).append(DOT).append(propertyName).append('=');
            queryString.append(PARAMETER_PREFIX).append(parameters.size());
            if (iterator.hasNext()) {
                queryString.append(COMMA);
            }
        }
    }

    private static void appendPropertyComparison(StringBuilder q, String logicalName, String propertyName, String otherProperty, String operator) {
        q.append(logicalName)
         .append(DOT)
         .append(propertyName)
         .append(operator)
         .append(logicalName)
         .append(DOT)
         .append(otherProperty);
    }

    private static PersistentProperty validateProperty(PersistentEntity entity, String name, Class criterionType) {
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && identity.getName().equals(name)) {
            return identity;
        }
        PersistentProperty[] compositeIdentity = ((AbstractPersistentEntity) entity).getCompositeIdentity();
        if(compositeIdentity != null) {
            for (PersistentProperty property : compositeIdentity) {
                if(property.getName().equals(name)) {
                    return property;
                }
            }
        }
        PersistentProperty prop = entity.getPropertyByName(name);
        if (prop == null) {
            throw new InvalidDataAccessResourceUsageException("Cannot use [" +
                  criterionType.getSimpleName() + "] criterion on non-existent property: " + name);
        }
        return prop;
    }

    private static interface QueryHandler {
        public int handle(PersistentEntity entity, Query.Criterion criterion, StringBuilder q, StringBuilder whereClause, String logicalName, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible);
    }

    private List buildWhereClause(PersistentEntity entity, Query.Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName, boolean allowJoins) {
        List parameters = new ArrayList();
        return buildWhereClause(entity, criteria, q, whereClause, logicalName, allowJoins, parameters);
    }

    private List buildWhereClause(PersistentEntity entity, Query.Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName, boolean allowJoins, List parameters) {
        if (!criteria.isEmpty()) {
            int position = parameters.size();
            final List<Query.Criterion> criterionList = criteria.getCriteria();
            whereClause.append(WHERE_CLAUSE);
            if (criteria instanceof Query.Negation) {
                whereClause.append(NOT_CLAUSE);
            }
            whereClause.append(OPEN_BRACKET);
            position = buildWhereClauseForCriterion(entity, criteria, q, whereClause, logicalName,
                    criterionList, position, parameters,
                    conversionService, allowJoins, this.hibernateCompatible);
            q.append(whereClause.toString());
            q.append(CLOSE_BRACKET);
        }
        return parameters;
    }

    protected void appendOrder(StringBuilder queryString, String logicalName) {
        if (!orders.isEmpty()) {
            queryString.append( ORDER_BY_CLAUSE);
            for (Query.Order order : orders) {
                queryString.append(logicalName)
                           .append(DOT)
                           .append(order.getProperty())
                           .append(SPACE)
                           .append(order.getDirection().toString())
                           .append(SPACE);
            }
        }
    }

    static int buildWhereClauseForCriterion(PersistentEntity entity,
                                            Query.Junction criteria, StringBuilder q, StringBuilder whereClause, String logicalName,
                                            final List<Query.Criterion> criterionList, int position, List parameters, ConversionService conversionService, boolean allowJoins, boolean hibernateCompatible) {
        for (Iterator<Query.Criterion> iterator = criterionList.iterator(); iterator.hasNext();) {
            Query.Criterion criterion = iterator.next();

            final String operator = criteria instanceof Query.Conjunction ? LOGICAL_AND : LOGICAL_OR;
            QueryHandler qh = queryHandlers.get(criterion.getClass());
            if (qh != null) {

                position = qh.handle(entity, criterion, q, whereClause, logicalName,
                        position, parameters, conversionService, allowJoins, hibernateCompatible);
            }
            else if (criterion instanceof AssociationCriteria) {

                if (!allowJoins) {
                    throw new InvalidDataAccessResourceUsageException("Joins cannot be used in a DELETE or UPDATE operation");
                }
                AssociationCriteria ac = (AssociationCriteria) criterion;
                Association association = ac.getAssociation();
                List<Query.Criterion> associationCriteriaList = ac.getCriteria();
                handleAssociationCriteria(q, whereClause, logicalName, position, parameters, conversionService, allowJoins, association, new Query.Conjunction(), associationCriteriaList, hibernateCompatible);
            }
            else {
                throw new InvalidDataAccessResourceUsageException("Queries of type "+criterion.getClass().getSimpleName()+" are not supported by this implementation");
            }

            if (iterator.hasNext()) {
                whereClause.append(operator);
            }
        }

        return position;
    }
}
