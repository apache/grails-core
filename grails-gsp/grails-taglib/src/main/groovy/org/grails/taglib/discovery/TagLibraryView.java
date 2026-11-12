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

import java.util.List;

/**
 * A tag library as the rules need to read it, whether from a syntax tree or from a compiled class.
 *
 * <p>{@link TagMethodView} lets the two agree on whether a method is a tag. This lets them agree on
 * which members to ask about in the first place, which is the other half of the same question: a tag
 * declared as a {@code Closure} field is inherited, so enumerating one class is not enough, while a
 * tag declared as a method is not, because dispatch reads declared methods only. Keeping the walk
 * here rather than once per side is what stops the index describing a different set of tags from the
 * one an application registers.
 *
 * @since 8.0.0
 */
public interface TagLibraryView {

    /**
     * @return the methods this class itself declares, excluding anything inherited
     */
    List<TagMethodView> declaredMethods();

    /**
     * @return the names of the non-static fields this class itself declares whose type is a
     *         {@code Closure}, including a subclass of one
     */
    List<String> declaredClosureFieldNames();

    /**
     * @return the superclass to continue the walk with, or {@code null} at the top of the hierarchy
     *         or where the superclass cannot be read
     */
    TagLibraryView superclassView();
}
