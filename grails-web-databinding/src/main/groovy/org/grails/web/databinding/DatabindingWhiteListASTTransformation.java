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
package org.grails.web.databinding;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * Gives a class that is bound by rules its data binding white lists when nothing in its compilation unit gave it
 * them, and records the names in them on the class. Domain classes and the parameter types of controller actions
 * get their white lists from the injectors that process them. The base class they extend does not, nor does a
 * command object compiled apart from the controller that binds it, as one in a plugin or a shared module is, nor a
 * class that only inherits its rules; the binder would bind such a class unrestricted, and a subclass compiled
 * against it would have to recompute its names from bytecode.
 *
 * <p>Runs after canonicalization, so the setters that transformations such as {@code @Delegate} add to the class are
 * in its lists. A subclass of the same compilation unit computes its own lists at canonicalization, before those
 * transformations have run on its parent, and the parent's lists then keep the names the subclass inherited, so
 * that the two agree: a property the parent gets by delegation is reached through the delegate there.</p>
 *
 * <p>A class that declares its white lists itself keeps them, and gets the record of them, so that a subclass
 * compiled against it reads the declared lists as it would read generated ones.</p>
 */
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class DatabindingWhiteListASTTransformation implements ASTTransformation {

    @Override
    public void visit(final ASTNode[] nodes, final SourceUnit source) {
        final DefaultASTDatabindingHelper helper = new DefaultASTDatabindingHelper();
        for (ClassNode classNode : source.getAST().getClasses()) {
            if (!classNode.isInterface() && !classNode.isEnum() && !classNode.isScript() &&
                    (DefaultASTDatabindingHelper.hasBindingRules(classNode) || DefaultASTDatabindingHelper.hasWhiteListField(classNode))) {
                helper.addDefaultDatabindingWhitelistFields(source, classNode);
            }
        }
    }
}
