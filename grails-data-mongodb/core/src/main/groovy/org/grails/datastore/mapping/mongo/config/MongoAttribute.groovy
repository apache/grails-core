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
package org.grails.datastore.mapping.mongo.config

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.document.config.Attribute

/**
 * Extends {@link Attribute} class with additional Mongo specific configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class MongoAttribute extends Attribute {

    public static final String INDEX_TYPE = "type"
    public static final String INDEX_TYPE_2D = "2d"
    public static final String INDEX_TYPE_2DSPHERE = "2dsphere"

    @SuppressWarnings("rawtypes")
    private Map indexAttributes

    /**
     * Whether this attribute is a database reference
     */
    boolean reference = false

    /**
     * @return The attributes for the index
     */
    Map getIndexAttributes() {
        return indexAttributes
    }

    /**
     * Sets index attributes
     *
     * @param indexAttributes The index attributes
     */
    void setIndexAttributes(Map indexAttributes) {
        if (this.indexAttributes == null) {
            this.indexAttributes = indexAttributes
        }
        else {
            this.indexAttributes.putAll(indexAttributes)
        }
    }


    /**
     * Configures the index attributes
     *
     * @param indexAttributes The index attributes
     * @return This attribute
     */
    MongoAttribute indexAttributes(Map indexAttributes) {
        setIndexAttributes(indexAttributes)
        return this
    }

    /**
     * Sets the field name to map to
     * @param name The field name
     */
    void setField(String name) {
        setTargetName(name)
    }

    /**
     * @return The field name to map to
     */
    String getField() {
        return getTargetName()
    }

    /**
     * Configures the field name to map to
     *
     * @param name The name of the field
     * @return This attribute
     */
    MongoAttribute field(String name) {
        setTargetName(name)
        return this
    }

    /**
     * Sets the geo index type
     * @param indexType The geo index type
     */
    void setGeoIndex(String indexType) {
        if(Boolean.valueOf(indexType)) {
            setIndex(true)
            initIndexAttributes()
            indexAttributes.put(INDEX_TYPE, INDEX_TYPE_2D)
        }
        else if (INDEX_TYPE_2D.equals(indexType) || INDEX_TYPE_2DSPHERE.equals(indexType)) {
            setIndex(true)
            initIndexAttributes()
            indexAttributes.put(INDEX_TYPE, indexType)
        }
    }

    /**
     * Sets the geo index type
     * @param indexType The geo index type
     */
    MongoAttribute geoIndex(String indexType) {
        setGeoIndex(indexType)
        return this
    }
    /**
     * Sets the index type
     *
     * @param type The index type
     */
    void setIndex(String type) {
        setIndex(true)
        initIndexAttributes()
        indexAttributes.put(INDEX_TYPE, type)
    }

    /**
     * Configures the index type
     *
     * @param type The index type
     * @return This attribute
     */
    MongoAttribute index(String type) {
        setIndex(type)
        return this
    }

    @SuppressWarnings("rawtypes")
    void initIndexAttributes() {
        if (indexAttributes == null) {
            indexAttributes = new HashMap()
        }
    }
}
