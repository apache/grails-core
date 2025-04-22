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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * default implementation of {@link CodecIdentifier}
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class DefaultCodecIdentifier implements CodecIdentifier {
    final private String codecName;
    final private Set<String> codecAliases;

    public DefaultCodecIdentifier(String codecName) {
        this(codecName, (Set<String>)null);
    }

    public DefaultCodecIdentifier(String codecName, String... codecAliases) {
        this(codecName, codecAliases != null ? new HashSet<String>(Arrays.asList(codecAliases)) : null);
    }

    public DefaultCodecIdentifier(String codecName, Set<String> codecAliases) {
        this.codecName = codecName;
        this.codecAliases = codecAliases != null ? Collections.unmodifiableSet(codecAliases) : null;
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#getCodecName()
     */
    public String getCodecName() {
        return codecName;
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#getCodecAliases()
     */
    public Set<String> getCodecAliases() {
        return codecAliases;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((codecAliases == null) ? 0 : codecAliases.hashCode());
        result = prime * result + ((codecName == null) ? 0 : codecName.hashCode());
        return result;
    }

    /* (non-Javadoc)
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
        DefaultCodecIdentifier other = (DefaultCodecIdentifier)obj;
        if (codecAliases == null) {
            if (other.codecAliases != null)
                return false;
        }
        else if (!codecAliases.equals(other.codecAliases))
            return false;
        if (codecName == null) {
            if (other.codecName != null)
                return false;
        }
        else if (!codecName.equals(other.codecName))
            return false;
        return true;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DefaultCodecIdentifier [codecName=" + codecName + ", codecAliases=" + codecAliases + "]";
    }

    /* (non-Javadoc)
     * @see CodecIdentifier#isEquivalent(CodecIdentifier)
     */
    public boolean isEquivalent(CodecIdentifier other) {
        if (this.codecName.equals(other.getCodecName())) {
            return true;
        }
        if (this.codecAliases != null && this.codecAliases.contains(other.getCodecName())) {
            return true;
        }
        if (other.getCodecAliases() != null && other.getCodecAliases().contains(this.codecName)) {
            return true;
        }
        return false;
    }
}
