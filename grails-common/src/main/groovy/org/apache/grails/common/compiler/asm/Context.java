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
package org.apache.grails.common.compiler.asm;

import org.springframework.asm.Label;

/**
 * Information about a class being parsed in a {@link ClassReader}.
 *
 * @author Eric Bruneton
 */
class Context {

    /**
     * Prototypes of the attributes that must be parsed for this class.
     */
    Attribute[] attrs;

    /**
     * The {@link ClassReader} option flags for the parsing of this class.
     */
    int flags;

    /**
     * The buffer used to read strings.
     */
    char[] buffer;

    /**
     * The start index of each bootstrap method.
     */
    int[] bootstrapMethods;

    /**
     * The access flags of the method currently being parsed.
     */
    int access;

    /**
     * The name of the method currently being parsed.
     */
    String name;

    /**
     * The descriptor of the method currently being parsed.
     */
    String desc;

    /**
     * The label objects, indexed by bytecode offset, of the method currently
     * being parsed (only bytecode offsets for which a label is needed have a
     * non null associated Label object).
     */
    Label[] labels;

    /**
     * The target of the type annotation currently being parsed.
     */
    int typeRef;

    /**
     * The path of the type annotation currently being parsed.
     */
    TypePath typePath;

    /**
     * The offset of the latest stack map frame that has been parsed.
     */
    int offset;

    /**
     * The labels corresponding to the start of the local variable ranges in the
     * local variable type annotation currently being parsed.
     */
    Label[] start;

    /**
     * The labels corresponding to the end of the local variable ranges in the
     * local variable type annotation currently being parsed.
     */
    Label[] end;

    /**
     * The local variable indices for each local variable range in the local
     * variable type annotation currently being parsed.
     */
    int[] index;

    /**
     * The encoding of the latest stack map frame that has been parsed.
     */
    int mode;

    /**
     * The number of locals in the latest stack map frame that has been parsed.
     */
    int localCount;

    /**
     * The number locals in the latest stack map frame that has been parsed,
     * minus the number of locals in the previous frame.
     */
    int localDiff;

    /**
     * The local values of the latest stack map frame that has been parsed.
     */
    Object[] local;

    /**
     * The stack size of the latest stack map frame that has been parsed.
     */
    int stackCount;

    /**
     * The stack values of the latest stack map frame that has been parsed.
     */
    Object[] stack;
}
