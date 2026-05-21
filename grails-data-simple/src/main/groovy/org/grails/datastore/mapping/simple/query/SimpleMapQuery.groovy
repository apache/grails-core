/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS licenses this file
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

import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.SimpleMapSession
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.query.event.PostQueryEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * A query implementation for the simple map-based datastore
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleMapQuery extends Query {

    private SimpleMapEntityPersister entityPersister

    SimpleMapQuery(SimpleMapSession session, PersistentEntity entity, SimpleMapEntityPersister entityPersister) {
        super(session, entity)
        this.entityPersister = entityPersister
    }

    protected Map<Serializable, Map> getDatastoreMap() {
        ((SimpleMapSession)session).getBackingMap()
    }

    protected Map getIndices() {
        ((SimpleMapSession)session).getIndices()
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Query.Junction criteria) {
        def entityMap = [:]
        def datastore = getDatastoreMap()
        def family = getFamily()
        def familyMap = (Map) datastore[family] ?: [:]
        
        entityMap.putAll(familyMap)
        
        if (!criteria.isEmpty()) {
            def criteriaList = criteria.getCriteria()
            def subQueryResult = executeSubQuery(criteria, criteriaList)
            def filteredKeys = subQueryResult.keySet().collect { it instanceof Number ? it.longValue() : it } as Set
            
            entityMap = familyMap.findAll { entry ->
                def key = entry.key instanceof Number ? entry.key.longValue() : entry.key
                return filteredKeys.contains(key)
            }
        }

        // Multi-tenancy support for DISCRIMINATOR mode
        SimpleMapDatastore datastoreInstance = (SimpleMapDatastore)session.datastore
        if (datastoreInstance.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            if (entity.isMultiTenant()) {
                def currentTenantId = Tenants.currentId(datastoreInstance)
                if (currentTenantId != null) {
                    entityMap = entityMap.findAll { entry ->
                        def entryTenantId = entry.value.get("tenantId")
                        return entryTenantId == null || entryTenantId.toString() == currentTenantId.toString()
                    }
                }
            }
        }

        if (!entity.isRoot()) {
            def discriminator = entity.discriminator
            entityMap = entityMap.findAll { it.value.discriminator == discriminator }
        }

        def nullEntries = entityMap.entrySet().findAll { it.value == null }
        entityMap.keySet().removeAll(nullEntries.collect { it.key })

        if (orderBy) {
            entityMap = entityMap.sort { a, b ->
                def result = 0
                for (Order order in orderBy) {
                    def name = order.property
                    def val1 = resolveIfEmbedded(name, a.value)
                    def val2 = resolveIfEmbedded(name, b.value)
                    if (order.direction == Order.Direction.DESC) {
                        result = val2 <=> val1
                    }
                    else {
                        result = val1 <=> val2
                    }
                    if (result != 0) break
                }
                return result
            }
        } else {
            // Default stable order by ID
            entityMap = entityMap.sort { a, b ->
                def k1 = a.key instanceof Number ? a.key.longValue() : a.key
                def k2 = b.key instanceof Number ? b.key.longValue() : b.key
                return k1 <=> k2
            }
        }
        
        def resultList = []
        if (max > -1 && offset > -1) {
            def last = offset + max - 1
            def keys = entityMap.keySet().toList()
            if (offset < keys.size()) {
                keys = keys[offset..Math.min(last, keys.size() - 1)]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else if (max > -1) {
            def keys = entityMap.keySet().toList()
            def to = Math.min(max - 1, keys.size() - 1)
            if (to >= 0) {
                keys = keys[0..to]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else if (offset > -1) {
            def keys = entityMap.keySet().toList()
            if (offset < keys.size()) {
                keys = keys[offset..(keys.size() - 1)]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else {
            populateQueryResult(entityMap.keySet().toList(), resultList, entityMap)
        }

        List finalResults
        if (projections.isEmpty()) {
            finalResults = resultList.collect {
                session.retrieve(entity.javaClass, (Serializable) it.key)
            }
        }
        else {
            List<Query.Projection> projectionList = projections.projectionList
            boolean hasAggregate = projectionList.any { it instanceof Query.CountProjection || it instanceof Query.CountDistinctProjection || it instanceof Query.AvgProjection || it instanceof Query.MinProjection || it instanceof Query.MaxProjection || it instanceof Query.SumProjection }
            
            if (hasAggregate) {
                def results = []
                for (p in projectionList) {
                    if (p instanceof Query.CountProjection) {
                        results << resultList.size()
                    }
                    else if (p instanceof Query.CountDistinctProjection) {
                        def propertyValues = resultList.collect { it.value[p.propertyName] }.findAll { it != null }
                        results << propertyValues.unique().size()
                    }
                    else if (p instanceof Query.AvgProjection || p instanceof Query.MinProjection || p instanceof Query.MaxProjection || p instanceof Query.SumProjection) {
                         def propertyValues = resultList.collect { it.value[p.propertyName] }.findAll { it != null }
                         if (p instanceof Query.MinProjection) {
                             results << propertyValues.min()
                         }
                         else if (p instanceof Query.MaxProjection) {
                             results << propertyValues.max()
                         }
                         else if (p instanceof Query.SumProjection) {
                             results << propertyValues.sum()
                         }
                         else if (p instanceof Query.AvgProjection) {
                             def res = propertyValues.isEmpty() ? 0 : propertyValues.sum() / propertyValues.size()
                             if (res instanceof BigDecimal) res = res.doubleValue()
                             results << res
                         }
                    }
                    else if (p instanceof Query.IdProjection) {
                        results << (resultList.isEmpty() ? null : resultList[0].key)
                    }
                    else if (p instanceof Query.PropertyProjection) {
                        def val = resultList.isEmpty() ? null : resultList[0].value[p.propertyName]
                        if (val != null) {
                             PersistentProperty prop = entity.getPropertyByName(p.propertyName)
                             if (prop instanceof ToOne && !(prop.type.isInstance(val))) {
                                 val = session.retrieve(prop.type, (Serializable)val)
                             }
                        }
                        results << val
                    }
                }
                finalResults = [results.size() == 1 ? results[0] : results]
            }
            else {
                finalResults = resultList.collect { res ->
                    def results = []
                    for (p in projectionList) {
                        if (p instanceof Query.IdProjection) {
                            results << res.key
                        }
                        else if (p instanceof Query.PropertyProjection) {
                            def val = res.value[p.propertyName]
                            if (val != null) {
                                PersistentProperty prop = entity.getPropertyByName(p.propertyName)
                                if (prop instanceof ToOne && !(prop.type.isInstance(val))) {
                                    val = session.retrieve(prop.type, (Serializable)val)
                                }
                            }
                            results << val
                        }
                    }
                    return results.size() == 1 ? results[0] : results
                }
            }
        }
        return finalResults
    }

    List list(Map params) {
        String sortProperty = params.sort?.toString()
        String sortDirection = params.order?.toString() ?: "asc"
        
        if (sortProperty || params.order) {
            if (!sortProperty) {
                sortProperty = entity.getIdentity()?.getName() ?: "id"
            }
            if (sortDirection.equalsIgnoreCase("desc")) {
                order(Query.Order.desc(sortProperty))
            } else {
                order(Query.Order.asc(sortProperty))
            }
        }
        if (params.max) {
            max(Integer.parseInt(params.max.toString()))
        }
        if (params.offset) {
            offset(Integer.parseInt(params.offset.toString()))
        }
        
        List results = list()
        if (params.max || params.offset) {
            try {
                def pagedResultListClass = Class.forName("grails.gorm.PagedResultList")
                def pagedResultList = pagedResultListClass.getConstructor(Query.class).newInstance(this)
                return (List)pagedResultList
            } catch (Throwable e) {
                // ignore
            }
        }
        return results
    }

    long deleteAll() {
        def results = list()
        for (result in results) {
            session.delete(result)
        }
        return results.size()
    }

    private void populateQueryResult(List keys, List resultList, Map entityMap) {
        for (key in keys) {
            resultList << [key: key, value: entityMap[key]]
        }
    }

    protected Map executeSubQuery(Query.Junction criteria, List<Query.Criterion> criterionList) {
        def datastore = getDatastoreMap()
        def familyMap = (Map) datastore[getFamily()] ?: [:]
        def entityMap = familyMap

        // Multi-tenancy support for DISCRIMINATOR mode
        SimpleMapDatastore datastoreInstance = (SimpleMapDatastore)session.datastore
        if (datastoreInstance.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            if (entity.isMultiTenant()) {
                def currentTenantId = Tenants.currentId(datastoreInstance)
                if (currentTenantId != null) {
                    entityMap = entityMap.findAll { entry ->
                        def entryTenantId = entry.value.get("tenantId")
                        return entryTenantId == null || entryTenantId.toString() == currentTenantId.toString()
                    }
                }
            }
        }

        if (criteria instanceof Query.Conjunction) {
            def resultList = []
            for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    resultList << executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it } as Set
                }
                else {
                    resultList << handleCriterion(c).collect { it instanceof Number ? it.longValue() : it } as Set
                }
            }

            if (resultList.isEmpty()) {
                return entityMap
            }
            def intersectKeys = resultList[0]
            for (int i = 1; i < resultList.size(); i++) {
                intersectKeys = intersectKeys.intersect(resultList[i])
            }
            return entityMap.findAll { entry -> 
                def key = entry.key instanceof Number ? entry.key.longValue() : entry.key
                intersectKeys.contains(key) 
            }
        }
        else if (criteria instanceof Query.Disjunction) {
            def unionKeys = [] as Set
            for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    unionKeys.addAll(executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it })
                }
                else {
                    unionKeys.addAll(handleCriterion(c).collect { it instanceof Number ? it.longValue() : it })
                }
            }
            return entityMap.findAll { entry -> 
                def key = entry.key instanceof Number ? entry.key.longValue() : entry.key
                unionKeys.contains(key) 
            }
        }
        else if (criteria instanceof Query.Negation) {
            def negationKeys = [] as Set
            for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    negationKeys.addAll(executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it })
                }
                else {
                    negationKeys.addAll(handleCriterion(c).collect { it instanceof Number ? it.longValue() : it })
                }
            }
            return entityMap.findAll { entry -> 
                def key = entry.key instanceof Number ? entry.key.longValue() : entry.key
                !negationKeys.contains(key) 
            }
        }

        return entityMap
    }

    private Collection handleCriterion(Query.Criterion c) {
        def handler = handlers[c.getClass()]
        if (!handler) {
            handler = handlers.find { k, v -> k.isAssignableFrom(c.getClass()) }?.value
        }
        if (handler) {
            PersistentProperty property = null
            if (c instanceof Query.PropertyNameCriterion) {
                property = entity.getPropertyByName(((Query.PropertyNameCriterion)c).property)
            }
            else if (c instanceof org.grails.datastore.mapping.query.AssociationQuery) {
                property = ((org.grails.datastore.mapping.query.AssociationQuery)c).getAssociation()
            }
            def results = handler.call(this, c, property)
            if (results instanceof Collection) {
                return results.collect { it instanceof Number ? it.longValue() : it }
            }
            else {
                return results ? [results instanceof Number ? results.longValue() : results] : []
            }
        }
        return []
    }

    protected marshalValue(PersistentProperty property, value) {
        if (value instanceof QueryableCriteria) {
            return value
        }
        if (property != null && value != null) {
            if (property instanceof Association) {
                if (value instanceof Collection) {
                    return value.collect { 
                        if (it == null) return null
                        if (property.type.isInstance(it)) {
                            def persister = session.getPersister(it)
                            return persister != null ? persister.getObjectIdentifier(it) : it
                        }
                        return it
                    }
                } else if (property.type.isInstance(value)) {
                    def persister = session.getPersister(value)
                    return persister != null ? persister.getObjectIdentifier(value) : value
                }
            }
            if (!property.type.isInstance(value)) {
                try {
                    value = session.getMappingContext().getConversionService().convert(value, property.getType())
                } catch (Throwable e) {
                    // ignore
                }
            }
            def marshaller = property.respondsTo("getCustomTypeMarshaller") ? property.getCustomTypeMarshaller() : null
            if (marshaller != null && marshaller.supports(session.getMappingContext())) {
                try {
                    value = marshaller.write(property, value, [:])
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
        return value
    }

    protected boolean matchesCriterion(SimpleMapQuery query, Query.PropertyCriterion pc, Object val) {
        def value = pc.value
        def prop = entity.getPropertyByName(pc.property)
        
        if (value instanceof QueryableCriteria) {
            value = value.get()
        }

        // Marshal the target value to its persistent form
        val = query.marshalValue(prop, val)

        if (pc instanceof Query.In) {
            if (value instanceof Collection) {
                def convertedValues = value.collect { query.marshalValue(prop, it) }
                if (val instanceof Number) {
                    val = val.doubleValue()
                    convertedValues = convertedValues.collect { it instanceof Number ? it.doubleValue() : it }
                }
                return convertedValues.contains(val)
            }
            return false
        }

        // Marshal scalar value to its persistent form
        value = query.marshalValue(prop, value)

        if (val instanceof Number && value instanceof Number) {
            val = val.doubleValue()
            value = value.doubleValue()
        }

        if (pc instanceof Query.Equals) {
            return val == value
        }
        else if (pc instanceof Query.NotEquals) {
            return val != value
        }
        else if (pc instanceof Query.GreaterThan) {
            if (val != null && value != null) {
                return val > value
            }
        }
        else if (pc instanceof Query.GreaterThanEquals) {
            if (val != null && value != null) {
                return val >= value
            }
        }
        else if (pc instanceof Query.LessThan) {
            if (val != null && value != null) {
                return val < value
            }
        }
        else if (pc instanceof Query.LessThanEquals) {
            if (val != null && value != null) {
                return val <= value
            }
        }
        else if (pc instanceof Query.ILike) {
            if (val != null && value != null) {
                def pattern = "(?i)" + value.toString().replace('%', '.*').replace('_', '.')
                return val.toString() ==~ pattern
            }
        }
        else if (pc instanceof Query.Like) {
            if (val != null && value != null) {
                def pattern = value.toString().replaceAll('%', '.*')
                return val.toString() ==~ pattern
            }
        }
        else if (pc instanceof Query.RLike) {
            if (val != null && value != null) {
                return val.toString() ==~ value.toString()
            }
        }
        return false
    }

    protected boolean matchesSubqueryCriterion(SimpleMapQuery query, Query.SubqueryCriterion sc, Object val, List subqueryResults) {
        if (val == null) return false
        
        def prop = entity.getPropertyByName(sc.property)
        val = query.marshalValue(prop, val)
        def results = subqueryResults.collect { query.marshalValue(prop, it) }
        
        if (val instanceof Number) {
            val = val.doubleValue()
            results = results.collect { it instanceof Number ? it.doubleValue() : it }
        }

        if (sc instanceof Query.EqualsAll) {
            return results.every { val == it }
        }
        else if (sc instanceof Query.NotEqualsAll) {
            return results.every { val != it }
        }
        else if (sc instanceof Query.GreaterThanAll) {
            return results.every { val > it }
        }
        else if (sc instanceof Query.GreaterThanEqualsAll) {
            return results.every { val >= it }
        }
        else if (sc instanceof Query.LessThanAll) {
            return results.every { val < it }
        }
        else if (sc instanceof Query.LessThanEqualsAll) {
            return results.every { val <= it }
        }
        else if (sc instanceof Query.GreaterThanSome) {
            return results.any { val > it }
        }
        else if (sc instanceof Query.GreaterThanEqualsSome) {
            return results.any { val >= it }
        }
        else if (sc instanceof Query.LessThanSome) {
            return results.any { val < it }
        }
        else if (sc instanceof Query.LessThanEqualsSome) {
            return results.any { val <= it }
        }
        else if (sc instanceof Query.NotIn) {
            return !results.contains(val)
        }
        return false
    }

    private static Map handlers = [
        (Query.SubqueryCriterion): { SimpleMapQuery query, Query.SubqueryCriterion sc, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            def subqueryResults = sc.value.list()
            
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(sc.property, ov)
                if (query.matchesSubqueryCriterion(query, sc, val, subqueryResults)) results << ok
            }
            return results
        },
        (Query.IdEquals): { SimpleMapQuery query, Query.IdEquals ie, PersistentProperty property ->
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            def key = ie.value instanceof Number ? ie.value.longValue() : ie.value
            if (familyMap.containsKey(key)) return [key]
            return []
        },
        (Query.Equals): { SimpleMapQuery query, Query.Equals eq, PersistentProperty property ->
            def name = eq.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, eq, val)) results << ok
            }
            return results
        },
        (Query.NotEquals): { SimpleMapQuery query, Query.NotEquals eq, PersistentProperty property ->
            def name = eq.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, eq, val)) results << ok
            }
            return results
        },
        (Query.IsNull): { SimpleMapQuery query, Query.IsNull eq, PersistentProperty property ->
            def name = eq.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val == null) results << ok
            }
            return results
        },
        (Query.IsNotNull): { SimpleMapQuery query, Query.IsNotNull eq, PersistentProperty property ->
            def name = eq.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val != null) results << ok
            }
            return results
        },
        (Query.In): { SimpleMapQuery query, Query.In eq, PersistentProperty property ->
            def name = eq.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, eq, val)) results << ok
            }
            return results
        },
        (Query.Between): { SimpleMapQuery query, Query.Between bt, PersistentProperty property ->
            def name = bt.property
            def results = []
            def from = bt.from
            def to = bt.to
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, new Query.GreaterThanEquals(name, from), val) &&
                    query.matchesCriterion(query, new Query.LessThanEquals(name, to), val)) {
                    results << ok
                }
            }
            return results
        },
        (Query.GreaterThan): { SimpleMapQuery query, Query.GreaterThan gt, PersistentProperty property ->
            def name = gt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, gt, val)) results << ok
            }
            return results
        },
        (Query.GreaterThanEquals): { SimpleMapQuery query, Query.GreaterThanEquals gt, PersistentProperty property ->
            def name = gt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, gt, val)) results << ok
            }
            return results
        },
        (Query.LessThan): { SimpleMapQuery query, Query.LessThan lt, PersistentProperty property ->
            def name = lt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, lt, val)) results << ok
            }
            return results
        },
        (Query.LessThanEquals): { SimpleMapQuery query, Query.LessThanEquals lt, PersistentProperty property ->
            def name = lt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, lt, val)) results << ok
            }
            return results
        },
        (Query.Like): { SimpleMapQuery query, Query.Like li, PersistentProperty property ->
            def name = li.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, li, val)) results << ok
            }
            return results
        },
        (Query.ILike): { SimpleMapQuery query, Query.ILike li, PersistentProperty property ->
            def name = li.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, li, val)) results << ok
            }
            return results
        },
        (Query.RLike): { SimpleMapQuery query, Query.RLike li, PersistentProperty property ->
            def name = li.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, li, val)) results << ok
            }
            return results
        },
        (Query.EqualsProperty): { SimpleMapQuery query, Query.EqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 == val2) results << ok
            }
            return results
        },
        (Query.NotEqualsProperty): { SimpleMapQuery query, Query.NotEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 != val2) results << ok
            }
            return results
        },
        (Query.GreaterThanProperty): { SimpleMapQuery query, Query.GreaterThanProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 > val2) results << ok
            }
            return results
        },
        (Query.GreaterThanEqualsProperty): { SimpleMapQuery query, Query.GreaterThanEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 >= val2) results << ok
            }
            return results
        },
        (Query.LessThanProperty): { SimpleMapQuery query, Query.LessThanProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 < val2) results << ok
            }
            return results
        },
        (Query.LessThanEqualsProperty): { SimpleMapQuery query, Query.LessThanEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 <= val2) results << ok
            }
            return results
        },
        (org.grails.datastore.mapping.query.AssociationQuery): { SimpleMapQuery query, org.grails.datastore.mapping.query.AssociationQuery aq, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            def subQuery = query.session.createQuery(aq.entity.javaClass)
            subQuery.criteria = aq.getCriteria()
            
            def subResults = subQuery.list()
            def matchingAssociatedKeys = subResults.collect { 
                def id = query.session.getPersister(it).getObjectIdentifier(it)
                return id instanceof Number ? id.longValue() : id
            } as Set
            
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(aq.getAssociation().name, ov)
                if (val instanceof Collection) {
                    def valList = val.collect { it instanceof Number ? it.longValue() : it }
                    if (valList.any { matchingAssociatedKeys.contains(it) }) results << ok
                }
                else if (val != null) {
                    def v = val instanceof Number ? val.longValue() : val
                    if (matchingAssociatedKeys.contains(v)) {
                        results << ok
                    }
                }
            }
            return results
        },
        (org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion): { SimpleMapQuery query, org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion fcc, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            def functionName = fcc.getFunctionName()
            def propertyCriterion = fcc.getPropertyCriterion()
            
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(fcc.property, ov)
                if (val != null) {
                    def functionResult = query.applyFunction(functionName, val)
                    if (query.matchesCriterion(query, propertyCriterion, functionResult)) {
                        results << ok
                    }
                }
            }
            return results
        },
        (Query.Exists): { SimpleMapQuery query, Query.Exists exists, PersistentProperty property ->
            def subQuery = query.session.createQuery(exists.getSubquery().getPersistentEntity().javaClass)
            subQuery.criteria = exists.getSubquery().getCriteria()
            def subResults = subQuery.list()
            if (!subResults.isEmpty()) {
                return query.getDatastoreMap()[query.getFamily()]?.keySet() ?: []
            }
            return []
        },
        (Query.NotExists): { SimpleMapQuery query, Query.NotExists exists, PersistentProperty property ->
            def subQuery = query.session.createQuery(exists.getSubquery().getPersistentEntity().javaClass)
            subQuery.criteria = exists.getSubquery().getCriteria()
            def subResults = subQuery.list()
            if (subResults.isEmpty()) {
                return query.getDatastoreMap()[query.getFamily()]?.keySet() ?: []
            }
            return []
        },
        (Query.SizeEquals): { SimpleMapQuery query, Query.SizeEquals se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() == se.value) results << ok
                }
            }
            return results
        },
        (Query.SizeNotEquals): { SimpleMapQuery query, Query.SizeNotEquals se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() != se.value) results << ok
                }
            }
            return results
        },
        (Query.SizeGreaterThan): { SimpleMapQuery query, Query.SizeGreaterThan se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() > se.value) results << ok
                }
            }
            return results
        },
        (Query.SizeGreaterThanEquals): { SimpleMapQuery query, Query.SizeGreaterThanEquals se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() >= se.value) results << ok
                }
            }
            return results
        },
        (Query.SizeLessThan): { SimpleMapQuery query, Query.SizeLessThan se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() < se.value) results << ok
                }
            }
            return results
        },
        (Query.SizeLessThanEquals): { SimpleMapQuery query, Query.SizeLessThanEquals se, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(se.property, ov)
                if (val instanceof Collection) {
                    if (val.size() <= se.value) results << ok
                }
            }
            return results
        }
    ]

    protected resolveIfEmbedded(String name, Map entry) {
        if (name.contains('.')) {
            def parts = name.split('\\.')
            def current = entry
            def currentEntity = entity
            for (part in parts) {
                def prop = currentEntity.getPropertyByName(part)
                if (current instanceof Map) {
                    current = current[part]
                }
                else {
                    return null
                }
                
                if (prop instanceof ToOne) {
                    currentEntity = ((ToOne)prop).getAssociatedEntity()
                    if (current != null && !(current instanceof Map)) {
                        // Resolve the entry for the association
                        def family = currentEntity.rootEntity.name
                        def datastore = getDatastoreMap()
                        def familyMap = (Map) datastore[family]
                        if (familyMap != null) {
                            current = familyMap[current]
                        } else {
                            current = null
                        }
                    }
                }
            }
            return current
        }
        return entry[name]
    }

    protected Object applyFunction(String functionName, Object value) {
        switch (functionName) {
            case "year":
                if (value instanceof Date) {
                    Calendar cal = Calendar.getInstance()
                    cal.time = (Date)value
                    return cal.get(Calendar.YEAR)
                }
                break
            case "month":
                if (value instanceof Date) {
                    Calendar cal = Calendar.getInstance()
                    cal.time = (Date)value
                    return cal.get(Calendar.MONTH) + 1
                }
                break
            case "day":
                if (value instanceof Date) {
                    Calendar cal = Calendar.getInstance()
                    cal.time = (Date)value
                    return cal.get(Calendar.DAY_OF_MONTH)
                }
                break
        }
        return value
    }

    public String getFamily() {
        return entity.rootEntity.name
    }
}
