/*
 * Copyright 2014 the original author or authors.
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
import java.util.Set;

import org.springframework.util.StringUtils;

public class CombinedCodecIdentifier implements CodecIdentifier {
    private CodecIdentifier[] codecIdentifiers;
    private String codecName;
    private String codecAlias;
    
    CombinedCodecIdentifier(CodecIdentifierProvider[] encodersOrDecoders) {
        this(encodersOrDecoders, false);
    }
    
    CombinedCodecIdentifier(CodecIdentifierProvider[] encodersOrDecoders, boolean reverseOrder) {
        int size = encodersOrDecoders.length;
        codecIdentifiers = new CodecIdentifier[size];
        String[] encoderNamesArr = new String[size];
        for(int i=0;i < size;i++) {
            int targetIndex = reverseOrder ? (size - 1 - i) : i;
            codecIdentifiers[targetIndex] = encodersOrDecoders[i].getCodecIdentifier();
            encoderNamesArr[targetIndex] = codecIdentifiers[targetIndex].getCodecName();
        }
        this.codecName = StringUtils.collectionToDelimitedString(Arrays.asList(encoderNamesArr), "And");
        this.codecAlias = StringUtils.collectionToCommaDelimitedString(Arrays.asList(encoderNamesArr));
    }

    @Override
    public String getCodecName() {
        return codecName;
    }

    @Override
    public Set<String> getCodecAliases() {
        return Collections.singleton(codecAlias);
    }

    @Override
    public boolean isEquivalent(CodecIdentifier other) {
        for(CodecIdentifier codecIdentifier : codecIdentifiers) { 
            if(codecIdentifier.isEquivalent(other)) {
                return true;
            }
        }
        return false;
    }
}