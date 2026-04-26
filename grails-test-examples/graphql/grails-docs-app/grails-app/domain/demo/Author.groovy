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

package demo

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class Author {

    String name

    // tag::locationDefinition[]
    Map homeLocation
    // end::locationDefinition[]

    // tag::associationDefinition[]
    //The key is the ISBN
    Map<String, Book> books

    static hasMany = [books: Book]
    // end::associationDefinition[]

    static constraints = {
    }

    static graphql = GraphQLMapping.build {

        // tag::customBooks[]
        exclude 'books' //<1>

        add('books', 'BookMap') { //<2>
            type { //<3>
                field('key', String)
                field('value', Book)
                collection true
            }
            dataFetcher { Author author ->
                //author.books.entrySet() does not work here because
                //the graphql-java implementation calls .get() on maps
                author.books.collect { key, value -> //<4>
                    [key: key, value: value]
                }.sort(true, {a, b -> a.value.id <=> b.value.id})
            }
        }
        // end::customBooks[]

        // tag::customLocation[]
        exclude 'homeLocation' //<1>

        add('homeLocation', 'Location') { //<2>
            type { //<3>
                field('lat', String)
                field('long', String)
            }
        }
        // end::customLocation[]
    }
}
