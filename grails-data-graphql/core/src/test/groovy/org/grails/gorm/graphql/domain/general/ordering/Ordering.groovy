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

package org.grails.gorm.graphql.domain.general.ordering

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class Ordering {

    String order0
    String orderNeg
    String order2
    String order1a
    
    // Checks default property order 
    String orderNullc
    String orderNulld
    String order8
    
    static constraints = {
        order2 order: 4
        order1a order: 1
        order0 order: 0
        orderNeg order: -21
    }
    
    static graphql = GraphQLMapping.build {
        add("order1",String.class,{
            order(1) // same order as 'a'
        })
        property("order8",[order:8])
        property("order2",[order:2])
    }
}
