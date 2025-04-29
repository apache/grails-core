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

package org.grails.gorm.graphql.domain.general.custom.property

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class SimpleType {

    String name

    String getNoFetcher() {
        'calls the getter'
    }

    static graphql = GraphQLMapping.build {
        add('noFetcher', String)

        add('customFetcher', String) {
            dataFetcher { SimpleType c ->
                'inside custom fetcher'
            }
        }
        add('notNull', String) {
            nullable false
        }
        add('onlyInput', String) {
            output false
        }
        add('onlyOutput', String) {
            input false
        }
        add('deprecatedNoReason', String) {
            deprecated true
        }
        add('deprecatedWithReason', String) {
            deprecationReason 'This is a reason'
        }
        add('described', String) {
            description 'This is a description'
        }

        add('list', [String])

        add('withArgument', String) {
            argument('arg', String)
        }
        add('withArgumentList', String) {
            argument('arg', [String])
        }
        add('withCustomArgument', String) {
            argument('arg', 'SimpleArg') {
                accepts {
                    field('field', String)
                }
            }
        }
    }

    def methodMissing(String name, Object[] args) {
        if (graphql.additional.find { it.name == name }) {
            return name
        } else {
            throw new MissingMethodException(name, SimpleType, args)
        }
    }
}
