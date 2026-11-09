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

import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import grails.gsp.NotATag;
import grails.gsp.Tag;

/**
 * A method being compiled, seen through {@link TagMethodView} so that {@link TagDiscoveryRules} can
 * classify it before the class exists.
 *
 * <p>Two differences from the compiled view are handled here. Parameter defaults have not yet been
 * expanded into overloads, so they are reported as optional. And whether names will survive into the
 * class file is a property of the compilation rather than of the method, so it is supplied by the
 * caller from the compiler configuration.
 *
 * @since 8.0.0
 */
public final class AstTagMethodView implements TagMethodView {

    private static final ClassNode CLOSURE_TYPE = ClassHelper.make(Closure.class);
    private static final ClassNode MAP_TYPE = ClassHelper.MAP_TYPE;
    private static final ClassNode TAG_ANNOTATION = ClassHelper.make(Tag.class);
    private static final ClassNode NOT_A_TAG_ANNOTATION = ClassHelper.make(NotATag.class);

    private final MethodNode method;
    private final Parameter[] parameters;
    private final boolean parameterNamesRetained;

    /**
     * @param method the method being compiled
     * @param parameterNamesRetained whether this compilation writes parameter names into the class
     *        file, which decides whether the attributes and body parameters have to carry those names
     */
    public AstTagMethodView(MethodNode method, boolean parameterNamesRetained) {
        this.method = method;
        this.parameters = method.getParameters();
        this.parameterNamesRetained = parameterNamesRetained;
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public boolean isPublic() {
        return method.isPublic();
    }

    @Override
    public boolean isStatic() {
        return method.isStatic();
    }

    @Override
    public boolean isGenerated() {
        // Trait application produces super-accessor bridges that are synthetic once compiled but are
        // not marked so on the tree; TagDiscoveryRules also rejects their names.
        return method.isSynthetic() || method.isAbstract();
    }

    @Override
    public boolean hasTagAnnotation() {
        return !method.getAnnotations(TAG_ANNOTATION).isEmpty();
    }

    @Override
    public boolean hasNotATagAnnotation() {
        return !method.getAnnotations(NOT_A_TAG_ANNOTATION).isEmpty();
    }

    @Override
    public int getParameterCount() {
        return parameters.length;
    }

    @Override
    public boolean isParameterMapAssignable(int index) {
        ClassNode type = parameters[index].getType();
        return type != null && (MAP_TYPE.equals(type) || type.isDerivedFrom(MAP_TYPE) ||
                type.implementsInterface(MAP_TYPE));
    }

    @Override
    public boolean isParameterClosureAssignable(int index) {
        ClassNode type = parameters[index].getType();
        return type != null && (CLOSURE_TYPE.equals(type) || type.isDerivedFrom(CLOSURE_TYPE));
    }

    @Override
    public String getParameterName(int index) {
        return parameters[index].getName();
    }

    @Override
    public boolean isParameterNamePresent(int index) {
        return parameterNamesRetained;
    }

    @Override
    public boolean isParameterOptional(int index) {
        return parameters[index].hasInitialExpression();
    }
}
