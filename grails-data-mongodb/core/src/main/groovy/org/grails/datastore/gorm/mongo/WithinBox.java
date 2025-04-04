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

import grails.mongodb.geo.Box;
import org.grails.datastore.gorm.finders.MethodExpression;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.springframework.util.Assert;

/**
 * Dynamic finder expression for within box queries
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class WithinBox extends MethodExpression {

    public WithinBox(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Criterion createCriterion() {
        Object argument = arguments[0];
        if(argument instanceof Box) {
            return new MongoQuery.WithinBox(propertyName, ((Box)argument).asList());
        }
        else {
            return new MongoQuery.WithinBox(propertyName, (List<?>) argument);
        }
    }

    @Override
    public void setArguments(Object[] arguments) {
        Assert.isTrue(arguments.length > 0,
            "Only a list of elements is supported in a 'withinBox' query");

        Object arg = arguments[0];

        boolean isList = arg instanceof List;
        Assert.isTrue((isList || (arg instanceof Box)),
                "Only a list of elements is supported in a 'withinBox' query");

        if(isList) {
            Collection<?> argument = (Collection<?>) arg;
            Assert.isTrue(argument.size() == 2,
                    "A 'withinBox' query requires a two dimensional list of values");

        }
        super.setArguments(arguments);
    }
}
