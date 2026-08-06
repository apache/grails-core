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
package org.grails.datastore.gorm.query.transform;

import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.TransformWithPriority;

import org.apache.grails.common.compiler.GroovyTransformOrder;

/**
 * Global version of {@link GormQuerySafetyTransformer} - runs automatically against every class
 * in every Grails application that has {@code grails-datamapping-core} on its compile classpath,
 * with no developer configuration required.
 *
 * <p>Since this check is intraprocedural and does not cover every possible way query text can be
 * built (see {@link GormQuerySafetyTransformer}'s documented limitations), a build that hits a
 * false positive it can't otherwise work around may need to disable it entirely rather than per
 * call site. Setting the {@value #PROTECT_SQL_INJECTION_ATTACKS_PROPERTY} system property to
 * {@code false} skips this transformation for the whole compilation unit - this is a global kill
 * switch of last resort, not a substitute for {@link GormQuerySafetyTransformer#SUPPRESS_WARNINGS_VALUE}.
 *
 * @since 8.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class GlobalGormQuerySafetyASTTransformation implements ASTTransformation, TransformWithPriority {

    /**
     * System property that enables this check; defaults to {@code true}. There is no fully
     * accurate way to guarantee protection against SQL/HQL/Cypher injection at compile time (this
     * check has documented gaps), so the name describes intent, not a guarantee. Set to
     * {@code false} only as a last resort, e.g. a false positive blocking a build with no other
     * workaround.
     */
    public static final String PROTECT_SQL_INJECTION_ATTACKS_PROPERTY = "protectSqlInjectionAttacks";

    public void visit(ASTNode[] nodes, SourceUnit source) {
        if (!Boolean.parseBoolean(System.getProperty(PROTECT_SQL_INJECTION_ATTACKS_PROPERTY, "true"))) {
            return;
        }
        GormQuerySafetyTransformer transformer = new GormQuerySafetyTransformer(source);
        ModuleNode ast = source.getAST();
        List<ClassNode> classes = ast.getClasses();
        for (ClassNode aClass : classes) {
            transformer.visitClass(aClass);
        }
    }

    @Override
    public int priority() {
        return GroovyTransformOrder.QUERY_SAFETY_ORDER;
    }
}
