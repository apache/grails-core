/*
 * Copyright 2013 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.async.transform.internal;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ListExpression;

/**
 * Interface for a class that handles transforming async transactional methods
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public interface DelegateAsyncTransactionalMethodTransformer {
    void transformTransactionalMethod(ClassNode classNode, ClassNode delegateClassNode, MethodNode methodNode, ListExpression promiseDecoratorLookupArguments);
}
