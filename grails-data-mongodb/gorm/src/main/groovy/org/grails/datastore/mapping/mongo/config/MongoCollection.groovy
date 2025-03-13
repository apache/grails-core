/* Copyright (C) 2010-2025 the original author or authors.
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
package org.grails.datastore.mapping.mongo.config

import com.mongodb.WriteConcern
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.document.config.Attribute
import org.grails.datastore.mapping.document.config.Collection
import org.grails.datastore.mapping.query.Query

/**
 * Provides configuration options for mapping Mongo DBCollection instances
 *
 * @author Graeme Rocher
 */
@SuppressWarnings("rawtypes")
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class MongoCollection extends Collection {

    /**
     * The database to use
     *
     * @return The name of the database
     */
    String database
    /**
     * @return The {@link WriteConcern} for the collection
     */
    WriteConcern writeConcern

    private List<Map> compoundIndices = new ArrayList<Map>()
    private List<Index> indices = new ArrayList<Index>()

    @Override
    protected MongoAttribute newProperty() {
        return new MongoAttribute()
    }


    Query.Order getSort() {
        return (Query.Order)super.getSort()
    }

    /**
     * Sets the default sorting
     * @param s The sort object
     * @return This collection
     */
    MongoCollection setSort(Object s) {
        if (s instanceof Query.Order) {
            super.setSort( (Query.Order) s )
        }
        if (s instanceof Map) {
            Map m = (Map) s
            if (!m.isEmpty()) {
                Map.Entry entry = (Map.Entry) m.entrySet().iterator().next()
                Object key = entry.getKey()
                if ("desc".equalsIgnoreCase(entry.getValue().toString())) {
                    super.setSort( Query.Order.desc(key.toString()) )
                }
                else {
                    super.setSort( Query.Order.asc(key.toString()) )
                }
            }
        }
        else {
            super.setSort( Query.Order.asc(s.toString()) )
        }
        return this
    }

    /**
     * Sets the default sorting
     * @param s The sort object
     * @return This collection
     */
    MongoCollection sort(Query.Order sort) {
        setSort(sort)
    }

    /**
     * Sets the default sorting
     * @param s The sort object
     * @return This collection
     */
    MongoCollection sort(Map sort) {
        setSort(sort)
    }
    /**
     * Defines an index
     *
     * @param definition The index definition
     */
    void index(Map<String, Object> definition) {
        index(definition, Collections.<String,Object>emptyMap())
    }

    /**
     * Defines an index
     *
     * @param definition The index definition
     * @param options The index options
     */
    void index(Map<String, Object> definition, Map<String, Object> options) {
        if(definition != null && !definition.isEmpty()) {
            indices.add(new Index(definition, options))
        }
    }

    List<Index> getIndices() {
        return indices
    }

    /**
     * Sets a compound index definition
     *
     * @param compoundIndex The compount index
     */
    void setCompoundIndex(Map compoundIndex) {
        if (compoundIndex != null) {
            compoundIndices.add(compoundIndex)
        }
    }

    /**
     * Sets a compound index definition
     *
     * @param compoundIndex The compount index
     */
    MongoCollection compoundIndex(Map compoundIndex) {
        setCompoundIndex(compoundIndex)
        return this
    }

    /**
     * Return all defined compound indices
     *
     * @return The compound indices to return
     */
    List<Map> getCompoundIndices() {
        return compoundIndices
    }

    @Override
    Entity property(String name, @DelegatesTo(MongoAttribute.class) Closure propertyConfig) {
        return super.property(name, propertyConfig)
    }

    @Override
    Entity<Attribute> id(@DelegatesTo(MongoAttribute.class) Closure identityConfig) {
        return super.id(identityConfig)
    }

    @Override
    Entity version(@DelegatesTo(MongoAttribute.class) Closure versionConfig) {
        return super.version(versionConfig)
    }

    @Override
    MongoAttribute property(@DelegatesTo(MongoAttribute.class) Closure propertyConfig) {
        return (MongoAttribute)super.property(propertyConfig)
    }

    @Override
    MongoAttribute property(Map propertyConfig) {
        return (MongoAttribute)super.property(propertyConfig)
    }
    /**
     * Definition of an index
     */
    static class Index {
        Map<String, Object> definition = new HashMap<String, Object>()
        Map<String, Object> options = new HashMap<String, Object>()


        Index(Map<String, Object> definition) {
            this.definition = definition
        }

        Index(Map<String, Object> definition, Map<String, Object> options) {
            this.definition = definition
            this.options = options
        }

        Map<String, Object> getDefinition() {
            return definition
        }

        Map<String, Object> getOptions() {
            return options
        }
    }
}
