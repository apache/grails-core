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
package org.grails.taglib.discovery;

import java.util.ArrayList;
import java.util.List;

import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

/**
 * A tag library read from its syntax tree, as a build reads one while compiling it.
 *
 * @since 8.0.0
 */
public final class AstTagLibraryView implements TagLibraryView {

    private static final ClassNode CLOSURE_TYPE = ClassHelper.make(Closure.class);

    private final ClassNode classNode;
    private final boolean parameterNamesRetained;

    public AstTagLibraryView(ClassNode classNode, boolean parameterNamesRetained) {
        this.classNode = classNode;
        this.parameterNamesRetained = parameterNamesRetained;
    }

    @Override
    public List<TagMethodView> declaredMethods() {
        List<TagMethodView> declared = new ArrayList<>();
        for (MethodNode method : classNode.getMethods()) {
            // A method inherited from a superclass is not dispatchable, because dispatch scans
            // declared methods; a trait method is woven as a declaration and so is still seen here.
            if (method.getDeclaringClass() != null && !classNode.equals(method.getDeclaringClass())) {
                continue;
            }
            declared.add(new AstTagMethodView(method, parameterNamesRetained));
        }
        return declared;
    }

    @Override
    public List<String> declaredClosureFieldNames() {
        List<String> names = new ArrayList<>();
        for (FieldNode field : classNode.getFields()) {
            if (field.isStatic() || field.getType() == null) {
                continue;
            }
            // Assignability rather than equality, because the runtime asks isAssignableFrom: a field
            // declared as a subclass of Closure is a tag there and has to be one here too.
            if (field.getType().isDerivedFrom(CLOSURE_TYPE) || CLOSURE_TYPE.equals(field.getType())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    @Override
    public TagLibraryView superclassView() {
        ClassNode superClass = classNode.getSuperClass();
        if (superClass == null || ClassHelper.isObjectType(superClass)) {
            return null;
        }
        return new AstTagLibraryView(superClass, parameterNamesRetained);
    }
}
