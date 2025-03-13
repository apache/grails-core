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

import com.mongodb.DBObject
import grails.mongodb.geo.Box
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * A custom type for persisting {@link Box} instances
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class BoxType extends AbstractShapeCoordsType<Box>{
    BoxType() {
        super(Box)
    }

    @Override
    protected Box readInternal(PersistentProperty property, String key, Document nativeSource) {
        def coords = nativeSource.get(key)
        if(coords instanceof List) {
            return Box.valueOf(coords)
        }
    }
}
