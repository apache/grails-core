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
package org.grails.encoder

import groovy.transform.CompileStatic

/**
 * default implementation of {@link CodecIdentifier}
 *
 * @author Lari Hotari
 * @since 2.3
 */
@CompileStatic
class DefaultCodecIdentifier implements CodecIdentifier {

    private final String codecName
    private final Set<String> codecAliases

    DefaultCodecIdentifier(String codecName) {
        this(codecName, (Set<String>) null)
    }

    DefaultCodecIdentifier(String codecName, String... codecAliases) {
        this(codecName, codecAliases != null ? new HashSet<>(Arrays.asList(codecAliases)) : null)
    }

    DefaultCodecIdentifier(String codecName, Set<String> codecAliases) {
        this.codecName = codecName
        this.codecAliases = codecAliases != null ? Collections.unmodifiableSet(codecAliases) : null
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#getCodecName()
     */
    String getCodecName() {
        return codecName
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#getCodecAliases()
     */
    Set<String> getCodecAliases() {
        return codecAliases
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((codecAliases == null) ? 0 : codecAliases.hashCode())
        result = prime * result + ((codecName == null) ? 0 : codecName.hashCode())
        return result
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        DefaultCodecIdentifier other = (DefaultCodecIdentifier) obj
        if (codecAliases == null) {
            if (other.codecAliases != null)
                return false
        }
        else if (!codecAliases.equals(other.codecAliases))
            return false
        if (codecName == null) {
            if (other.codecName != null)
                return false
        }
        else if (!codecName.equals(other.codecName))
            return false
        return true
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    String toString() {
        return 'DefaultCodecIdentifier [codecName=' + codecName + ', codecAliases=' + codecAliases + ']'
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#isEquivalent(CodecIdentifier)
     */
    boolean isEquivalent(CodecIdentifier other) {
        if (this.codecName.equals(other.getCodecName())) {
            return true
        }
        if (this.codecAliases != null && this.codecAliases.contains(other.getCodecName())) {
            return true
        }
        if (other.getCodecAliases() != null && other.getCodecAliases().contains(this.codecName)) {
            return true
        }
        return false
    }
}
