/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.mongo;

import org.grails.datastore.gorm.finders.MethodExpression;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;

/**
 * Dynamic finder expression for within polygon queries
 *
 * @author Sergei Shushkevich
 */
public class WithinPolygon extends MethodExpression {

    public WithinPolygon(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Criterion createCriterion() {
        return new MongoQuery.WithinPolygon(propertyName, (List<?>) arguments[0]);
    }

    @Override
    public void setArguments(Object[] arguments) {
        Assert.isTrue(arguments.length > 0 && arguments[0] instanceof List,
                "Only a list of elements is supported in a 'withinPolygon' query");

        Collection<?> argument = (Collection<?>) arguments[0];
        Assert.isTrue(argument.size() == 2,
                "A 'withinPolygon' query requires a two dimensional list of values");

        super.setArguments(arguments);
    }
}
