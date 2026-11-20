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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic

/**
 * Thrown when feature packs that were given cannot be among the features of a generation. It says
 * which packs, and whether it is a pack itself that cannot be used here, whatever it is given with, or
 * the packs that were given together.
 *
 * @since 9.0
 */
@CompileStatic
class PackRefusedException extends IllegalArgumentException {

    private final List<Pack> packs
    private final boolean ofThePack

    /**
     * @param message what is refused, with where each pack came from
     * @param packs the packs the refusal is about
     * @param ofThePack whether the pack cannot be used whatever it is given with: it has the name of a
     * feature of the framework, names a group of the framework, or is for another Grails or Forge
     */
    PackRefusedException(String message, List<Pack> packs, boolean ofThePack) {
        super(message)
        this.packs = Collections.unmodifiableList(new ArrayList<Pack>(packs))
        this.ofThePack = ofThePack
    }

    /**
     * @return the packs the refusal is about
     */
    List<Pack> getPacks() {
        return packs
    }

    /**
     * @return whether it is the pack itself that cannot be used, and not what it was given together with
     */
    boolean isOfThePack() {
        return ofThePack
    }
}
