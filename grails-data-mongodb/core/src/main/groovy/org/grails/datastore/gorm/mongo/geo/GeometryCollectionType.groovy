/* Copyright (C) 2014 SpringSource
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
package org.grails.datastore.gorm.mongo.geo

import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import grails.mongodb.geo.GeometryCollection
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext

/**
 * Custom type implementation for persisting GeometryCollection instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GeometryCollectionType extends AbstractMappingAwareCustomTypeMarshaller<GeometryCollection, Document, Document>{

    public static final String GEOMETRIES = "geometries"

    GeometryCollectionType() {
        super(GeometryCollection)
    }

    @Override
    boolean supports(MappingContext context) {
        return context instanceof MongoMappingContext;
    }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, GeometryCollection value, Document nativeTarget) {
        if(value != null) {
            def col = new Document()
            col.put(GeoJSONType.GEO_TYPE, GeometryCollection.simpleName)
            col.put(GEOMETRIES, value.asList())
            nativeTarget.put(key, col)
        }
        return null
    }

    @Override
    protected GeometryCollection readInternal(PersistentProperty property, String key, Document nativeSource) {
        if(nativeSource != null) {
            def col = nativeSource.get(key)
            if(col instanceof Document) {
                def geometries = col.get(GEOMETRIES)
                if(geometries instanceof List) {
                    def geoCol = GeometryCollection.valueOf((List) geometries)
                    return geoCol
                }
            }
        }
        return null
    }
}
