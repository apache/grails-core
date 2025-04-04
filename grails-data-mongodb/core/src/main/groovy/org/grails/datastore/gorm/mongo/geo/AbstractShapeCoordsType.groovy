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

import grails.mongodb.geo.Box
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
 * Abstract implementation for custom types that persist shapes using their coordinate values
 */
@CompileStatic
abstract class AbstractShapeCoordsType<T extends Shape> extends AbstractMappingAwareCustomTypeMarshaller<T, Document, Document>{
    AbstractShapeCoordsType(Class<T> targetType) {
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
        if(value) {
            def coords = value.asList()
            nativeTarget.put(key, coords)
            return coords
        }
    }


    @Override
    protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion criterion, Document nativeQuery) {
        if(criterion instanceof Query.Equals) {
            def value = criterion.value
            if(value instanceof Box) {
                nativeQuery.put(key, value.asList())
            }
        }
        else {
            super.queryInternal(property, key, criterion, nativeQuery)
        }
    }
}
