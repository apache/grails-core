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

import java.util.Collection;
import java.util.List;

import grails.mongodb.geo.Circle;
import org.grails.datastore.gorm.finders.MethodExpression;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.springframework.util.Assert;

/**
 * A dynamic finder method expression that adds the ability to query within a circle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class WithinCircle extends MethodExpression {

    public WithinCircle(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Criterion createCriterion() {
        Object argument = arguments[0];
        if(argument instanceof Circle) {
            return new MongoQuery.WithinCircle(propertyName, ((Circle)argument).asList());
        }
        else {
            return new MongoQuery.WithinCircle(propertyName, (List<?>) argument);
        }
    }

    @Override
    public void setArguments(Object[] arguments) {
        Assert.isTrue(arguments.length > 0,
                "Only a list of elements is supported in a 'WithinCircle' query");

        Object arg = arguments[0];

        boolean isList = arg instanceof List;
        Assert.isTrue((isList || (arg instanceof Circle)),
                "Only a list of elements is supported in a 'withinBox' query");

        if(isList) {
            Collection<?> argument = (Collection<?>) arguments[0];
            Assert.isTrue(argument.size() == 2,
                    "A 'WithinCircle' query requires a two dimensional list of values");
        }

        super.setArguments(arguments);
    }
}
