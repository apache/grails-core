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

package org.grails.datastore.gorm.neo4j.services.implementers

import grails.neo4j.services.Cypher
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.neo4j.services.transform.CypherQueryStringTransformer
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.services.implementers.FindOneStringQueryImplementer
import org.grails.datastore.gorm.services.transform.QueryStringTransformer

import java.lang.annotation.Annotation

/**
 * A cypher query implementer
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneCypherQueryImplementer extends FindOneStringQueryImplementer  implements AnnotatedServiceImplementer<Cypher> {


    @Override
    protected Class<? extends Annotation> getAnnotationType() {
        Cypher
    }

    @Override
    protected QueryStringTransformer createQueryStringTransformer(SourceUnit sourceUnit, VariableScope scope) {
        return new CypherQueryStringTransformer(sourceUnit, scope)
    }
}
