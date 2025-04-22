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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ChainedEncoder implements Encoder, StreamingEncoder {
    private final StreamingEncoder[] encoders;
    private final CodecIdentifier combinedCodecIdentifier;
    private final boolean safe;
    // this ThreadLocal lives as long as the instance of this ChainedEncoder class, this isn't a static ThreadLocal 
    private final ThreadLocal<ChainedEncoderCacheItem> cacheItemThreadLocal = new ThreadLocal<ChainedEncoder.ChainedEncoderCacheItem>() {
      protected ChainedEncoderCacheItem initialValue() {
          return new ChainedEncoderCacheItem();
      };  
    };
    
    private static class ChainedEncoderCacheItem {
        EncodedAppender lastAppenderForCached;
        boolean lastIgnoreEncodingStateForCached;
        EncodedAppender cachedChainedAppender;
        
        void putInCache(final EncodedAppender appender, EncodedAppender target) {
            lastAppenderForCached = appender;
            lastIgnoreEncodingStateForCached = appender.isIgnoreEncodingState();
            cachedChainedAppender = target;
        }
        
        EncodedAppender getCached(final EncodedAppender appender) {
            if(lastAppenderForCached == appender && lastIgnoreEncodingStateForCached == appender.isIgnoreEncodingState()) {
                return cachedChainedAppender;
            }
            return null;
        }
    }
    
    public ChainedEncoder(List<StreamingEncoder> encoders, boolean safe) {
        this(encoders.toArray(new StreamingEncoder[encoders.size()]), safe);
    }
    
    public ChainedEncoder(StreamingEncoder[] encoders, boolean safe) {
        this.encoders = Arrays.copyOf(encoders, encoders.length);
        this.combinedCodecIdentifier = createCodecIdentifier(encoders);
        this.safe = safe;
    }
    
    public static StreamingEncoder createFor(StreamingEncoder[] encoders) {
        return createFor(Arrays.asList(encoders));
    }
    
    public static StreamingEncoder createFor(List<StreamingEncoder> encoders) {
        return createFor(encoders, null);
    }
    
    public static StreamingEncoder createFor(List<StreamingEncoder> encoders, Boolean safe) {
        if(encoders==null) {
            return null;
        } else if(encoders.size()==0) {
            return DefaultEncodingStateRegistry.NONE_ENCODER;
        } else if(encoders.size()==1) {
            return encoders.get(0);
        } else {
            if(safe == null) {
                for(StreamingEncoder encoder : encoders) {
                    if(encoder.isSafe()) {
                        safe = true;
                        break;
                    }
                }
            }
            return new ChainedEncoder(encoders, safe != null ? safe : false);
        }
    }

    protected CombinedCodecIdentifier createCodecIdentifier(StreamingEncoder[] encoders) {
        return new CombinedCodecIdentifier(encoders);
    }
    
    @Override
    public CodecIdentifier getCodecIdentifier() {
        return combinedCodecIdentifier;
    }

    @Override
    public void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len,
            EncodedAppender appender, EncodingState encodingState) throws IOException {
        EncodedAppender target = chainEncodersAndCachePerThread(appender);
        StreamingEncoder encoder=encoders[0];
        if(appender.shouldEncode(encoder, encodingState.getPreviousEncodingState())) {
            encoder.encodeToStream(encoder, source, offset, len, target, encodingState.getPreviousEncodingState());
        } else {
            target.appendEncoded(encoder, encodingState.getPreviousEncodingState(), source, offset, len);
        }
    }

    protected EncodedAppender chainEncodersAndCachePerThread(final EncodedAppender appender) {
        ChainedEncoderCacheItem cacheItem = cacheItemThreadLocal.get();
        
        EncodedAppender target = cacheItem.getCached(appender);
        if(target == null) {
            target = doChainEncoders(appender);
            cacheItem.putInCache(appender, target);
        }
        return target;
    }

    protected EncodedAppender doChainEncoders(final EncodedAppender appender) {
        EncodedAppender target=appender;
        for(int i=encoders.length-1;i >= 1;i--) {
            StreamingEncoder encoder=encoders[i];
            target=new StreamingEncoderEncodedAppender(encoder, target);
            target.setIgnoreEncodingState(appender.isIgnoreEncodingState());
        }
        return target;
    }

    @Override
    public Object encode(Object o) {
        if(o==null) return o;
        Object encoded = o;
        for (StreamingEncoder encoder : encoders) {
            encoded = encoder.encode(encoded);
        }
        return encoded;
    }

    @Override
    public boolean isSafe() {
        return safe;
    }

    @Override
    public boolean isApplyToSafelyEncoded() {
        return true;
    }

    @Override
    public void markEncoded(CharSequence string) {
        
    }
}
