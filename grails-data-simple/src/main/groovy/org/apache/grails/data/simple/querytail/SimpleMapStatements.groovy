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
package org.apache.grails.data.simple.querytail

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailRedactor
import org.grails.datastore.mapping.keyvalue.mapping.config.Family
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

/**
 * Builds the statements the simple datastore records for query-tail, in the shape of SQL, from a
 * query and the mapping rather than from text, so no value ever reaches one: every value is
 * {@link QueryTailRedactor#PLACEHOLDER}, and so is every name the mapping does not know, such as a
 * property name a query took from a request, and every segment of a path past one.
 * A query is recorded as {@code select * from Book where title = ? order by title asc limit ?}, with
 * its projections in place of {@code *}, the criteria of an association as paths through it and a
 * subquery in parentheses. An entry read by its key is {@code select * from Book where id = ?},
 * entries read by their keys together are {@code select * from Book where id in (?)}, and the writes
 * are {@code insert into Book}, {@code update Book where id = ?} and {@code delete from Book where id = ?}.
 * The name after {@code from} is the family the entity's entries are kept in: its class name, unless
 * its mapping names another.
 *
 * @since 9.0
 */
@CompileStatic
final class SimpleMapStatements {

    private static final String PLACEHOLDER = QueryTailRedactor.PLACEHOLDER

    private static final String ALL = '*'

    /** The functions a criterion may apply to a property, which the simple datastore evaluates. */
    private static final Set<String> FUNCTIONS = ['second', 'minute', 'hour', 'year', 'month', 'day', 'lower',
            'upper', 'trim', 'length'] as Set<String>

    private SimpleMapStatements() {
    }

    /**
     * @param entity the entity the query is for
     * @param family the family its entries are kept in
     * @param query the query, whose projections, order, offset and maximum are recorded
     * @param criteria the criteria it runs with
     * @return the statement of the query
     */
    static String select(PersistentEntity entity, String family, Query query, Query.Junction criteria) {
        List<String> groupBy = []
        StringBuilder out = new StringBuilder('select ')
                .append(projections(entity, query.projections().projectionList, groupBy))
                .append(' from ').append(family)
        String where = criteria == null ? null : junction(entity, '', criteria)
        if (where) {
            out.append(' where ').append(where)
        }
        if (groupBy) {
            out.append(' group by ').append(groupBy.join(', '))
        }
        List<Query.Order> orders = query.orderBy
        if (orders) {
            out.append(' order by ').append(orders.collect { Query.Order order -> sorted(entity, order) }.join(', '))
        }
        if (query.max != null) {
            out.append(' limit ').append(PLACEHOLDER)
        }
        if (query.offset != null) {
            out.append(' offset ').append(PLACEHOLDER)
        }
        out.toString()
    }

    /**
     * @return the statement of an entry read by its key
     */
    static String lookup(PersistentEntity entity, String family) {
        "select * from ${family} where ${identity(entity)} = ${PLACEHOLDER}".toString()
    }

    /**
     * @return the statement of entries read by their keys together
     */
    static String lookupAll(PersistentEntity entity, String family) {
        "select * from ${family} where ${identity(entity)} in (${PLACEHOLDER})".toString()
    }

    /**
     * @return the statement of an insert
     */
    static String insert(String family) {
        "insert into ${family}".toString()
    }

    /**
     * @return the statement of an update of an entry
     */
    static String update(PersistentEntity entity, String family) {
        "update ${family} where ${identity(entity)} = ${PLACEHOLDER}".toString()
    }

    /**
     * @return the statement of a delete of an entry
     */
    static String delete(PersistentEntity entity, String family) {
        "delete from ${family} where ${identity(entity)} = ${PLACEHOLDER}".toString()
    }

    /**
     * @return the family the entries of an entity are kept in, as the simple datastore names it
     */
    static String family(PersistentEntity entity) {
        Object mapped = entity.mapping?.mappedForm
        String family = mapped instanceof Family ? ((Family) mapped).family : null
        family ?: entity.javaClass.name
    }

    private static String projections(PersistentEntity entity, List<Query.Projection> projections, List<String> groupBy) {
        List<String> listed = []
        boolean distinct = false
        for (Query.Projection projection in projections) {
            if (projection instanceof Query.DistinctProjection) {
                distinct = true
            }
            else if (projection instanceof Query.IdProjection) {
                listed << identity(entity)
            }
            else if (projection instanceof Query.CountProjection) {
                listed << 'count(*)'
            }
            else if (projection instanceof Query.PropertyProjection) {
                listed << propertyProjection(entity, (Query.PropertyProjection) projection, groupBy)
            }
            else {
                listed << PLACEHOLDER
            }
        }
        String list = listed ? listed.join(', ') : ALL
        distinct ? "distinct ${list}".toString() : list
    }

    private static String propertyProjection(PersistentEntity entity, Query.PropertyProjection projection, List<String> groupBy) {
        String property = path(entity, '', projection.propertyName)
        if (projection instanceof Query.CountDistinctProjection) {
            return "count(distinct ${property})".toString()
        }
        if (projection instanceof Query.DistinctPropertyProjection) {
            return "distinct ${property}".toString()
        }
        if (projection instanceof Query.GroupPropertyProjection) {
            groupBy << property
            return property
        }
        String function = projection instanceof Query.AvgProjection ? 'avg' :
                projection instanceof Query.MaxProjection ? 'max' :
                projection instanceof Query.MinProjection ? 'min' :
                projection instanceof Query.SumProjection ? 'sum' : null
        function ? "${function}(${property})".toString() : property
    }

    /**
     * @return an order as its property and direction; whether it ignores case is not recorded, as the simple
     * datastore sorts by the value either way
     */
    private static String sorted(PersistentEntity entity, Query.Order order) {
        "${path(entity, '', order.property)} ${order.direction == Query.Order.Direction.DESC ? 'desc' : 'asc'}".toString()
    }

    /**
     * @return the criteria of a junction joined by its operator, a negation as {@code not (...)}, or null
     * when it has none; a nested junction joined by the other operator is put in parentheses, and one
     * joined by the same operator is not
     */
    private static String junction(PersistentEntity entity, String prefix, Query.Junction junction) {
        String operator = junction instanceof Query.Disjunction || junction instanceof Query.Negation ? 'or' : 'and'
        List<String> parts = []
        List<String> operators = []
        for (Query.Criterion criterion in junction.criteria) {
            String part = condition(entity, prefix, criterion)
            if (part) {
                parts << part
                operators << operatorOf(criterion)
            }
        }
        if (!parts) {
            return null
        }
        if (parts.size() > 1) {
            for (int i = 0; i < parts.size(); i++) {
                if (operators[i] != null && operators[i] != operator) {
                    parts[i] = "(${parts[i]})".toString()
                }
            }
        }
        String joined = parts.join(" ${operator} ")
        junction instanceof Query.Negation ? "not (${joined})".toString() : joined
    }

    /**
     * @return the operator a criterion joins its own criteria with, when it is a junction, or the criteria of an
     * association, with more than one, and null when it is a single condition or a negation, which is shown whole
     */
    private static String operatorOf(Query.Criterion criterion) {
        Query.Junction junction = criterion instanceof Query.Junction ? (Query.Junction) criterion :
                criterion instanceof AssociationQuery ? ((AssociationQuery) criterion).criteria : null
        if (junction == null || junction instanceof Query.Negation || junction.criteria.size() < 2) {
            return null
        }
        junction instanceof Query.Disjunction ? 'or' : 'and'
    }

    private static String condition(PersistentEntity entity, String prefix, Query.Criterion criterion) {
        if (criterion instanceof Query.Junction) {
            return junction(entity, prefix, (Query.Junction) criterion)
        }
        if (criterion instanceof AssociationQuery) {
            return association(entity, prefix, (AssociationQuery) criterion)
        }
        if (criterion instanceof Query.Exists) {
            return "exists ${subquery(((Query.Exists) criterion).subquery)}".toString()
        }
        if (criterion instanceof Query.NotExists) {
            return "not exists ${subquery(((Query.NotExists) criterion).subquery)}".toString()
        }
        if (criterion instanceof FunctionCallingCriterion) {
            FunctionCallingCriterion call = (FunctionCallingCriterion) criterion
            String function = FUNCTIONS.contains(call.functionName) ? call.functionName : PLACEHOLDER
            Query.PropertyCriterion applied = call.propertyCriterion
            String property = path(entity, prefix, applied.property)
            return call.onValue ?
                    "${property} ${operator(applied)} ${function}(${value(applied)})".toString() :
                    "${function}(${property}) ${operator(applied)} ${value(applied)}".toString()
        }
        if (!(criterion instanceof Query.PropertyNameCriterion)) {
            return PLACEHOLDER
        }
        Query.PropertyNameCriterion named = (Query.PropertyNameCriterion) criterion
        String property = criterion instanceof Query.IdEquals ? identity(entity) : path(entity, prefix, named.property)
        if (criterion instanceof Query.IsNull) {
            return "${property} is null".toString()
        }
        if (criterion instanceof Query.IsNotNull) {
            return "${property} is not null".toString()
        }
        if (criterion instanceof Query.IsEmpty) {
            return "${property} is empty".toString()
        }
        if (criterion instanceof Query.IsNotEmpty) {
            return "${property} is not empty".toString()
        }
        if (criterion instanceof Query.PropertyComparisonCriterion) {
            Query.PropertyComparisonCriterion compared = (Query.PropertyComparisonCriterion) criterion
            return "${property} ${comparison(compared)} ${path(entity, prefix, compared.otherProperty)}".toString()
        }
        if (!(criterion instanceof Query.PropertyCriterion)) {
            return "${property} ${PLACEHOLDER}".toString()
        }
        Query.PropertyCriterion valued = (Query.PropertyCriterion) criterion
        if (isSize(valued)) {
            return "size(${property}) ${operator(valued)} ${PLACEHOLDER}".toString()
        }
        if (criterion instanceof Query.Between) {
            return "${property} between ${PLACEHOLDER} and ${PLACEHOLDER}".toString()
        }
        if (criterion instanceof Query.In) {
            Query.In inList = (Query.In) criterion
            return "${property} in ${inList.subquery != null ? subquery(inList.subquery) : "(${PLACEHOLDER})"}".toString()
        }
        if (criterion instanceof Query.NotIn) {
            Query.NotIn notIn = (Query.NotIn) criterion
            return "${property} not in ${notIn.subquery != null ? subquery(notIn.subquery) : "(${PLACEHOLDER})"}".toString()
        }
        "${property} ${operator(valued)} ${value(valued)}".toString()
    }

    /**
     * @return the criteria of an association as paths through it
     */
    private static String association(PersistentEntity entity, String prefix, AssociationQuery query) {
        Association association = query.association
        PersistentEntity associated = association.associatedEntity
        String through = "${prefix}${path(entity, '', association.name)}.".toString()
        if (associated == null) {
            return "${through}${PLACEHOLDER}".toString()
        }
        junction(associated, through, query.criteria)
    }

    private static String value(Query.PropertyCriterion criterion) {
        criterion.value instanceof QueryableCriteria ? subquery((QueryableCriteria) criterion.value) : PLACEHOLDER
    }

    private static String subquery(QueryableCriteria criteria) {
        PersistentEntity entity = criteria?.persistentEntity
        if (entity == null) {
            return "(${PLACEHOLDER})".toString()
        }
        Query.Conjunction where = new Query.Conjunction()
        for (Query.Criterion criterion in criteria.criteria) {
            where.add(criterion)
        }
        String condition = junction(entity, '', where)
        String projected = projections(entity, criteria.projections ?: [], [])
        "(select ${projected} from ${family(entity)}${condition ? " where ${condition}" : ''})".toString()
    }

    private static boolean isSize(Query.PropertyCriterion criterion) {
        criterion instanceof Query.SizeEquals || criterion instanceof Query.SizeNotEquals ||
                criterion instanceof Query.SizeGreaterThan || criterion instanceof Query.SizeGreaterThanEquals ||
                criterion instanceof Query.SizeLessThan || criterion instanceof Query.SizeLessThanEquals
    }

    private static String operator(Query.PropertyCriterion criterion) {
        if (criterion instanceof Query.SubqueryCriterion) {
            return subqueryOperator((Query.SubqueryCriterion) criterion)
        }
        if (criterion instanceof Query.ILike) {
            return 'ilike'
        }
        if (criterion instanceof Query.RLike) {
            return 'rlike'
        }
        if (criterion instanceof Query.Like) {
            return 'like'
        }
        if (criterion instanceof Query.NotEquals || criterion instanceof Query.SizeNotEquals) {
            return '<>'
        }
        if (criterion instanceof Query.GreaterThanEquals || criterion instanceof Query.SizeGreaterThanEquals) {
            return '>='
        }
        if (criterion instanceof Query.GreaterThan || criterion instanceof Query.SizeGreaterThan) {
            return '>'
        }
        if (criterion instanceof Query.LessThanEquals || criterion instanceof Query.SizeLessThanEquals) {
            return '<='
        }
        if (criterion instanceof Query.LessThan || criterion instanceof Query.SizeLessThan) {
            return '<'
        }
        if (criterion instanceof Query.In) {
            return 'in'
        }
        if (criterion instanceof Query.Equals || criterion instanceof Query.IdEquals || criterion instanceof Query.SizeEquals) {
            return '='
        }
        PLACEHOLDER
    }

    private static String subqueryOperator(Query.SubqueryCriterion criterion) {
        if (criterion instanceof Query.NotIn) {
            return 'not in'
        }
        String quantifier = criterion instanceof Query.GreaterThanSome || criterion instanceof Query.GreaterThanEqualsSome ||
                criterion instanceof Query.LessThanSome || criterion instanceof Query.LessThanEqualsSome ? 'some' : 'all'
        String comparison = criterion instanceof Query.EqualsAll ? '=' :
                criterion instanceof Query.NotEqualsAll ? '<>' :
                criterion instanceof Query.GreaterThanAll || criterion instanceof Query.GreaterThanSome ? '>' :
                criterion instanceof Query.GreaterThanEqualsAll || criterion instanceof Query.GreaterThanEqualsSome ? '>=' :
                criterion instanceof Query.LessThanAll || criterion instanceof Query.LessThanSome ? '<' :
                criterion instanceof Query.LessThanEqualsAll || criterion instanceof Query.LessThanEqualsSome ? '<=' : null
        comparison ? "${comparison} ${quantifier}".toString() : PLACEHOLDER
    }

    private static String comparison(Query.PropertyComparisonCriterion criterion) {
        criterion instanceof Query.EqualsProperty ? '=' :
                criterion instanceof Query.NotEqualsProperty ? '<>' :
                criterion instanceof Query.GreaterThanEqualsProperty ? '>=' :
                criterion instanceof Query.GreaterThanProperty ? '>' :
                criterion instanceof Query.LessThanEqualsProperty ? '<=' :
                criterion instanceof Query.LessThanProperty ? '<' : PLACEHOLDER
    }

    private static String identity(PersistentEntity entity) {
        entity.identity?.name ?: 'id'
    }

    /**
     * @return a property path with each segment the mapping knows kept, from the entity or the association
     * the segment before it leads to, and every segment from the first it does not know on recorded as ?
     */
    private static String path(PersistentEntity entity, String prefix, String name) {
        if (name == null) {
            return "${prefix}${PLACEHOLDER}".toString()
        }
        StringBuilder out = new StringBuilder(prefix)
        PersistentEntity current = entity
        String[] segments = name.split('\\.', -1)
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append('.')
            }
            String segment = segments[i]
            PersistentProperty property = current == null ? null : current.getPropertyByName(segment)
            if (property == null && current != null && current.identity?.name == segment) {
                property = current.identity
            }
            if (property == null) {
                out.append(PLACEHOLDER)
                current = null
            }
            else {
                out.append(segment)
                current = property instanceof Association ? ((Association) property).associatedEntity : null
            }
        }
        out.toString()
    }
}
