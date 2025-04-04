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
package org.grails.datastore.gorm.mongo;

import grails.mongodb.geo.Distance;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query;

/**
 * nearSphere query support
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class NearSphere extends Near{
    public NearSphere(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Query.Criterion createCriterion() {
        MongoQuery.NearSphere near = new MongoQuery.NearSphere(propertyName, arguments[0]);

        if(arguments.length > 1) {
            Object o = arguments[1];
            if(o instanceof Number) {
                near.setMaxDistance(Distance.valueOf(((Number) o).doubleValue()));
            }
            else {
                near.setMaxDistance((Distance)o);
            }
        }
        return near;
    }
}
