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
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.types.GraphQLPropertyType

@Entity
class CustomType  {

    String name

    Map getNoFetcher() {
        [value: 'calls the getter', result: true, domain: new OtherDomain(name: 'from getter')]
    }

    static graphql = GraphQLMapping.build {

        add('noFetcher', 'NoFetcher') {
            type {
                field('value', String) {
                    nullable false
                    description 'fadf'
                }
                field('result', Boolean)
                field('domain', OtherDomain)
                collection true
            }
        }
        add('customFetcher', 'CustomFetcher') {
            type {
                field('comment', 'CustomFetcherComment') {
                    field('replies', 'CustomFetcherReply') {
                        field('text', String)
                        collection true
                        description 'The replies to the comment'
                    }
                    description 'A comment'
                }
                field('commentCount', Long)
            }
            dataFetcher { SimpleType c ->
                [comment: [replies: [[text: "reply 1"], [text: "reply 2"]]], commentCount: 1]
            }
        }
        add('notNull', 'NotNull') {
            type {
                field('null', Boolean)
            }
            nullable false
        }
        add('onlyInput', 'OnlyInput') {
            type {
                field('output', Boolean)
            }
            output false
        }
        add('onlyOutput', 'OnlyOutput') {
            type {
                field('input', Boolean)
            }
            input false
        }
        add('deprecatedNoReason', 'DepNoReason') {
            type {
                field('deprecated', Boolean)
            }
            deprecated true
        }
        add('deprecatedWithReason', 'DepReason') {
            type {
                field('deprecated', Boolean)
            }
            deprecationReason 'This is a reason'
        }
        add('described', 'Described') {
            type {
                field('description', String)
            }
            description 'This is a description'
        }
        add('withArgument', 'WithArg') {
            argument('arg', String)
            type {
                field('response', String)
            }
        }
        add('withArgumentList', 'WithArgList') {
            argument('arg', [String])
            type {
                field('response', String)
            }
        }
        add('withCustomArgument', 'WithCustomArg') {
            argument('arg', 'CustomArg') {
                accepts {
                    field('field', String)
                }
            }
            type {
                field('response', String)
            }
        }
    }

}