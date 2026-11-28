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
package logging

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Makes a logger of SLF4J when it is loaded, as the global transformation of Grails does, and says each
 * source it visits, so that a specification sees that it ran.
 */
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
class LoggingTransformation implements ASTTransformation {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingTransformation)

    void visit(ASTNode[] nodes, SourceUnit source) {
        LOG.debug('Visiting {}', source.name)
        System.err.println("LOGGING TRANSFORMATION visited ${new File(source.name).name}")
    }
}
