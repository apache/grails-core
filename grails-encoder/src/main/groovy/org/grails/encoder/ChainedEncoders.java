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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.grails.buffer.StreamCharBuffer;

public class ChainedEncoders {

    private ChainedEncoders() {
    }

    public static List<StreamingEncoder> toStreamingEncoders(List<Encoder> encoders) {
        if(encoders == null || encoders.isEmpty()) {
            return null;
        }
        List<StreamingEncoder> streamingEncoders = new ArrayList<StreamingEncoder>();
        for(Encoder encoder : encoders) {
            if(!(encoder instanceof StreamingEncoder)) {
                return null;
            }
            StreamingEncoder streamingEncoder = (StreamingEncoder)encoder;
            if(shouldApplyEncoder(streamingEncoder)) {
                streamingEncoders.add(streamingEncoder);
            }
        }
        return streamingEncoders;
    }

    public static void chainEncode(StreamEncodeable streamEncodeable, EncodedAppender appender, List<Encoder> encoders) throws IOException {
        List<StreamingEncoder> streamingEncoders = toStreamingEncoders(encoders);
        if(streamingEncoders != null) {
            chainStreamingEncode(streamEncodeable, appender, streamingEncoders);
        } else {
            chainMixedEncode(streamEncodeable, appender, encoders);
        }
    }

    private static void chainMixedEncode(StreamEncodeable streamEncodeable, EncodedAppender appender,
            List<Encoder> encoders) throws IOException {
        if(encoders==null || encoders.isEmpty()) {
            streamEncodeable.encodeTo(appender, null);
        } else {     
            StreamEncodeable lastStreamEncodeable = streamEncodeable;
            if(encoders.size() > 1) {
                StreamCharBuffer buffer;
                if(streamEncodeable instanceof StreamCharBuffer) {
                    buffer = (StreamCharBuffer)streamEncodeable;
                } else {
                    buffer = new StreamCharBuffer();
                    streamEncodeable.encodeTo(((StreamCharBuffer.StreamCharBufferWriter)buffer.getWriter()).getEncodedAppender(), null);
                }
                for(int i=0;i < encoders.size()-1;i++) {
                    buffer = buffer.encodeToBuffer(encoders.get(i));
                }
                lastStreamEncodeable = buffer;
            }
            lastStreamEncodeable.encodeTo(appender, encoders.get(encoders.size()-1));
        }
    }

    public static void chainStreamingEncode(StreamEncodeable streamEncodeable, EncodedAppender appender, List<StreamingEncoder> encoders) throws IOException {
        EncodedAppender target;
        Encoder lastEncoder;
        if(encoders != null && encoders.size() > 0) {
            target = chainAllButLastEncoders(appender, encoders);
            lastEncoder = encoders.get(0);
        } else {
            target = appender;
            lastEncoder = null;
        }
        target.append(lastEncoder, streamEncodeable);
    }

    public static EncodedAppender chainAllButLastEncoders(EncodedAppender appender, List<StreamingEncoder> encoders) {
        EncodedAppender target=appender;
        for(int i=encoders.size()-1;i >= 1;i--) {
            StreamingEncoder encoder=encoders.get(i);
            target=new StreamingEncoderEncodedAppender(encoder, target);
        }
        return target;
    }

    public static EncodedAppender chainAllEncoders(EncodedAppender appender, List<StreamingEncoder> encoders) {
        EncodedAppender target=appender;
        for(int i=encoders.size()-1;i >= 0;i--) {
            StreamingEncoder encoder=encoders.get(i);
            target=new StreamingEncoderEncodedAppender(encoder, target);
        }
        return target;
    }

    public static List<Encoder> appendEncoder(List<Encoder> encoders, Encoder encodeToEncoder) {
        List<Encoder> nextEncoders;
        if(encodeToEncoder != null) {
            if(encoders != null) {
                List<Encoder> joined = new ArrayList<Encoder>(encoders.size()+1);
                joined.addAll(encoders);
                joined.add(encodeToEncoder);
                nextEncoders = Collections.unmodifiableList(joined);
            } else {
                nextEncoders = Collections.singletonList(encodeToEncoder);
            }
        } else {
            nextEncoders = encoders;
        }
        return nextEncoders;
    }

    /**
     * checks that the encoder isn't a NoneEncoder instance
     * 
     * @param encoder
     * @return
     */
    public static boolean shouldApplyEncoder(StreamingEncoder encoder) {
        return !DefaultEncodingStateRegistry.isNoneEncoder(encoder);
    }

}
