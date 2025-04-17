/*
 * Copyright 2004-2005 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp;

import grails.util.Environment;
import groovy.lang.Binding;
import groovy.lang.Writable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.taglib.AbstractTemplateVariableBinding;
import org.grails.taglib.TemplateVariableBinding;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputContextLookup;

import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes itself to the specified writer, typically the response writer.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 0.5
 */
public class GroovyPageWritable implements Writable {
    private static final Log LOG = LogFactory.getLog(GroovyPageWritable.class);
    private static final String GSP_NONE_CODEC_NAME = "none";
    private GroovyPageMetaInfo metaInfo;
    private OutputContextLookup outputContextLookup;
    private boolean allowSettingContentType;
    @SuppressWarnings("rawtypes")
    private Map additionalBinding = new LinkedHashMap();
    private boolean showSource;

    private static final String GROOVY_SOURCE_CONTENT_TYPE = "text/plain";
    public GroovyPageWritable(GroovyPageMetaInfo metaInfo, OutputContextLookup outputContextLookup, boolean allowSettingContentType) {
        this.metaInfo = metaInfo;
        this.outputContextLookup = outputContextLookup;
        this.allowSettingContentType = allowSettingContentType;
    }



    /**
     * This sets any additional variables that need to be placed in the Binding of the GSP page.
     *
     * @param binding The additional variables
     */
    @SuppressWarnings("rawtypes")
    public void setBinding(Map binding) {
        if (binding != null) {
            additionalBinding = binding;
        }
    }

    /**
     * Writes the template to the specified Writer
     *
     * @param out The Writer to write to, normally the HttpServletResponse
     * @return Returns the passed Writer
     * @throws IOException
     */
    public Writer writeTo(Writer out) throws IOException {
        OutputContext outputContext = outputContextLookup.lookupOutputContext();
        try {
            return doWriteTo(outputContext, out);
        } finally {
            doCleanUp(outputContext, out);
        }
    }

    protected void doCleanUp(OutputContext outputContext, Writer out) {
        metaInfo.writeToFinished(out);
    }

    protected Writer doWriteTo(OutputContext outputContext, Writer out) throws IOException {
        if (shouldShowGroovySource(outputContext)) {
            // Set it to TEXT
            outputContext.setContentType(GROOVY_SOURCE_CONTENT_TYPE); // must come before response.getOutputStream()
            writeGroovySourceToResponse(metaInfo, out);
        }
        else {
            // Set it to HTML by default
            if (metaInfo.getCompilationException()!=null) {
                throw metaInfo.getCompilationException();
            }

            // Set up the script context
            AbstractTemplateVariableBinding parentBinding = null;
            boolean hasRequest = outputContext != null;
            boolean newParentCreated = false;

            if (hasRequest) {
                parentBinding = outputContext.getBinding();
                if (parentBinding == null) {
                    parentBinding = outputContext.createAndRegisterRootBinding();
                    newParentCreated = true;
                }
            }

            if (allowSettingContentType && hasRequest) {
                // only try to set content type when evaluating top level GSP
                boolean contentTypeAlreadySet = outputContext.isContentTypeAlreadySet();
                if (!contentTypeAlreadySet) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Writing output with content type: " + metaInfo.getContentType());
                    }
                    outputContext.setContentType(metaInfo.getContentType()); // must come before response.getWriter()
                }
            }

            GroovyPageBinding binding = createBinding(parentBinding);
            if (hasRequest) {
                outputContext.setBinding(binding);
            }

            GroovyPage page = null;
            try {
                page = (GroovyPage)metaInfo.getPageClass().newInstance();
            } catch (Exception e) {
                throw new GroovyPagesException("Problem instantiating page class", e);
            }
            page.setBinding(binding);
            binding.setOwner(page);

            page.initRun(out, outputContext, metaInfo);

            try {
                page.run();
            }
            finally {
                page.cleanup();
                if (hasRequest) {
                    if (newParentCreated) {
                        outputContext.setBinding(null);
                    } else  {
                        outputContext.setBinding(parentBinding);
                    }
                }
            }
        }
        return out;
    }

    private int incrementAndGetDebugTemplatesIdCounter(OutputContext outputContext) {
        //debugTemplatesIdCounter.incrementAndGet()
        return 0;
    }

    private boolean shouldShowGroovySource(OutputContext outputContext) {
        return isShowSource() && Environment.getCurrent() == Environment.DEVELOPMENT && metaInfo.getGroovySource() != null;
    }

    private static final GspNoneCodec gspNoneCodeInstance = new GspNoneCodec();

    public boolean isShowSource() {
        return showSource;
    }

    public void setShowSource(boolean showSource) {
        this.showSource = showSource;
    }

    private static final class GspNoneCodec {
        @SuppressWarnings("unused")
        public final Object encode(Object object) {
            return object;
        }
    }

    private GroovyPageBinding createBinding(Binding parent) {
        GroovyPageBinding binding = new GroovyPageBinding();
        binding.setParent(parent);
        binding.setVariableDirectly("it", null);
        if (additionalBinding != null) {
            binding.addMap(additionalBinding);
        }
        // set plugin context path for top level rendering, this means actual view + layout
        // view is top level when parent is GroovyPageRequestBinding
        // pluginContextPath is also resetted when a plugin template is overrided by an application view
        if (parent==null || (parent instanceof TemplateVariableBinding && ((TemplateVariableBinding)parent).isRoot()) || "".equals(metaInfo.getPluginPath())) {
            binding.setPluginContextPath(metaInfo.getPluginPath());
        }
        binding.setPagePlugin(metaInfo.getPagePlugin());
        return binding;
    }

    /**
     * Copy all of input to output.
     * @param in The input stream to writeInputStreamToResponse from
     * @param out The output to write to
     * @throws IOException When an error occurs writing to the response Writer
     */
    protected void writeInputStreamToResponse(InputStream in, Writer out) throws IOException {
        try {
            in.reset();
            Reader reader = new InputStreamReader(in, "UTF-8");
            char[] buf = new char[8192];

            for (;;) {
                int read = reader.read(buf);
                if (read <= 0) break;
                out.write(buf, 0, read);
            }
        }
        finally {
            out.close();
            in.close();
        }
    }

    /**
     * Writes the Groovy source code attached to the given info object
     * to the response, prefixing each line with its line number. The
     * line numbers make it easier to match line numbers in exceptions
     * to the generated source.
     * @param info The meta info for the GSP page that we want to write
     * the generated source for.
     * @param out The writer to send the source to.
     * @throws IOException If there is either a problem with the input
     * stream for the Groovy source, or the writer.
     */
    protected void writeGroovySourceToResponse(GroovyPageMetaInfo info, Writer out) throws IOException {
        InputStream in = info.getGroovySource();
        if (in == null) return;
        try {
            try {
                in.reset();
            }
            catch (IOException e) {
                // ignore
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            int lineNum = 1;
            int maxPaddingSize = 3;

            // Prepare the buffer containing the whitespace padding.
            // The padding is used to right-align the line numbers.
            StringBuilder paddingBuffer = new StringBuilder(maxPaddingSize);
            for (int i = 0; i < maxPaddingSize; i++) {
                paddingBuffer.append(' ');
            }

            // Set the initial padding.
            String padding = paddingBuffer.toString();

            // Read the Groovy source line by line and write it to the
            // response, prefixing each line with the line number.
            for (String line = reader.readLine(); line != null; line = reader.readLine(), lineNum++) {
                // Get the current line number as a string.
                String numberText = String.valueOf(lineNum);

                // Decrease the padding if the current line number has
                // more digits than the previous one.
                if (padding.length() + numberText.length() > 4) {
                    paddingBuffer.deleteCharAt(padding.length() - 1);
                    padding = paddingBuffer.toString();
                }

                // Write out this line.
                out.write(padding);
                out.write(numberText);
                out.write(": ");
                out.write(line);
                out.write('\n');
            }
        }
        finally {
            out.close();
            in.close();
        }
    }
}
