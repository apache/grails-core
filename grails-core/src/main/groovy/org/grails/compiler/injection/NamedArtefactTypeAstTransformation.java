/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.compiler.injection;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * An AST transform used to apply a named artefact type
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class NamedArtefactTypeAstTransformation extends AbstractArtefactTypeAstTransformation {

    String artefactType;

    public NamedArtefactTypeAstTransformation(String artefactType) {
        this.artefactType = artefactType;
    }

    public void visit(ASTNode[] nodes, SourceUnit source) {
        for (ClassNode node : source.getAST().getClasses()) {
            performInjectionOnArtefactType(source, node, artefactType);
        }
    }
}
