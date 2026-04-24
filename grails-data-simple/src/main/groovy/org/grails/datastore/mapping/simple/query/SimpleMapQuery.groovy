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
package org.grails.datastore.mapping.simple.query

import java.util.regex.Pattern

import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.util.Assert

import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValue
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.SimpleMapSession
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister
import groovy.transform.CompileDynamic

/**
 * Simple query implementation that queries a map of objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileDynamic
class SimpleMapQuery extends Query {

    private String family
    private SimpleMapEntityPersister entityPersister

    SimpleMapQuery(SimpleMapSession session, PersistentEntity entity, SimpleMapEntityPersister entityPersister) {
        super(session, entity)
        family = getFamily(entity)
        this.entityPersister = entityPersister
    }

    protected Map<String, Map> getDatastoreMap() {
        ((SimpleMapSession)session).getBackingMap()
    }

    protected Map getIndices() {
        ((SimpleMapSession)session).getIndices()
    }

    List list(Map params) {
        if (params.containsKey("max")) {
            this.max = Integer.valueOf(params.max.toString())
        }
        if (params.containsKey("offset")) {
            this.offset = Integer.valueOf(params.offset.toString())
        }
        if (params.containsKey("sort")) {
            def sort = params.sort.toString()
            def order = params.order?.toString() ?: "asc"
            if (order == "desc") {
                this.order(Query.Order.desc(sort))
            } else {
                this.order(Query.Order.asc(sort))
            }
        }
        return list()
    }

    protected List executeQuery(PersistentEntity entity, Query.Junction criteria) {
        def results = []
        def entityMap = [:]
        def datastore = getDatastoreMap()
        if (criteria.isEmpty()) {
            populateQueryResult(datastore[family].keySet().toList(), entityMap)
        }
        else {
            def criteriaList = criteria.getCriteria()
            entityMap = executeSubQuery(criteria, criteriaList)
            if (!entity.isRoot()) {
                def childKeys = datastore[family].keySet()
                entityMap = entityMap.subMap(childKeys)
            }
        }

        def nullEntries = entityMap.entrySet().findAll { it.value == null }
        entityMap.keySet().removeAll(nullEntries.collect { it.key })

        if (orderBy) {
            orderBy.reverseEach { Query.Order order ->
                boolean desc = order.direction == Query.Order.Direction.DESC
                entityMap = entityMap.sort { a, b ->
                    int cmp = (a.value."${order.property}" <=> b.value."${order.property}")
                    return desc ? -cmp : cmp
                }
            }
        }
        if (projections.isEmpty()) {
            results = entityMap.keySet().collect { session.retrieve(entity.javaClass, it as Serializable) }
        }
        else {
            def projectionList = projections.projectionList
            def projectionCount = projectionList.size()
            def entityList = entityMap.values()

            projectionList.each { Query.Projection p ->

                if (p instanceof Query.IdProjection) {
                    results << entityMap.keySet().toList()
                }
                else if (p instanceof Query.CountProjection) {
                    results << [entityList.size()]
                }
                else if (p instanceof Query.CountDistinctProjection) {
                    results << [entityList.collect { it."$p.propertyName" }.unique().size()]
                }
                else if (p instanceof Query.PropertyProjection) {
                    def propertyValues = entityList.collect { it."$p.propertyName" }
                    if (p instanceof Query.MaxProjection) {
                        results << [propertyValues.max()]
                    }
                    else if (p instanceof Query.MinProjection) {
                        results << [propertyValues.min()]
                    }
                    else if (p instanceof Query.SumProjection) {
                        results << [propertyValues.sum()]
                    }
                    else if (p instanceof Query.AvgProjection) {
                        def res = propertyValues.isEmpty() ? 0 : propertyValues.sum() / propertyValues.size()
                        if (res instanceof BigDecimal) res = res.doubleValue()
                        results << [res]
                    }
                    else {
                        PersistentProperty prop = entity.getPropertyByName(p.propertyName)
                        if (prop instanceof ToOne) {
                             propertyValues = propertyValues.collect { session.retrieve(prop.type, it as Serializable) }
                        }
                        results << propertyValues
                    }
                }
            }

            if (projectionCount == 1) {
                results = results[0]
            }
            else {
                def finalResults = []
                if (!results.isEmpty()) {
                    def firstList = results[0]
                    if (firstList instanceof Collection) {
                        for (int i = 0; i < firstList.size(); i++) {
                            def row = []
                            for (int j = 0; j < projectionCount; j++) {
                                row << results[j][i]
                            }
                            finalResults << row
                        }
                    }
                }
                results = finalResults
            }
        }

        if (max > -1) {
            def from = offset > 0 ? offset : 0
            def to = max + from - 1
            if (to >= results.size()) to = results.size() - 1
            if (from < results.size()) {
                results = results[from..to]
            }
            else {
                results = []
            }
        }
        else if (offset > 0) {
            def from = offset
            def to = results.size() - 1
            if (from < results.size()) {
                results = results[from..to]
            }
            else {
                results = []
            }
        }

        return results
    }

    private String getFamily(PersistentEntity entity) {
        def cm = entity.getMapping()
        String table = null
        if (cm.getMappedForm() != null) {
            table = cm.getMappedForm().getFamily()
        }
        if (table == null) table = entity.getJavaClass().getName()
        
        def datastore = (SimpleMapDatastore)session.datastore
        def connectionName = datastore.connectionSources.defaultConnectionSource.name
        if (!org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT.equals(connectionName)) {
            table = "${connectionName}:${table}"
        }
        
        return table
    }

    private Map executeSubQuery(Query.Junction criteria, criteriaList) {
        def entityMap = [:]
        if (criteria instanceof Query.Conjunction) {
            def resultList = executeSubQueryInternal(criteria, criteriaList)

            if (resultList.isEmpty()) {
                return entityMap
            }

            def finalKeys = resultList[0]
            for (int i = 1; i < resultList.size(); i++) {
                finalKeys = finalKeys.intersect(resultList[i])
            }

            populateQueryResult(finalKeys, entityMap)
        }
        else if (criteria instanceof Query.Disjunction) {
            def resultList = executeSubQueryInternal(criteria, criteriaList)
            if (resultList.isEmpty()) {
                return entityMap
            }

            def finalKeys = resultList[0]
            for (int i = 1; i < resultList.size(); i++) {
                finalKeys = finalKeys.plus(resultList[i])
            }
            populateQueryResult(finalKeys.unique(), entityMap)
        }
        return entityMap
    }

    private List executeSubQueryInternal(criteria, criteriaList) {
        def resultList = []
        for (Query.Criterion c in criteriaList) {
            def handler = handlers[c.class]
            if (handler != null) {
                def property = entity.getPropertyByName(c.property)
                resultList << handler.call(c, property)
            }
            else if (c instanceof Query.Junction) {
                resultList << executeSubQuery(c, c.criteria).keySet().toList()
            }
            else if (c instanceof FunctionCallingCriterion) {
                def property = entity.getPropertyByName(c.property)
                def functionName = c.functionName
                Closure function = null
                if (functionName == 'year') {
                    function = { val ->
                        if (val instanceof Date) {
                           def cal = new GregorianCalendar()
                           cal.time = val
                           return cal.get(Calendar.YEAR)
                        }
                        return val
                    }
                }
                
                if (function != null) {
                    resultList << handlers[Query.Equals].call(c.propertyCriterion, property, function)
                }
                else {
                    throw new InvalidDataAccessResourceUsageException("Unsupported query function $functionName")
                }
            }
            else {
                def associationHandler = associationQueryHandlers[c.class]
                if (associationHandler != null) {
                    resultList << associationHandler.call(c, entity.getPropertyByName(c.association.name))
                }
                else {
                    throw new InvalidDataAccessResourceUsageException("Unsupported query criterion $c")
                }
            }
        }
        return resultList
    }

    private void populateQueryResult(List keys, Map entityMap) {
        def datastore = getDatastoreMap()
        for (key in keys) {
            entityMap[key] = datastore[family][key]
        }
    }

    protected def marshalValue(PersistentProperty property, value) {
        if (value != null && property instanceof Custom) {
            def marshaller = ((Custom)property).customTypeMarshaller
            if (marshaller.targetType.isInstance(value)) {
                return marshaller.write(property, value, [:])
            }
        }
        return value
    }

    def handlers = [
        (Query.In): { Query.In inList, PersistentProperty property ->
            def disjunction = new Query.Disjunction()
            for (value in inList.values) {
                disjunction.add(Restrictions.eq(inList.name, value))
            }

            executeSubQueryInternal(disjunction, disjunction.criteria).flatten().unique()
        },
        (Query.IdEquals): { Query.IdEquals equals, PersistentProperty property ->
            def indexer = entityPersister.getPropertyIndexer(entity.identity)
            return indexer.query(equals.value)
        },
        (Query.Equals): { Query.Equals equals, PersistentProperty property, Closure function = null, boolean onValue = false ->
            def indexer = entityPersister.getPropertyIndexer(property)
            def value = subqueryIfNecessary(equals)
            value = marshalValue(property, value)

            if (value && property instanceof ToOne && property.type.isInstance(value)) {
               value = session.getPersister(value).getObjectIdentifier(value)
            }

            if (function != null) {
                def allEntities = getDatastoreMap()[family]
                allEntities.findAll {
                    def val = resolveIfEmbedded(property.name, it.value)
                    def calculatedValue = function(val)
                    calculatedValue == value
                }.collect { it.key }
            }
            else {
                if (equals.property.contains('.') || value == null) {
                    def allEntities = getDatastoreMap()[family]
                    return allEntities.findAll {
                        def val = resolveIfEmbedded(equals.property, it.value)
                        val == value
                    }.collect { it.key }
                }
                else {
                    return indexer.query(value)
                }
            }
        },
        (Query.NotEquals): { Query.NotEquals equals, PersistentProperty property, Closure function = null, boolean onValue = false ->
            def indexed = handlers[Query.Equals].call(new Query.Equals(equals.property, equals.value), property, function)
            return negateResults(indexed)
        },
        (Query.IsNull): { Query.IsNull equals, PersistentProperty property, Closure function = null , boolean onValue = false ->
            handlers[Query.Equals].call(new Query.Equals(equals.property, null), property, function)
        },
        (Query.IsNotNull): { Query.IsNotNull equals, PersistentProperty property, Closure function = null, boolean onValue = false ->
            def indexed = handlers[Query.Equals].call(new Query.Equals(equals.property, null), property, function)
            return negateResults(indexed)
        },
        (Query.Between): { Query.Between between, PersistentProperty property, Closure function = null, boolean onValue = false ->
            def from = marshalValue(property, between.from)
            def to = marshalValue(property, between.to)
            def name = between.property
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll {
                def val = (function != null ? function(resolveIfEmbedded(name, it.value)) : resolveIfEmbedded(name, it.value))
                val >= from && val <= to
            }.collect { it.key }
        },
        (Query.GreaterThan): { Query.GreaterThan gt, PersistentProperty property, Closure function = null, boolean onValue = false ->
            def name = gt.property
            final value = marshalValue(property, subqueryIfNecessary(gt))
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll {
                def val = (function != null ? function(resolveIfEmbedded(name, it.value)) : resolveIfEmbedded(name, it.value))
                val > value
            }.collect { it.key }
        },
        (Query.LessThan): { Query.LessThan lt, PersistentProperty property ->
            def name = lt.property
            final value = marshalValue(property, subqueryIfNecessary(lt))
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll {
                def val = resolveIfEmbedded(name, it.value)
                val < value
            }.collect { it.key }
        },
        (Query.GreaterThanEquals): { Query.GreaterThanEquals gt, PersistentProperty property ->
            def name = gt.property
            final value = marshalValue(property, subqueryIfNecessary(gt))
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll {
                def val = resolveIfEmbedded(name, it.value)
                val >= value
            }.collect { it.key }
        },
        (Query.LessThanEquals): { Query.LessThanEquals lte, PersistentProperty property ->
            def name = lte.property
            final value = marshalValue(property, subqueryIfNecessary(lte))
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll {
                def val = resolveIfEmbedded(name, it.value)
                val <= value
            }.collect { it.key }
        },
        (Query.EqualsAll): { Query.EqualsAll equalsAll, PersistentProperty property ->
            def name = equalsAll.property
            final values = subqueryIfNecessary(equalsAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) == it }
            }.collect { it.key }
        },
        (Query.NotEqualsAll): { Query.NotEqualsAll equalsAll, PersistentProperty property ->
            def name = equalsAll.property
            final values = subqueryIfNecessary(equalsAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) != it }
            }.collect { it.key }
        },
        (Query.GreaterThanAll): { Query.GreaterThanAll gtAll, PersistentProperty property ->
            def name = gtAll.property
            final values = subqueryIfNecessary(gtAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) > it }
            }.collect { it.key }
        },
        (Query.LessThanAll): { Query.LessThanAll ltAll, PersistentProperty property ->
            def name = ltAll.property
            final values = subqueryIfNecessary(ltAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) < it }
            }.collect { it.key }
        },
        (Query.GreaterThanEqualsAll): { Query.GreaterThanEqualsAll geAll, PersistentProperty property ->
            def name = geAll.property
            final values = subqueryIfNecessary(geAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) >= it }
            }.collect { it.key }
        },
        (Query.LessThanEqualsAll): { Query.LessThanEqualsAll leAll, PersistentProperty property ->
            def name = leAll.property
            final values = subqueryIfNecessary(leAll, false)
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                values.every { resolveIfEmbedded(name, entry.value) <= it }
            }.collect { it.key }
        },
        (Query.Like): { Query.Like like, PersistentProperty property ->
            def indexer = entityPersister.getPropertyIndexer(property)

            def root = indexer.indexRoot
            def regexFormat = like.pattern.replaceAll('%', '.*?')
            def pattern = "${root}:${regexFormat}"
            def matchingIndices = getIndices().findAll { key, value ->
                key ==~ pattern
            }

            Set result = []
            for (indexed in matchingIndices) {
                result.addAll(indexed.value)
            }

            return result.toList()
        },
        (Query.ILike): { Query.ILike like, PersistentProperty property ->
            def regexFormat = like.pattern.replaceAll('%', '.*?')
            return executeLikeWithRegex(property, regexFormat)
        },
        (Query.RLike): { Query.RLike like, PersistentProperty property ->
            def regexFormat = like.pattern
            return executeLikeWithRegex(property, regexFormat)
        },
        (Query.EqualsProperty): { Query.EqualsProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) == it.value[other] }.collect { it.key }
        },
        (Query.NotEqualsProperty): { Query.NotEqualsProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) != it.value[other] }.collect { it.key }
        },
        (Query.GreaterThanProperty): { Query.GreaterThanProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) > it.value[other] }.collect { it.key }
        },
        (Query.GreaterThanEqualsProperty): { Query.GreaterThanEqualsProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) >= it.value[other] }.collect { it.key }
        },
        (Query.LessThanProperty): { Query.LessThanProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) < it.value[other] }.collect { it.key }
        },
        (Query.LessThanEqualsProperty): { Query.LessThanEqualsProperty gt, PersistentProperty property ->
            def name = gt.property
            def other = gt.otherProperty
            def allEntities = getDatastoreMap()[family]

            allEntities.findAll { resolveIfEmbedded(name, it.value) <= it.value[other] }.collect { it.key }
        },
        (Query.SizeEquals): { Query.SizeEquals se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() == value }
        },
       (Query.SizeNotEquals): { Query.SizeNotEquals se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() != value }
        },
        (Query.SizeGreaterThan): { Query.SizeGreaterThan se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() > value }
        },
        (Query.SizeGreaterThanEquals): { Query.SizeGreaterThanEquals se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() >= value }
        },
        (Query.SizeLessThan): { Query.SizeLessThan se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() < value }
        },
        (Query.SizeLessThanEquals): { Query.SizeLessThanEquals se, PersistentProperty property ->
            def allEntities = getDatastoreMap()[family]
            final value = subqueryIfNecessary(se)
            queryAssociationList(allEntities, property as Association) { it.size() <= value }
        }
    ]

    def associationQueryHandlers = [
        (AssociationQuery): { AssociationQuery aq, PersistentProperty property ->
            Query.Junction queryCriteria = aq.criteria
            def association = aq.association
            def associatedEntity = association.associatedEntity
            def associatedPersister = session.getPersister(associatedEntity.javaClass)
            def associatedQuery = new SimpleMapQuery(session, associatedEntity, associatedPersister as SimpleMapEntityPersister)
            associatedQuery.criteria = queryCriteria
            def associatedIds = associatedQuery.list().collect { associatedPersister.getObjectIdentifier(it) }
            
            // Now find owners that have these associated IDs
            def allEntities = getDatastoreMap()[family]
            allEntities.findAll { entry ->
                if (association instanceof ToOne) {
                    def val = entry.value[association.name]
                    return associatedIds.contains(val)
                }
                else {
                    def indexer = entityPersister.getAssociationIndexer(entry.value as Map, association)
                    def ownerId = entry.key
                    def results = indexer.query(ownerId)
                    return !results.intersect(associatedIds).isEmpty()
                }
            }.collect { it.key }
        }
    ]

    protected List executeAssociationSubQuery(Map allEntities, PersistentEntity entity, Query.Junction criteria, PersistentProperty property) {
        def criteriaList = criteria.getCriteria()
        def entityMap = executeSubQueryOnMap(entity, criteria, criteriaList, allEntities)
        return entityMap.keySet().toList()
    }

    private Map executeSubQueryOnMap(PersistentEntity entity, Query.Junction criteria, List criteriaList, Map allEntities) {
        def entityMap = [:]
        if (criteria instanceof Query.Conjunction) {
            def resultList = executeSubQueryInternalOnMap(entity, criteria, criteriaList, allEntities)

            if (resultList.isEmpty()) {
                return entityMap
            }

            def finalKeys = resultList[0]
            for (int i = 1; i < resultList.size(); i++) {
                finalKeys = finalKeys.intersect(resultList[i])
            }

            allEntities.each { k, v -> if (finalKeys.contains(k)) entityMap[k] = v }
        }
        else if (criteria instanceof Query.Disjunction) {
            def resultList = executeSubQueryInternalOnMap(entity, criteria, criteriaList, allEntities)
            if (resultList.isEmpty()) {
                return entityMap
            }

            def finalKeys = resultList[0]
            for (int i = 1; i < resultList.size(); i++) {
                finalKeys = finalKeys.plus(resultList[i])
            }
            allEntities.each { k, v -> if (finalKeys.contains(k)) entityMap[k] = v }
        }
        return entityMap
    }

    private List executeSubQueryInternalOnMap(PersistentEntity entity, Query.Junction criteria, List criteriaList, Map allEntities) {
        def resultList = []
        for (Query.Criterion c in criteriaList) {
            if (c instanceof Query.PropertyCriterion) {
                def name = c.property
                def value = c.value
                if (c instanceof Query.Equals) {
                    resultList << allEntities.findAll { it.value[name] == value }.collect { it.key }
                }
                else if (c instanceof Query.NotEquals) {
                    resultList << allEntities.findAll { it.value[name] != value }.collect { it.key }
                }
            }
        }
        return resultList
    }

    private ArrayList negateResults(List results) {
        def entityMap = getDatastoreMap()[family]
        if (entityMap == null) return []
        def allIds = new ArrayList(entityMap.keySet())
        // Ensure we are comparing IDs to IDs by using toString() as a universal normalizer for SimpleMap IDs
        def resultIds = results.collect { (session.mappingContext.isPersistentEntity(it) ? session.getPersister(it).getObjectIdentifier(it) : it)?.toString() }
        def normalizedAllIds = allIds.collect { it.toString() }
        
        normalizedAllIds.removeAll(resultIds)
        
        // Map back to original IDs
        return allIds.findAll { normalizedAllIds.contains(it.toString()) }
    }

    private def subqueryIfNecessary(Query.PropertyCriterion pc, boolean uniqueResult = true) {
        def value = pc.value
        if (value instanceof QueryableCriteria) {
            def query = session.createQuery(value.persistentEntity.javaClass)
            for (Query.Criterion c in value.getCriteria()) {
                query.add(c)
            }
            
            // Copy projections from the subquery
            for (Query.Projection p in value.getProjections()) {
                query.projections().add(p)
            }
            
            // If it's a property comparison and NO projection exists, default to ID
            if (query.projections.isEmpty()) {
                query.projections().add(Restrictions.id())
            }
            
            def results = query.list()
            if (uniqueResult) {
                def res = results.isEmpty() ? null : results[0]
                if (session.mappingContext.isPersistentEntity(res)) {
                    res = session.getPersister(res).getObjectIdentifier(res)
                }
                if (res instanceof BigDecimal) res = res.doubleValue()
                return res
            }
            else {
                return results.collect { 
                    def res = session.mappingContext.isPersistentEntity(it) ? session.getPersister(it).getObjectIdentifier(it) : it
                    if (res instanceof BigDecimal) res = res.doubleValue()
                    return res
                }
            }
        }
        return value
    }

    private def resolveIfEmbedded(String name, Map map) {
        if (name.contains('.')) {
            def parts = name.split(/\./)
            def current = map
            for (part in parts) {
                if (current instanceof Map) {
                    current = current[part]
                }
                else {
                    break
                }
            }
            return current
        }
        else {
            return map[name]
        }
    }

    protected List executeLikeWithRegex(PersistentProperty property, regexFormat) {
        def indexer = entityPersister.getPropertyIndexer(property)

        def root = indexer.indexRoot
        def pattern = Pattern.compile("${root}:${regexFormat}", Pattern.CASE_INSENSITIVE)
        def matchingIndices = getIndices().findAll { key, value ->
            pattern.matcher(key as String).matches()
        }

        Set result = []
        for (indexed in matchingIndices) {
            result.addAll(indexed.value)
        }

        return result.toList()
    }

    protected queryAssociationList(Map allEntities, Association association, Closure callable) {
        allEntities.findAll {
            def indexer = entityPersister.getAssociationIndexer(it.value as Map, association)
            def results = indexer.query(it.key)
            callable.call(results)
        }.keySet().toList()
    }
}
