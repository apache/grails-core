package org.grails.taglib;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.grails.encoder.Encoder;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputEncodingStack;
import org.grails.taglib.encoder.OutputEncodingStackAttributes;
import org.grails.taglib.encoder.WithCodecHelper;

import java.io.Writer;
import java.util.Map;

/**
 * Created by lari on 16/07/14.
 */
public class TagOutput {
    public static final String APPLY_CODEC_TAG_NAME = "applyCodec";
    public static final String ENCODE_AS_ATTRIBUTE_NAME = "encodeAs";
    public static final Closure<?> EMPTY_BODY_CLOSURE = new ConstantClosure("");
    public static final String DEFAULT_NAMESPACE = "g";

    private TagOutput() {
    }

    @SuppressWarnings("rawtypes")
    public final static Object captureTagOutput(TagLibraryLookup gspTagLibraryLookup, String namespace,
                                                String tagName, Map attrs, Object body, OutputContext outputContext) {

        GroovyObject tagLib = lookupCachedTagLib(gspTagLibraryLookup, namespace, tagName);

        if (tagLib == null) {
            throw new GrailsTagException("Tag [" + tagName + "] does not exist. No corresponding tag library found.");
        }

        if (!(attrs instanceof GroovyPageAttributes)) {
            attrs = new GroovyPageAttributes(attrs, false);
        }
        ((GroovyPageAttributes)attrs).setGspTagSyntaxCall(false);
        Closure actualBody = createOutputCapturingClosure(tagLib, body, outputContext);

        final GroovyPageTagWriter tagOutput = new GroovyPageTagWriter();
        OutputEncodingStack outputStack = null;
        try {
            outputStack = OutputEncodingStack.currentStack(outputContext, false);
            if (outputStack == null) {
                outputStack = OutputEncodingStack.currentStack(outputContext, true, tagOutput, true, true);
            }
            Map<String, Object> defaultEncodeAs = gspTagLibraryLookup.getEncodeAsForTag(namespace, tagName);
            Map<String, Object> codecSettings = createCodecSettings(namespace, tagName, attrs, defaultEncodeAs);

            OutputEncodingStackAttributes.Builder builder = WithCodecHelper.createOutputStackAttributesBuilder(codecSettings, outputContext.getGrailsApplication());
            builder.topWriter(tagOutput);
            outputStack.push(builder.build());

            Object tagLibProp = tagLib.getProperty(tagName); // retrieve tag lib and create wrapper writer
            if (tagLibProp instanceof Closure) {
                Closure tag = (Closure) ((Closure) tagLibProp).clone();
                Object bodyResult;

                switch (tag.getParameterTypes().length) {
                    case 1:
                        bodyResult = tag.call(new Object[]{attrs});
                        if (actualBody != null && actualBody != EMPTY_BODY_CLOSURE) {
                            Object bodyResult2 = actualBody.call();
                            if (bodyResult2 != null) {
                                if (actualBody instanceof ConstantClosure) {
                                    outputStack.getStaticWriter().print(bodyResult2);
                                } else {
                                    outputStack.getTaglibWriter().print(bodyResult2);
                                }
                            }
                        }

                        break;
                    case 2:
                        bodyResult = tag.call(new Object[]{attrs, actualBody});
                        break;
                    default:
                        throw new GrailsTagException("Tag [" + tagName +
                                "] does not specify expected number of params in tag library [" +
                                tagLib.getClass().getName() + "]");

                }

                Encoder taglibEncoder = outputStack.getTaglibEncoder();

                boolean returnsObject = gspTagLibraryLookup.doesTagReturnObject(namespace, tagName);

                if (returnsObject && bodyResult != null && !(bodyResult instanceof Writer)) {
                    if (taglibEncoder != null) {
                        bodyResult=taglibEncoder.encode(bodyResult);
                    }
                    return bodyResult;
                }

                // add some method to always return string, configurable?
                if (taglibEncoder != null) {
                    return taglibEncoder.encode(tagOutput.getBuffer());
                } else {
                    return tagOutput.getBuffer();
                }
            }

            throw new GrailsTagException("Tag [" + tagName + "] does not exist in tag library [" +
                    tagLib.getClass().getName() + "]");
        } finally {
            if (outputStack != null) outputStack.pop();
        }
    }

    public final static GroovyObject lookupCachedTagLib(TagLibraryLookup gspTagLibraryLookup,
                                                        String namespace, String tagName) {

        return gspTagLibraryLookup != null ? gspTagLibraryLookup.lookupTagLibrary(namespace, tagName) : null;
    }

    public final static Closure<?> createOutputCapturingClosure(Object wrappedInstance, final Object body1,
                                                                final OutputContext outputContext) {
        if (body1 == null) {
            return EMPTY_BODY_CLOSURE;
        }

        if (body1 instanceof ConstantClosure || body1 instanceof TagBodyClosure) {
            return (Closure<?>) body1;
        }

        if (body1 instanceof Closure) {
            return new TagBodyClosure(wrappedInstance, outputContext, (Closure<?>) body1);
        }

        return new ConstantClosure(body1);
    }

    public static Map<String, Object> createCodecSettings(String namespace, String tagName, @SuppressWarnings("rawtypes") Map attrs,
                                                          Map<String, Object> defaultEncodeAs) {
        Object codecInfo = null;
        if (attrs.containsKey(ENCODE_AS_ATTRIBUTE_NAME)) {
            codecInfo = attrs.get(ENCODE_AS_ATTRIBUTE_NAME);
        } else if (DEFAULT_NAMESPACE.equals(namespace) && APPLY_CODEC_TAG_NAME.equals(tagName)) {
            codecInfo = attrs;
        }
        Map<String, Object> codecSettings = WithCodecHelper.mergeSettingsAndMakeCanonical(codecInfo, defaultEncodeAs);
        return codecSettings;
    }

    @SuppressWarnings("rawtypes")
    public static final class ConstantClosure extends Closure {
        private static final long serialVersionUID = 1L;
        private static final Class[] EMPTY_CLASS_ARR=new Class[0];
        final Object retval;

        public ConstantClosure(Object retval) {
            super(null);
            this.retval = retval;
        }

        @Override
        public int getMaximumNumberOfParameters() {
            return 0;
        }

        @Override
        public Class[] getParameterTypes() {
            return EMPTY_CLASS_ARR;
        }

        public Object doCall(Object obj) {
            return retval;
        }

        public Object doCall() {
            return retval;
        }

        public Object doCall(Object[] args) {
            return retval;
        }

        @Override
        public Object call(Object... args) {
            return retval;
        }

        public boolean asBoolean() {
            return DefaultTypeTransformation.castToBoolean(retval);
        }
    }
}
