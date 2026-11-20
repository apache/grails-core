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
package grails.compiler.traits

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

import grails.artefact.gsp.TagLibraryInvoker
import grails.gsp.taglib.compiler.CompiledTagCallRewriter
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.taglib.index.TagLibraryIndex

/**
 * Compiles a call to a known tag into a direct invocation, wherever tags can be called from.
 *
 * <p>A tag library rewrites its own calls as it is compiled, but a controller can call tags too, and
 * gains that ability from the {@link TagLibraryInvoker} trait rather than from being a tag library.
 * Any class carrying that trait is therefore a candidate, which covers controllers without naming
 * them and without a second copy of the rewriting rules. A compiled GSP calls tags as well, and
 * reaches them through {@code GroovyPage} rather than through the trait, so it is matched separately.
 *
 * <p>Runs in a later phase than trait injection, since whether a class can call tags is only settled
 * once its traits have been applied. Ordering by priority within a phase was not enough: it holds for
 * transforms that declare one, but a trait arriving from a local transform is applied after every
 * global transform has run, so a class compiled from outside the conventional directory was read
 * before it carried the trait, and the same source read as a convention class was read after. Waiting
 * for the phase reads every class once it is whole, which is what the rewrite needs to decide.
 *
 * @since 8.0.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class CompiledTagCallTransformation implements ASTTransformation, TransformWithPriority {

    private static final ClassNode TAG_LIBRARY_INVOKER = ClassHelper.make(TagLibraryInvoker)

    /**
     * Matched by name rather than by type: the page runtime is not on the classpath this
     * transformation is compiled against, and need not be.
     */
    private static final String GROOVY_PAGE = 'org.grails.gsp.GroovyPage'

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode module = source.getAST()
        if (module == null) {
            return
        }
        TagLibraryIndex index = null
        for (ClassNode classNode : module.getClasses()) {
            if (!callsTags(classNode)) {
                continue
            }
            if (index == null) {
                index = TagLibraryIndex.forClassLoader(source.getClassLoader())
                if (index.isEmpty()) {
                    return
                }
            }
            new CompiledTagCallRewriter(source, index, classNode).rewrite()
        }
    }

    /**
     * @return true when the class can call tags, which is what carrying the tag library invoker trait
     *         means, whether it is a controller, a tag library or anything else given that ability,
     *         or what being a compiled page means
     */
    private static boolean callsTags(ClassNode classNode) {
        if (classNode.implementsInterface(TAG_LIBRARY_INVOKER) || classNode.declaresInterface(TAG_LIBRARY_INVOKER)) {
            return true
        }
        for (ClassNode current = classNode.superClass; current != null; current = current.superClass) {
            if (GROOVY_PAGE == current.name) {
                return true
            }
        }
        false
    }

    @Override
    int priority() {
        GroovyTransformOrder.COMPILED_TAG_CALL_ORDER
    }
}
