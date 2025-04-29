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
package org.grails.datastore.gorm.finders

import spock.lang.Specification

/**
 * Created by graemerocher on 06/02/2017.
 */
class DynamicFinderSpec extends Specification {

    void "test build match spec"() {
        given:
        MatchSpec spec = DynamicFinder.buildMatchSpec(prefix, methodName, parameters)

        expect:
        spec.methodName == methodName
        spec.methodCallExpressions.size() == expressions
        spec.requiredArguments == parameters
        spec.prefix == prefix
        spec.queryExpression == queryExpression

        where:
        prefix   | methodName              | parameters | expressions | queryExpression    |   propertyNames
        "findBy" | "findByTitle"           | 1          |    1        | "Title"            |  ['title']
        "findBy" | "findByTitleBetween"    | 2          |    1        | "TitleBetween"     |  ['title']
        "findBy" | "findByTitleAndAuthor"  | 2          |    2        | "TitleAndAuthor"   |  ['title', 'author']
    }
}
