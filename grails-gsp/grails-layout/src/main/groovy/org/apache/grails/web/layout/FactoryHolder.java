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

package org.apache.grails.web.layout;

import com.opensymphony.module.sitemesh.Factory;

import grails.util.Holder;

/**
 * Holds a reference to the Grails Layout Factory object.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
public class FactoryHolder {

    private static Holder<Factory> holder = new Holder<Factory>("factory");

    private FactoryHolder() {
        // static only
    }

    public static Factory getFactory() {
        Factory factory = holder.get();
        return factory;
    }

    public static Factory getGrailsLayoutFactory() {
        return getFactory();
    }

    public static synchronized void setFactory(Factory factory) {
        holder.set(factory);
    }
}
