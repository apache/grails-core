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
package org.apache.grails.core.aot;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the JDK types the framework's dynamically compiled code calls.
 *
 * <p>A plugin descriptor's closures, the bean DSL and much of the framework's older Groovy are compiled
 * without static compilation, so a call on a map, a string or a number in them is resolved through Groovy's
 * metaclass for whatever it is made on, and made reflectively -- and so is a {@code new ArrayList()}, and
 * so is reading a constant such as {@code File.separator}. An
 * image keeps a JDK method or constructor for that only when something asked for it, and Groovy's own
 * metadata covers the types Groovy owns rather than the JDK's. An image built without these stopped as the
 * plugins declared their beans, on a call nobody wrote on a type everybody uses.</p>
 *
 * <p>The types are the ones that code calls, which are few and stable: the collections and their entries,
 * strings, numbers and their wrappers, and the static helpers it reaches for. Where an interface declares
 * the method, the interface is named and its implementations are reached through it.</p>
 *
 * @since 9.0
 */
public class JdkDispatchRuntimeHints implements RuntimeHintsRegistrar {

    /** What dynamically compiled code calls methods on and reads constants from. */
    private static final Class<?>[] CALLED = {
        Object.class,
        String.class,
        CharSequence.class,
        Comparable.class,
        Number.class,
        Boolean.class,
        Enum.class,
        Iterable.class,
        Iterator.class,
        Collection.class,
        List.class,
        Set.class,
        Map.class,
        Map.Entry.class,
        System.class,
        Arrays.class,
        Collections.class,
        Objects.class,
        File.class
    };

    /** What it creates with {@code new}. */
    private static final Class<?>[] CREATED = {
        ArrayList.class,
        HashMap.class,
        LinkedHashMap.class,
        TreeMap.class,
        HashSet.class,
        LinkedHashSet.class,
        TreeSet.class,
        BigDecimal.class,
        BigInteger.class
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : CALLED) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_PUBLIC_FIELDS);
        }
        for (Class<?> type : CREATED) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }

}
