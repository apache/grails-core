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

import grails.mongodb.geo.GeoJSON
import grails.mongodb.geo.Shape
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.query.Query

/**
 * Abstract class for persisting {@link Shape} instances in GeoJSON format. See http://geojson.org/geojson-spec.html
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
abstract class GeoJSONType<T extends Shape> extends AbstractMappingAwareCustomTypeMarshaller<T, Document, Document> {

    public static final String COORDINATES = "coordinates"
    public static final String GEO_TYPE = "type"

    GeoJSONType(Class<T> targetType) {
        super(targetType)
    }

    @Override
    boolean supports(MappingContext context) {
        return context instanceof MongoMappingContext;
    }

    @Override
    boolean supports(Datastore datastore) {
        return datastore instanceof MongoDatastore;
    }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, T value, Document nativeTarget) {
        if(value != null) {
            Document pointData = convertToGeoDocument((Shape)value)
            nativeTarget.put(key, pointData)
            return pointData
        }
    }

    static Document convertToGeoDocument(Shape value) {
        def geoJson = new Document()
        geoJson.put(GEO_TYPE, value.getClass().simpleName)
        geoJson.put(COORDINATES, value.asList())
        return geoJson
    }

    @Override
    protected T readInternal(PersistentProperty property, String key, Document nativeSource) {
        def obj = nativeSource.get(key)
        if(obj instanceof Document) {
            Document pointData = (Document)obj
            def coords = pointData.get(COORDINATES)

            if(coords instanceof List) {
                return createFromCoords(coords)
            }
        }
        return null
    }

    abstract T createFromCoords(List<List<Double>> coords)

    @Override
    protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion value, Document nativeQuery) {
        if(value instanceof Query.Equals) {
            def v = value.getValue()
            if(v instanceof GeoJSON) {
                Shape shape = (Shape) v

                def geoJson = convertToGeoDocument(shape)
                nativeQuery.put(key, geoJson)
            }
            else if( v instanceof Shape) {
                nativeQuery.put(key, v.asList())
            }
            else {
                super.queryInternal(property, key, value, nativeQuery)
            }
        }
        else {
            super.queryInternal(property, key, value, nativeQuery)
        }
    }
}
