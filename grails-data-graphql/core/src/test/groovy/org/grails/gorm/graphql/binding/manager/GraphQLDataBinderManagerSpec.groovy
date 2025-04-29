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

package org.grails.gorm.graphql.binding.manager

import org.grails.gorm.graphql.binding.GraphQLDataBinder
import spock.lang.Specification

class GraphQLDataBinderManagerSpec extends Specification {

    DefaultGraphQLDataBinderManager manager

    void setup() {
        manager = new DefaultGraphQLDataBinderManager()
    }

    GraphQLDataBinder newBinder() {
        new GraphQLDataBinder() {
            @Override
            void bind(Object object, Map data) { }
        }
    }

    void "test Object binder exists"() {
        expect:
        manager.getDataBinder(String) != null
        manager.getDataBinder(Object) != null
    }

    void "test binders add order takes precedence"() {
        given:
        GraphQLDataBinder binder = newBinder()
        manager.registerDataBinder(String, binder)

        expect:
        manager.getDataBinder(Object) != binder
        manager.getDataBinder(String) == binder
    }

    void "test binder for exact class takes precedence over parent classes"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder string = newBinder()
        manager.registerDataBinder(String, string)
        manager.registerDataBinder(Serializable, serializable)

        expect:
        manager.getDataBinder(String) == string
        manager.getDataBinder(Number) == serializable
    }

    void "test binder for exact class takes precedence over parent classes reverse order"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder string = newBinder()
        manager.registerDataBinder(Serializable, serializable)
        manager.registerDataBinder(String, string)

        expect:
        manager.getDataBinder(String) == string
        manager.getDataBinder(Number) == serializable
    }

    void "test the first parent class is chosen"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder comparable = newBinder()
        manager.registerDataBinder(Serializable, serializable)
        manager.registerDataBinder(Comparable, comparable)

        expect:
        manager.getDataBinder(Long) == comparable
    }

    void "test the first parent class is chosen reverse order"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder comparable = newBinder()
        manager.registerDataBinder(Comparable, comparable)
        manager.registerDataBinder(Serializable, serializable)

        expect:
        manager.getDataBinder(Long) == serializable
    }
}
