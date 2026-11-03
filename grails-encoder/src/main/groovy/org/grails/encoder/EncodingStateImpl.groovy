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
 * Default implementation of {@link EncodingState}
 *
 * @author Lari Hotari
 * @since 2.3
 */
@CompileStatic
class EncodingStateImpl implements EncodingState {

    public static final EncodingState UNDEFINED_ENCODING_STATE = new EncodingStateImpl((Set<Encoder>) null, null)
    private final Set<Encoder> encoders
    private final EncodingState previousEncodingState

    /**
     * Default constructor
     *
     * @param encoders
     *            the encoders
     */
    EncodingStateImpl(Set<Encoder> encoders, EncodingState previousEncodingState) {
        this.encoders = encoders
        this.previousEncodingState = previousEncodingState
    }

    EncodingStateImpl(Encoder encoder, EncodingState previousEncodingState) {
        this(Collections.singleton(encoder), previousEncodingState)
    }

    /*
     * (non-Javadoc)
     * @see
     * EncodingState#getEncoders()
     */
    Set<Encoder> getEncoders() {
        return encoders
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((encoders == null) ? 0 : encoders.hashCode())
        return result
    }

    /*
     * (non-Javadoc)
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
        EncodingStateImpl other = (EncodingStateImpl) obj
        if (encoders == null) {
            if (other.encoders != null && !other.encoders.isEmpty())
                return false
        }
        else if (!encoders.equals(other.encoders))
            return false
        return true
    }

    EncodingState appendEncoder(Encoder encoder) {
        if (encoder == null) return this
        Set<Encoder> newEncoders
        if (encoders == null || encoders.isEmpty()) {
            newEncoders = Collections.singleton(encoder)
        } else if (encoders.size() == 1 && encoder in encoders) {
            return this
        } else {
            newEncoders = new LinkedHashSet<>()
            newEncoders.addAll(encoders)
            newEncoders.add(encoder)
        }
        return new EncodingStateImpl(newEncoders, this)
    }

    @Override
    String toString() {
        StringBuilder sb = new StringBuilder()
        sb << 'EncodingStateImpl'
        if (encoders != null && !encoders.isEmpty()) {
            sb << ' [encoders='
            boolean first = true
            for (Encoder encoder in encoders) {
                if (!first) {
                    sb << ', '
                } else {
                    first = false
                }
                sb << '[@' << System.identityHashCode(encoder) << ' '
                sb << "${encoder.codecIdentifier.codecName} safe:${encoder.safe} apply to safe:${encoder.applyToSafelyEncoded}"
                sb << ']'
            }
            sb << ']'
        } else {
            sb << '[no encoders]'
        }
        return sb.toString()
    }

    @Override
    EncodingState getPreviousEncodingState() {
        return previousEncodingState
    }
}
