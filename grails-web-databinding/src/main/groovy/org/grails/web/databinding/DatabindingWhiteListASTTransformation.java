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
 * Gives a class that is bound by rules a data binding white list when nothing in its compilation unit gave it one,
 * and records the names in it on the class. Domain classes and the parameter types of controller actions get their
 * white list from the injectors that process them. The base class they extend does not, nor does a command object
 * compiled apart from the controller that binds it, as one in a plugin or a shared module is, nor a class that only
 * inherits its rules; the binder would bind such a class unrestricted, and a subclass compiled against it would
 * have to recompute its names from bytecode.
 *
 * <p>Runs after canonicalization, so the setters that transformations such as {@code @Delegate} add to the class are
 * in its list. A subclass of the same compilation unit computes its own list at canonicalization, before those
 * transformations have run on its parent, and the parent's list then keeps the names the subclass inherited, so
 * that the two agree: a property the parent gets by delegation is reached through the delegate there.</p>
 *
 * <p>A class that declares its white list itself keeps it, and gets the record of it, so that a subclass compiled
 * against it reads the declared list as it would read a generated one.</p>
 */
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class DatabindingWhiteListASTTransformation implements ASTTransformation {

    @Override
    public void visit(final ASTNode[] nodes, final SourceUnit source) {
        final DefaultASTDatabindingHelper helper = new DefaultASTDatabindingHelper();
        for (ClassNode classNode : source.getAST().getClasses()) {
            if (!classNode.isInterface() && !classNode.isEnum() && !classNode.isScript() &&
                    (DefaultASTDatabindingHelper.hasBindingRules(classNode) || DefaultASTDatabindingHelper.hasWhiteListField(classNode))) {
                helper.addDefaultDatabindingWhitelistField(source, classNode);
            }
        }
    }
}
