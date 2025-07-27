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

package org.grails.datastore.gorm.async.transform
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.codehaus.groovy.transform.GroovyASTTransformationClass

/**
 * An AST transformation that takes each method in the given class and adds a delegate method that returns a {@link grails.async.Promise} and executes the method asynchronously.
 * For example given the following class:
 *
 * <pre><code>
 * class BookApi {
 *    List<Book> listBooksByTitle(String title) { }
 * }
 * </code></pre>
 *
 * If the annotation is applied to a new class:
 *
 * <pre><code>
 * @DelegateAsync(BookApi)
 * class AsyncBookApi {}
 * </code></pre>
 *
 * The resulting class is transformed into:
 *
 * <pre><code>
 * class AsyncBookApi {
 *     private BookApi $bookApi
 *     Promise<List<Book>> listBooksByTitle(String title) {
 *        (Promise<List<Book>>)Promises.createPromise {
 *            $bookApi.listBooksByTitle(title)
 *        }
 *     }
 * }
 * </code></pre>
 *
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.TYPE, ElementType.FIELD])
@GroovyASTTransformationClass("org.grails.datastore.gorm.async.transform.DelegateAsyncTransformation")
@interface DelegateAsync {
    Class value() default DelegateAsync
}
