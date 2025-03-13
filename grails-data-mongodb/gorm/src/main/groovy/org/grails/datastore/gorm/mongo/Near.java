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
package org.grails.datastore.gorm.mongo;

import java.util.List;
import java.util.Map;

import grails.mongodb.geo.Distance;
import grails.mongodb.geo.Point;
import org.grails.datastore.gorm.finders.MethodExpression;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.springframework.util.Assert;

public class Near extends MethodExpression {

    public Near(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Criterion createCriterion() {
        MongoQuery.Near near = new MongoQuery.Near(propertyName, arguments[0]);

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

    @Override
    public void setArguments(Object[] arguments) {
        Assert.isTrue(arguments.length > 0 ,
            "Missing required arguments to findBy*Near query");

        Object arg1 = arguments[0];

        Assert.isTrue(((arg1 instanceof Point) || (arg1 instanceof Map) || (arg1 instanceof List)) ,
                "Argument to findBy*Near should either be a Point, coordinate List or a Map");

        if(arguments.length>1) {
            Object arg2 = arguments[1];
            Assert.isTrue(((arg2 instanceof Number) || (arg2 instanceof Distance)),
                    "Second argument to findBy*Near should either the distance: either a number or an instanceof Distance");
        }

        super.setArguments(arguments);
    }
}
