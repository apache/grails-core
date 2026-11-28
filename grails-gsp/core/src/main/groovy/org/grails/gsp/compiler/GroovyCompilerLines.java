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
package org.grails.gsp.compiler;

import org.codehaus.groovy.control.SourceUnit;

/**
 * What the Groovy compiler on the class path takes for the end of a line of a source. The table of
 * lines of a page has an entry for each line of the generated class as the compiler numbers them, so
 * it has to count them as the compiler does, and the versions of the compiler do not agree on one
 * character: a carriage return that no line feed follows ends no line for Groovy 6.0.0 and those
 * before it, and ends one for the versions after it. The compiler is asked, once.
 */
final class GroovyCompilerLines {

    private GroovyCompilerLines() {
    }

    /**
     * @return whether a carriage return that no line feed follows ends a line for the compiler
     */
    static boolean carriageReturnEndsLine() {
        return Holder.CARRIAGE_RETURN_ENDS_LINE;
    }

    private static final class Holder {

        static final boolean CARRIAGE_RETURN_ENDS_LINE = statementLineAfter("\r") > 1;

        private Holder() {
        }
    }

    // the line the compiler gives a statement that follows a comment with the characters in it
    private static int statementLineAfter(String characters) {
        try {
            SourceUnit unit = SourceUnit.create("lines", "/*" + characters + "*/ 1");
            unit.parse();
            unit.completePhase();
            unit.nextPhase();
            unit.convert();
            return unit.getAST().getStatementBlock().getStatements().get(0).getLineNumber();
        } catch (RuntimeException | LinkageError e) {
            // a compiler that cannot be asked is taken for the one Grails is built with
            return 1;
        }
    }
}
