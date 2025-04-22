/*
 * Copyright 2013 the original author or authors.
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
package org.grails.encoder;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of {@link EncodingState}
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class EncodingStateImpl implements EncodingState {
    public static final EncodingState UNDEFINED_ENCODING_STATE = new EncodingStateImpl((Set<Encoder>)null, null);
    private final Set<Encoder> encoders;
    private final EncodingState previousEncodingState;

    /**
     * Default constructor
     *
     * @param encoders
     *            the encoders
     */
    public EncodingStateImpl(Set<Encoder> encoders, EncodingState previousEncodingState) {
        this.encoders = encoders;
        this.previousEncodingState = previousEncodingState;
    }

    public EncodingStateImpl(Encoder encoder, EncodingState previousEncodingState) {
        this(Collections.singleton(encoder), previousEncodingState);
    }

    /*
     * (non-Javadoc)
     * @see
     * EncodingState#getEncoders()
     */
    public Set<Encoder> getEncoders() {
        return encoders;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((encoders == null) ? 0 : encoders.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EncodingStateImpl other = (EncodingStateImpl)obj;
        if (encoders == null) {
            if (other.encoders != null && other.encoders.size() > 0)
                return false;
        }
        else if (!encoders.equals(other.encoders))
            return false;
        return true;
    }

    public EncodingState appendEncoder(Encoder encoder) {
        if(encoder==null) return this;
        Set<Encoder> newEncoders;
        if (encoders == null || encoders.size()==0) {
            newEncoders = Collections.singleton(encoder);
        } else if (encoders.size()==1 && encoders.contains(encoder)) {
            return this;
        } else {
            newEncoders = new LinkedHashSet<Encoder>();
            newEncoders.addAll(encoders);
            newEncoders.add(encoder);
        }
        return new EncodingStateImpl(newEncoders, this);
    }

    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("EncodingStateImpl");
        if(encoders != null && encoders.size() > 0) {
            sb.append(" [encoders=");
            boolean first=true;
            for(Encoder encoder : encoders) {
                if(!first) {
                    sb.append(", ");
                } else {
                    first=false;
                }
                sb.append("[@");
                sb.append(System.identityHashCode(encoder));
                sb.append(" ");
                sb.append(encoder.getCodecIdentifier().getCodecName() + " safe:" + encoder.isSafe() + " apply to safe:" + encoder.isApplyToSafelyEncoded());
                sb.append("]");
            }
            sb.append("]");
        } else {
            sb.append("[no encoders]");
        }
        return sb.toString();
    }

    @Override
    public EncodingState getPreviousEncodingState() {
        return previousEncodingState;
    }
}
