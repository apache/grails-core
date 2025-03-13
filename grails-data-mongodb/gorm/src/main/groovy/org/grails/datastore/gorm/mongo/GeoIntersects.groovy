/* Copyright (C) 2013 SpringSource
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
package org.grails.datastore.gorm.mongo

import grails.mongodb.geo.GeoJSON
import org.grails.datastore.gorm.finders.MethodExpression
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.grails.datastore.mapping.query.Query
import org.springframework.util.Assert

/**
 * Dynamic finder extension for GeoIntersects style queries
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class GeoIntersects extends MethodExpression{
    GeoIntersects(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName)
    }

    @Override
    Query.Criterion createCriterion() {
        return new MongoQuery.GeoIntersects(propertyName, arguments[0]);
    }

    @Override
    void setArguments(Object[] arguments) {
        Assert.isTrue arguments.length == 1, "Exactly 1 argument required to GeoWithin query"


        def value = arguments[0]

        Assert.isTrue( (value instanceof Map) || (( value instanceof GeoJSON) ),
                "Argument must be either a Map or a GeoJSON shape (Polygon, LineString or Point)")

        super.setArguments(arguments)
    }

}
