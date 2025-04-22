/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.compiler.ast;

import org.codehaus.groovy.ast.ClassNode;

/**
 * Mainly just a marker interface for implementations that perform injection on domain classes.
 *
 * @author Graeme Rocher
 *
 * @since 0.2
 */
public interface GrailsDomainClassInjector extends ClassInjector {

    /**
     * Doesn't check with the specified ClassNode is a valid entity and assumes it
     * is and proceeds with the injection regardless.
     *
     * @param classNode The ClassNode
     * @since 1.1
     */
    void performInjectionOnAnnotatedEntity(ClassNode classNode);
}
