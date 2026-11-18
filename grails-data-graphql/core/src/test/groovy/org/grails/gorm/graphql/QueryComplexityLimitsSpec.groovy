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
package org.grails.gorm.graphql

import graphql.ExecutionInput
import graphql.ParseAndValidate
import graphql.schema.GraphQLSchema
import graphql.validation.QueryComplexityLimits
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.gorm.graphql.domain.general.GeneralPackage
import org.grails.gorm.graphql.domain.hibernate.HibernatePackage
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * graphql-java 26 enforces query complexity limits by default; the upgrade guide tells
 * applications to adjust them with {@link QueryComplexityLimits#setDefaultLimits}.
 */
class QueryComplexityLimitsSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared GraphQLSchema schema

    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop')),
                GeneralPackage.getPackage(), HibernatePackage.getPackage())
        schema = new Schema(hibernateDatastore.mappingContext).generate()
    }

    void cleanup() {
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.DEFAULT)
    }

    void 'a query nested 100 levels deep passes the default limits'() {
        expect:
        errors(nestedQuery(100)).empty
    }

    void 'a query nested more than 100 levels deep fails the default limits'() {
        expect:
        errors(nestedQuery(101)) == [ValidationErrorType.MaxQueryDepthExceeded.name()]
    }

    void 'raised default limits let a deeper query pass'() {
        given:
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.newLimits()
                .maxDepth(150)
                .maxFieldsCount(200_000)
                .build())

        expect:
        errors(nestedQuery(150)).empty
        errors(nestedQuery(151)) == [ValidationErrorType.MaxQueryDepthExceeded.name()]
    }

    void 'the limits can be turned off'() {
        given:
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)

        expect:
        errors(nestedQuery(160)).empty
    }

    /**
     * DebugCircular refers to itself through otherCircular, so a query can nest as deep as
     * needed: debugBarList and circular are the first two levels, every otherCircular adds one
     * and id is the last.
     */
    private static String nestedQuery(int depth) {
        int nested = depth - 3
        '{ debugBarList { circular { ' + 'otherCircular { ' * nested + 'id' + ' }' * nested + ' } } }'
    }

    /**
     * The validation error types, or the class of any other error, so a query the parser
     * rejects (graphql-java stops parsing past about 165 levels) cannot pass as valid.
     */
    private List<String> errors(String query) {
        ParseAndValidate.parseAndValidate(schema, ExecutionInput.newExecutionInput(query).build()).errors.collect {
            it instanceof ValidationError ? ((ValidationError) it).validationErrorType.name() : it.getClass().simpleName
        }
    }
}
