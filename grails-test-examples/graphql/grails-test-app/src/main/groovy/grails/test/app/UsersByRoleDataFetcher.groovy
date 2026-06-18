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

package grails.test.app

import grails.compiler.GrailsCompileStatic
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.fetcher.impl.EntityDataFetcher

@CompileStatic
class UsersByRoleDataFetcher extends EntityDataFetcher<List<User>> {

    /**
     * Here we pass the {@link org.grails.datastore.mapping.model.PersistentEntity}
     * of the domain being QUERIED
     *
     * If the query was being done on UserRole and the users were being returned
     * through a projection, the alternate constructor (UserRole.gormPersistentEntity, 'user')
     * should be used instead.
     */
    UsersByRoleDataFetcher() {
        super(User.gormPersistentEntity)
    }

    /**
     * No need to add transactional here since the parent class has it defined
     * in a method that encompasses this one
     */
    @GrailsCompileStatic
    @Override
    protected List executeQuery(DataFetchingEnvironment environment, Map queryArgs) {
        Role role = Role.load((Serializable) environment.getArgument('role'))
        def users = UserRole.where { role == role }.property('user')
        User.where {
            id in users
        }.list(queryArgs)
    }
}
