/*
 * Copyright 2004-2024 the original author or authors.
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
package org.grails.gsp.compiler;

import grails.config.ConfigMap;
import grails.io.IOUtils;
import grails.util.Environment;
import grails.util.GrailsStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.grails.buffer.FastStringWriter;
import org.grails.buffer.StreamByteBuffer;
import org.grails.buffer.StreamCharBuffer;
import org.grails.gsp.CompileStaticGroovyPage;
import org.grails.gsp.GroovyPage;
import org.grails.gsp.ModelRecordingGroovyPage;
import org.grails.gsp.compiler.tags.GrailsTagRegistry;
import org.grails.gsp.compiler.tags.GroovySyntaxTag;
import org.grails.io.support.SpringIOUtils;
import org.grails.taglib.GrailsTagException;
import org.grails.taglib.encoder.OutputEncodingSettings;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOTE: Based on work done by the GSP standalone project (https://gsp.dev.java.net/).
 *
 * Parsing implementation for GSP files
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 * @author Lari Hotari
 */
public class GroovyPageParser implements Tokens {

    public static final Log LOG = LogFactory.getLog(GroovyPageParser.class);

    private static final Pattern PARA_BREAK = Pattern.compile(
            "/p>\\s*<p[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_BREAK = Pattern.compile(
            "((/td>\\s*</tr>\\s*<)?tr[^>]*>\\s*<)?td[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_DIRECTIVE_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*(\"\"\"|\"(?!\"\")|'''|'(?!''))(.*?)\\2", Pattern.DOTALL);

    private static final String TAGLIB_DIRECTIVE = "taglib";

    private static final Pattern PRESCAN_PAGE_DIRECTIVE_PATTERN = Pattern.compile("<%@\\s*(?!" + TAGLIB_DIRECTIVE + " )(.*?)\\s*%>", Pattern.DOTALL);
    private static final Pattern PRESCAN_COMMENT_PATTERN = Pattern.compile("<%--.*?%>", Pattern.DOTALL);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    private static final Pattern NON_WHITESPACE_PATTERN = Pattern.compile("\\S");
    private static final Pattern IMPORT_SEMICOLON_PATTERN = Pattern.compile(";");

    public static final String CONSTANT_NAME_JSP_TAGS = "JSP_TAGS";
    public static final String CONSTANT_NAME_CONTENT_TYPE = "CONTENT_TYPE";
    public static final String CONSTANT_NAME_LAST_MODIFIED = "LAST_MODIFIED";
    public static final String CONSTANT_NAME_EXPRESSION_CODEC = "EXPRESSION_CODEC";
    public static final String CONSTANT_NAME_STATIC_CODEC = "STATIC_CODEC";
    public static final String CONSTANT_NAME_OUT_CODEC = "OUT_CODEC";
    public static final String CONSTANT_NAME_TAGLIB_CODEC = "TAGLIB_CODEC";
    public static final String CONSTANT_NAME_COMPILE_STATIC_MODE = "COMPILE_STATIC_MODE";
    public static final String CONSTANT_NAME_MODEL_FIELDS_MODE = "MODEL_FIELDS_MODE";
    public static final String DEFAULT_ENCODING = "UTF-8";

    private static final String MULTILINE_GROOVY_STRING_DOUBLEQUOTES="\"\"\"";
    private static final String MULTILINE_GROOVY_STRING_SINGLEQUOTES="'''";
    public static final String MODEL_DIRECTIVE = "model";
    public static final String COMPILE_STATIC_DIRECTIVE = "compileStatic";
    public static final String TAGLIBS_DIRECTIVE = "taglibs";
    public static final List<String> DEFAULT_TAGLIB_NAMESPACES = Collections.unmodifiableList(Arrays.asList(new String[]{"g", "tmpl", "f", "asset", "plugin"}));

    private GroovyPageScanner scan;
    private GSPWriter out;
    private String className;
    private String packageName;
    private String sourceName; // last segment of the file name (eg- index.gsp)
    private boolean finalPass = false;
    private int tagIndex;
    private Map<Object, Object> tagContext;
    private Stack<TagMeta> tagMetaStack = new Stack<TagMeta>();
    private GrailsTagRegistry tagRegistry = GrailsTagRegistry.getInstance();
    private Environment environment;
    private List<String> htmlParts = new ArrayList<String>();

    Set<Integer> bodyVarsDefined=new HashSet<Integer>();
    Map<Integer, String> attrsVarsMapDefinition=new HashMap<Integer, String>();

    int closureLevel=0;

    /*
     * Set to true when whitespace is currently being saved for later output if
     * the next tag isn't set to swallow it
     */
    private boolean currentlyBufferingWhitespace;

    /*
     * Set to true if the last output was not whitespace, so that we can detect
     * when a tag has illegal content before it
     */
    private boolean previousContentWasNonWhitespace;

    private StringBuffer whitespaceBuffer = new StringBuffer();

    private String contentType = DEFAULT_CONTENT_TYPE;
    private boolean doNextScan = true;
    private int state;
    private static final String DEFAULT_CONTENT_TYPE = "text/html;charset=UTF-8";
    private int constantCount = 0;
    private Map<String,Integer> constantsToNumbers = new HashMap<String,Integer>();

    private final String pageName;
    public static final String[] DEFAULT_IMPORTS = {
        "grails.plugins.metadata.GrailsPlugin",
        "org.grails.gsp.compiler.transform.LineNumber",
        "org.grails.gsp.GroovyPage",
        "org.grails.web.taglib.*",
        "org.grails.taglib.GrailsTagException",
        "org.springframework.web.util.*",
        "grails.util.GrailsUtil"
    };
    public static final String CONFIG_PROPERTY_DEFAULT_CODEC = "grails.views.default.codec";
    public static final String CONFIG_PROPERTY_GSP_ENCODING = "grails.views.gsp.encoding";
    public static final String CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR = "grails.views.gsp.keepgenerateddir";

    public static final String CONFIG_PROPERTY_GSP_COMPILESTATIC = "grails.views.gsp.compileStatic";
    public static final String CONFIG_PROPERTY_GSP_ALLOWED_TAGLIB_NAMESPACES = "grails.views.gsp.compileStaticConfig.taglibs";
    public static final String CONFIG_PROPERTY_GSP_CODECS = "grails.views.gsp.codecs";

    private static final String IMPORT_DIRECTIVE = "import";
    private static final String CONTENT_TYPE_DIRECTIVE = "contentType";
    public static final String CODEC_DIRECTIVE_POSTFIX = "Codec";
    private static final String EXPRESSION_CODEC_DIRECTIVE = OutputEncodingSettings.EXPRESSION_CODEC_NAME + CODEC_DIRECTIVE_POSTFIX;
    private static final String EXPRESSION_CODEC_DIRECTIVE_ALIAS = "default" + CODEC_DIRECTIVE_POSTFIX;
    private static final String STATIC_CODEC_DIRECTIVE = OutputEncodingSettings.STATIC_CODEC_NAME + CODEC_DIRECTIVE_POSTFIX;
    private static final String OUT_CODEC_DIRECTIVE = OutputEncodingSettings.OUT_CODEC_NAME + CODEC_DIRECTIVE_POSTFIX;
    private static final String TAGLIB_CODEC_DIRECTIVE = OutputEncodingSettings.TAGLIB_CODEC_NAME + CODEC_DIRECTIVE_POSTFIX;

    private String pluginAnnotation;
    public static final String GROOVY_SOURCE_CHAR_ENCODING = "UTF-8";
    private Map<String, String> jspTags = new HashMap<String, String>();
    private long lastModified;
    private boolean precompileMode;
    private Boolean compileStaticMode;
    private boolean modelFieldsMode;
    private String expressionCodecDirectiveValue = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.EXPRESSION_CODEC_NAME);
    private String outCodecDirectiveValue = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.OUT_CODEC_NAME);
    private String staticCodecDirectiveValue = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.STATIC_CODEC_NAME);
    private String taglibCodecDirectiveValue = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.TAGLIB_CODEC_NAME) ;
    private String modelDirectiveValue;

    private File keepGeneratedDirectory;
    private Set<String> allowedTaglibNamespaces = new LinkedHashSet<>(DEFAULT_TAGLIB_NAMESPACES);

    public String getContentType() {
        return contentType;
    }

    public int getCurrentOutputLineNumber() {
        return scan.getLineNumberForToken();
    }

    public Map<String, String> getJspTags() {
        return jspTags;
    }

    public void setKeepGeneratedDirectory(File keepGeneratedDirectory) {
        this.keepGeneratedDirectory = keepGeneratedDirectory;
    }


    class TagMeta {
        String name;
        String namespace;
        Object instance;
        boolean isDynamic;
        boolean hasAttributes;
        int lineNumber;
        boolean emptyTag;
        int tagIndex;
        boolean bufferMode=false;
        int bufferPartNumber = -1;

        @Override
        public String toString() {
            return "<" + namespace + ":" + name + ">";
        }
    }

    public GroovyPageParser(String name, String uri, String filename, InputStream in, String encoding, String expressionCodecName, ConfigMap configMap) throws IOException {
        this(name, uri, filename, readStream(in, encoding), expressionCodecName, configMap);
    }

    public GroovyPageParser(String name, String uri, String filename, String gspSource, ConfigMap configMap) throws IOException {
        this(name, uri, filename, gspSource, null, configMap);
    }

    public GroovyPageParser(String name, String uri, String filename, InputStream in, ConfigMap configMap) throws IOException {
        this(name, uri, filename, in, null, null, configMap);
    }

    public GroovyPageParser(String name, String uri, String filename, String gspSource, String expressionCodecName, ConfigMap configMap) throws IOException {
        this.expressionCodecDirectiveValue = expressionCodecName;
        if (expressionCodecDirectiveValue == null) {
            expressionCodecDirectiveValue = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.EXPRESSION_CODEC_NAME);
        }

        if (configMap != null) {
            configure(configMap);
        }

        scan = new GroovyPageScanner(gspSource, uri);
        pageName = uri;
        environment = Environment.getCurrent();
        makeName(name);
        makeSourceName(filename);
    }

    /**
     * Configures the parser for the given Config map
     *
     * @param config The config map
     */
    private void configure(ConfigMap config) {
        compileStaticMode = config.getProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_COMPILESTATIC, Boolean.class);

        Object allowedTagLibsConfigValue = config.getProperty(CONFIG_PROPERTY_GSP_ALLOWED_TAGLIB_NAMESPACES, Object.class);
        if (allowedTagLibsConfigValue instanceof Iterable) {
            for (Object val : ((Iterable) allowedTagLibsConfigValue)) {
                allowedTaglibNamespaces.add(String.valueOf(val).trim());
            }
        } else if (allowedTagLibsConfigValue instanceof CharSequence) {
            allowedTaglibNamespaces.addAll(Arrays.asList(allowedTagLibsConfigValue.toString().split("\\s*,\\s*")));
        }

        setExpressionCodecDirectiveValue(
                config.getProperty(OutputEncodingSettings.CONFIG_PROPERTY_GSP_CODECS + '.' + OutputEncodingSettings.EXPRESSION_CODEC_NAME, String.class,
                        config.getProperty( OutputEncodingSettings.CONFIG_PROPERTY_DEFAULT_CODEC, String.class, OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.EXPRESSION_CODEC_NAME) ))
        );

        setStaticCodecDirectiveValue(
                config.getProperty(OutputEncodingSettings.CONFIG_PROPERTY_GSP_CODECS + '.' + OutputEncodingSettings.STATIC_CODEC_NAME, String.class, OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.STATIC_CODEC_NAME ))
        );

        setTaglibCodecDirectiveValue(
                config.getProperty(OutputEncodingSettings.CONFIG_PROPERTY_GSP_CODECS + '.' + OutputEncodingSettings.TAGLIB_CODEC_NAME, String.class, OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.TAGLIB_CODEC_NAME ))
        );

        setOutCodecDirectiveValue(
                config.getProperty(OutputEncodingSettings.CONFIG_PROPERTY_GSP_CODECS + '.' + OutputEncodingSettings.OUT_CODEC_NAME, String.class, OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.OUT_CODEC_NAME ))
        );

        Object keepDirObj = config.getProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR, Object.class);
        if (keepDirObj instanceof File) {
            setKeepGeneratedDirectory((File) keepDirObj);
        }
        else if (keepDirObj != null) {
            setKeepGeneratedDirectory(new File(String.valueOf(keepDirObj)));
        }

    }

    private Map<String, String> parseDirectives(String gspSource) {
        Map <String, String> result=new HashMap<String, String>();
        // strip gsp comments
        String input = PRESCAN_COMMENT_PATTERN.matcher(gspSource).replaceAll("");
        // find page directives
        Matcher m=PRESCAN_PAGE_DIRECTIVE_PATTERN.matcher(input);
        if (m.find()) {
            Matcher mat = PAGE_DIRECTIVE_PATTERN.matcher(m.group(1));
            while (mat.find()) {
                String name = mat.group(1);
                String value = mat.group(3);
                result.put(name, value);
            }
        }
        return result;
    }

    public int[] getLineNumberMatrix() {
        return out.getLineNumbers();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public InputStream parse() {
        resolveKeepGeneratedDirectory();

        StreamCharBuffer streamBuffer = new StreamCharBuffer(1024);
        StreamByteBuffer byteOutputBuffer = new StreamByteBuffer(1024,
                StreamByteBuffer.ReadMode.RETAIN_AFTER_READING);

        try {
            streamBuffer.connectTo(new OutputStreamWriter(byteOutputBuffer.getOutputStream(),
                    GROOVY_SOURCE_CHAR_ENCODING), true);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Grails cannot run unless your environment supports UTF-8!");
        }

        File keepGeneratedFile = null;
        Writer keepGeneratedWriter = null;
        if (keepGeneratedDirectory != null) {
            keepGeneratedFile = new File(keepGeneratedDirectory, className);
            try {
                keepGeneratedWriter = new OutputStreamWriter(
                        new FileOutputStream(keepGeneratedFile),
                        GROOVY_SOURCE_CHAR_ENCODING);
            }
            catch (IOException e) {
                LOG.warn("Cannot open keepgenerated file for writing. File's absolute path is '" +
                        keepGeneratedFile.getAbsolutePath() + "'");
                keepGeneratedFile = null;
            }
            streamBuffer.connectTo(keepGeneratedWriter, true);
        }

        Writer target = streamBuffer.getWriter();
        try {
            generateGsp(target, false);
            return byteOutputBuffer.getInputStream();
        }
        finally {
            SpringIOUtils.closeQuietly(keepGeneratedWriter);
        }
    }

    private void resolveKeepGeneratedDirectory() {
        if (keepGeneratedDirectory != null && !keepGeneratedDirectory.isDirectory()) {
            LOG.warn("The directory specified with " + CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR +
                    " config parameter doesn't exist or isn't a readable directory. Absolute path: '" +
                    keepGeneratedDirectory.getAbsolutePath() + "' Keepgenerated will be disabled.");
            keepGeneratedDirectory = null;
        }
    }

    public void generateGsp(Writer target) {
        generateGsp(target, true);
    }

    public void generateGsp(Writer target, boolean precompileMode) {
        this.precompileMode = precompileMode;

        out = new GSPWriter(target, this);
        if (packageName != null && packageName.length() > 0) {
            out.println("package " + packageName);
            out.println();
        }
        page();
        finalPass = true;
        scan.reset();
        previousContentWasNonWhitespace = false;
        currentlyBufferingWhitespace = false;
        page();

        out.close();
        scan = null;
    }

    public void writeHtmlParts(File filename) throws IOException {
        DataOutputStream dataOut = null;
        try {
            dataOut = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(filename)));
            dataOut.writeInt(htmlParts.size());
            for (String part : htmlParts) {
                dataOut.writeUTF(part);
            }
        }
        finally {
            SpringIOUtils.closeQuietly(dataOut);
        }
    }

    public void writeLineNumbers(File filename) throws IOException {
        DataOutputStream dataOut = null;
        try {
            dataOut = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(filename)));
            int lineNumbersCount = out.getCurrentLineNumber() - 1;
            int[] lineNumbers = out.getLineNumbers();
            dataOut.writeInt(lineNumbersCount);
            for (int i = 0; i < lineNumbersCount; i++) {
                dataOut.writeInt(lineNumbers[i]);
            }
        }
        finally {
            SpringIOUtils.closeQuietly(dataOut);
        }
    }

    private void declare(boolean gsp) {
        if (finalPass) {
            return;
        }

        out.println();
        write(scan.getToken().trim(), gsp);
        out.println();
        out.println();
    }

    private void direct() {
        if (finalPass) {
            return;
        }

        String text = scan.getToken();
        text = text.trim();
        if (text.startsWith(TAGLIB_DIRECTIVE + " ")) {
            directJspTagLib(text);
        } else {
            directPage(text);
        }
    }

    private void directPage(String text) {

        text = text.trim();
        Matcher mat = PAGE_DIRECTIVE_PATTERN.matcher(text);
        Boolean compileStaticModeSetting = compileStaticMode;
        while (mat.find()) {
            String name = mat.group(1);
            String value = mat.group(3);
            if (name.equals(IMPORT_DIRECTIVE)) {
                pageImport(value);
            }
            if (name.equalsIgnoreCase(CONTENT_TYPE_DIRECTIVE)) {
                contentType(value);
            }
            if (name.equalsIgnoreCase(EXPRESSION_CODEC_DIRECTIVE)) {
                expressionCodecDirectiveValue = value.trim();
            }
            if (name.equalsIgnoreCase(EXPRESSION_CODEC_DIRECTIVE_ALIAS)) {
                expressionCodecDirectiveValue = value.trim();
            }
            if (name.equalsIgnoreCase(STATIC_CODEC_DIRECTIVE)) {
                staticCodecDirectiveValue = value.trim();
            }
            if (name.equalsIgnoreCase(OUT_CODEC_DIRECTIVE)) {
                outCodecDirectiveValue = value.trim();
            }
            if (name.equalsIgnoreCase(TAGLIB_CODEC_DIRECTIVE)) {
                taglibCodecDirectiveValue = value.trim();
            }
            if (name.equalsIgnoreCase(MODEL_DIRECTIVE)) {
                if (modelDirectiveValue != null) {
                    modelDirectiveValue += "\n" + value.trim();
                } else {
                    modelDirectiveValue = value.trim();
                }
                modelFieldsMode = true;
                if (compileStaticModeSetting == null) {
                    compileStaticModeSetting = true;
                }
            }
            if (name.equalsIgnoreCase(COMPILE_STATIC_DIRECTIVE)) {
                compileStaticModeSetting = GrailsStringUtils.toBoolean(value.trim());
            }
            if (name.equalsIgnoreCase(TAGLIBS_DIRECTIVE)) {
                allowedTaglibNamespaces.addAll(Arrays.asList(value.trim().split("\\s*,\\s*")));
            }
        }
        this.compileStaticMode = compileStaticModeSetting != null ? compileStaticModeSetting : false;
    }

    private void directJspTagLib(String text) {

        text = text.substring(TAGLIB_DIRECTIVE.length() + 1, text.length());
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        populateMapWithAttributes(attrs, text);

        String prefix = attrs.get("\"prefix\"");
        String uri = attrs.get("\"uri\"");

        if (uri != null && prefix != null) {

            final String namespace = prefix.substring(1, prefix.length() - 1);
            if (!GroovyPage.DEFAULT_NAMESPACE.equals(namespace)) {
                jspTags.put(namespace, uri.substring(1, uri.length() - 1));
            }
            else {
                LOG.error("You cannot override the default 'g' namespace with the directive <%@ taglib prefix=\"g\" %>. Please select another namespace.");
            }
        }
    }

    private void contentType(String value) {
        contentType = value;
    }

    private void scriptletExpr() {
        if (!finalPass) {
            return;
        }

        String text = scan.getToken().trim();
        out.printlnToResponse(text);
    }

    private void expr() {
        if (!finalPass) return;

        String text = scan.getToken().trim();
        text = getExpressionText(text);
        if (text != null && text.length() > 2 && text.startsWith("(") && text.endsWith(")")) {
            out.printlnToResponse(GroovyPage.EXPRESSION_OUT_STATEMENT, text.substring(1,text.length()-1));
        } else {
            out.printlnToResponse(GroovyPage.EXPRESSION_OUT_STATEMENT, text);
        }
    }

    /**
     * Returns an expression text for the given expression
     *
     * @param text
     *            The text
     * @return An expression text
     */
    public String getExpressionText(String text) {
        return getExpressionText(text,true);
    }

    public String getExpressionText(String text, boolean _safeDereference) {
        boolean safeDereference = false;
        if (text.endsWith("?")) {
            text = text.substring(0, text.length() - 1);
            safeDereference = _safeDereference;
        }
        if (!precompileMode && !isCompileStaticMode() &&
                (environment == Environment.DEVELOPMENT || environment == Environment.TEST)) {
            String escaped = escapeGroovy(text);
            text = "evaluate('" + escaped + "', " +
                    getCurrentOutputLineNumber() + ", it) { return " + text +
                    " }" + (safeDereference ? "?" : "");
        }
        else {
            // add extra parenthesis, see http://jira.codehaus.org/browse/GRAILS-4351
            // or GroovyPagesTemplateEngineTests.testForEachInProductionMode

            text = "(" + text + ")"+ (safeDereference ? "?" : "");
        }
        return text;
    }

    private String escapeGroovy(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Write to the outputstream ONLY if the string is not blank, else we hold
     * it back in case it is to be swallowed between tags
     */
    @SuppressWarnings("unused")
    private void bufferedPrintlnToResponse(String s) {
        if (currentlyBufferingWhitespace) {
            whitespaceBuffer.append(s);
        }
        else {
            flushTagBuffering();
            out.printlnToResponse(s);
        }
    }

    private void htmlPartPrintlnToResponse(int partNumber) {
        if (!tagMetaStack.isEmpty()) {
            TagMeta tm = tagMetaStack.peek();
            if (tm.bufferMode && tm.bufferPartNumber == -1) {
                tm.bufferPartNumber = partNumber;
                return;
            }
        }

        flushTagBuffering();

        htmlPartPrintlnRaw(partNumber);
    }

    private void htmlPartPrintlnRaw(int partNumber) {
        out.print("printHtmlPart(");
        out.print(String.valueOf(partNumber));
        out.print(")");
        out.println();
    }

    public void flushTagBuffering() {
        if (!tagMetaStack.isEmpty()) {
            TagMeta tm = tagMetaStack.peek();
            if (tm.bufferMode) {
                writeTagBodyStart(tm);
                if (tm.bufferPartNumber != -1) {
                    htmlPartPrintlnRaw(tm.bufferPartNumber);
                }
                tm.bufferMode = false;
            }
        }
    }



    private void html() {
        if (!finalPass) return;

        String text = scan.getToken();
        if (text.length() == 0) {
            return;
        }

        // If we detect it is all whitespace, we need to keep it for later
        // If it is not whitespace, we need to flush any whitespace we do have
        boolean contentIsWhitespace = !NON_WHITESPACE_PATTERN.matcher(text).find();
        if (!contentIsWhitespace && currentlyBufferingWhitespace) {
            flushBufferedWhiteSpace();
        }
        else {
            currentlyBufferingWhitespace = contentIsWhitespace;
        }
        // We need to know if the last content output was not whitespace, for tag safety checks
        previousContentWasNonWhitespace = !contentIsWhitespace;

        if (currentlyBufferingWhitespace) {
            whitespaceBuffer.append(text);
        }
        else {
            appendHtmlPart(text);
        }
    }

    private void appendHtmlPart(String text) {
        // flush previous white space if any
        if (whitespaceBuffer.length() > 0) {
            if (text != null) {
                whitespaceBuffer.append(text);
            }
            text = whitespaceBuffer.toString();
            clearBufferedWhiteSpace();
        }

        // de-dupe constants
        Integer constantNumber = constantsToNumbers.get(text);
        if (constantNumber == null) {
            constantNumber = constantCount++;
            constantsToNumbers.put(text, constantNumber);
            htmlParts.add(text);
        }
        htmlPartPrintlnToResponse(constantNumber);
    }

    private void makeName(String uri) {
        String name;
        int slash = uri.lastIndexOf('/');
        if (slash > -1) {
            name = uri.substring(slash + 1);
            uri = uri.substring(0, (uri.length() - 1) - name.length());
            while (uri.endsWith("/")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            slash = uri.lastIndexOf('/');
            if (slash > -1) {
                name = uri.substring(slash + 1) + '_' + name;
            }
        }
        else {
            name = uri;
        }
        StringBuilder buf = new StringBuilder(name.length());
        for (int ix = 0, ixz = name.length(); ix < ixz; ix++) {
            char c = name.charAt(ix);
            if (c < '0' || (c > '9' && c < '@') || (c > 'Z' && c < '_') ||
                    (c > '_' && c < 'a') || c > 'z') {
                c = '_';
            }
            else if (ix == 0 && c >= '0' && c <= '9') {
                c = '_';
            }
            buf.append(c);
        }
        className = buf.toString();
    }

    /**
     * find the simple name of this gsp
     * @param filename the fully qualified file name
     */
    private void makeSourceName(String filename) {
        if (filename != null) {
            int lastSegmentStart = filename.lastIndexOf('/');
            if (lastSegmentStart == -1) {
                lastSegmentStart = filename.lastIndexOf('\\');
            }
            sourceName = filename.substring(lastSegmentStart + 1);
        } else {
            sourceName = className;
        }
    }

    private static boolean match(CharSequence pat, CharSequence text, int start) {
        int ix = start, ixz = text.length(), ixy = start + pat.length();
        if (ixz > ixy) {
            ixz = ixy;
        }
        if (pat.length() > ixz - start) {
            return false;
        }

        for (; ix < ixz; ix++) {
            if (Character.toLowerCase(text.charAt(ix)) != Character.toLowerCase(pat.charAt(ix - start))) {
                return false;
            }
        }
        return true;
    }

    private static int match(Pattern pat, CharSequence text, int start) {
        Matcher mat = pat.matcher(text);
        if (mat.find(start) && mat.start() == start) {
            return mat.end();
        }
        return 0;
    }

    private void page() {

        if (finalPass) {
            out.println();
            if (pluginAnnotation != null) {
                out.println(pluginAnnotation);
            }
            if (isCompileStaticMode()) {
                out.println("@groovy.transform.CompileStatic(extensions = ['" + GroovyPageTypeCheckingExtension.class.getName() + "'])");
                if (allowedTaglibNamespaces != null && !allowedTaglibNamespaces.isEmpty()) {
                    out.println("@" + GroovyPageTypeCheckingConfig.class.getName() + "(taglibs = ['" + DefaultGroovyMethods.join((Iterable) allowedTaglibNamespaces, "','") + "'])");
                }
            }
            out.print("class ");
            out.print(className);
            out.print(" extends ");
            out.print(resolveGspSuperClassName());
            out.println(" {");
            if(modelDirectiveValue != null) {
                out.println("// start model fields");
                out.println(modelDirectiveValue);
                out.println("// end model fields");
            }
            out.println("public String getGroovyPageFileName() { \"" +
                    pageName.replaceAll("\\\\", "/") + "\" }");
            out.println("public Object run() {");
            /*
            out.println("def params = binding.params");
            out.println("def request = binding.request");
            out.println("def flash = binding.flash");
            out.println("def response = binding.response");
            */
            out.println("Writer " + GroovyPage.OUT + " = getOut()");
            out.println("Writer " + GroovyPage.EXPRESSION_OUT + " = getExpressionOut()");
            //out.println("JspTagLib jspTag");
        }

        loop: for (;;) {
            if (doNextScan) {
                state = scan.nextToken();
            }
            else {
                doNextScan = true;
            }

            // Flush any buffered whitespace if there's not a possibility of more whitespace
            // or a new tag which will handle flushing as necessary
            if ((state != GSTART_TAG) && (state != HTML)) {
                flushBufferedWhiteSpace();
                previousContentWasNonWhitespace = false; // well, we don't know
            }

            switch (state) {
                case EOF:
                    break loop;
                case HTML:
                    html();
                    break;
                case JEXPR:
                    scriptletExpr();
                    break;
                case JSCRIPT:
                    script(false);
                    break;
                case JDIRECT:
                    direct();
                    break;
                case JDECLAR:
                    declare(false);
                    break;
                case GEXPR:
                    expr();
                    break;
                case GSCRIPT:
                    script(true);
                    break;
                case GDIRECT:
                    direct();
                    break;
                case GDECLAR:
                    declare(true);
                    break;
                case GSTART_TAG:
                    startTag();
                    break;
                case GEND_EMPTY_TAG:
                case GEND_TAG:
                    endTag();
                    break;
            }
        }

        if (finalPass) {
            if (!tagMetaStack.isEmpty()) {
                throw new GrailsTagException("Grails tags were not closed! [" +
                        tagMetaStack + "] in GSP " + pageName + "", pageName,
                        getCurrentOutputLineNumber());
            }

            out.println("}");

            out.println("public static final Map " + CONSTANT_NAME_JSP_TAGS + " = new HashMap()");
            if (jspTags != null && jspTags.size() > 0) {
                out.println("static {");
                for (Map.Entry<String, String> entry : jspTags.entrySet()) {
                    out.print("\t" + CONSTANT_NAME_JSP_TAGS + ".put('");
                    out.print(escapeGroovy(entry.getKey()));
                    out.print("','");
                    out.print(escapeGroovy(entry.getValue()));
                    out.println("')");
                }
                out.println("}");
            }

            out.println("protected void init() {");
            out.println("\tthis.jspTags = " + CONSTANT_NAME_JSP_TAGS);
            out.println("}");

            out.println("public static final String " +
                    CONSTANT_NAME_CONTENT_TYPE + " = '" +
                    escapeGroovy(contentType) + "'");

            out.println("public static final long " +
                    CONSTANT_NAME_LAST_MODIFIED + " = " + lastModified + "L");

            out.println("public static final String " +
                    CONSTANT_NAME_EXPRESSION_CODEC + " = '" + escapeGroovy(expressionCodecDirectiveValue) + "'");
            out.println("public static final String " +
                    CONSTANT_NAME_STATIC_CODEC + " = '" + escapeGroovy(staticCodecDirectiveValue) + "'");
            out.println("public static final String " +
                    CONSTANT_NAME_OUT_CODEC + " = '" + escapeGroovy(outCodecDirectiveValue) + "'");
            out.println("public static final String " +
                    CONSTANT_NAME_TAGLIB_CODEC + " = '" + escapeGroovy(taglibCodecDirectiveValue) + "'");

            if(isCompileStaticMode()) {
                out.println("public static final boolean " +
                        CONSTANT_NAME_COMPILE_STATIC_MODE + " = " + isCompileStaticMode());
            }
            if(modelFieldsMode) {
                out.println("public static final boolean " +
                        CONSTANT_NAME_MODEL_FIELDS_MODE + " = " + modelFieldsMode);
            }

            out.println("}");

            if (shouldAddLineNumbers()) {
                addLineNumbers();
            }
        }
        else {
            for (int i = 0; i < DEFAULT_IMPORTS.length; i++) {
                out.print("import ");
                out.println(DEFAULT_IMPORTS[i]);
            }
        }
    }

    private String resolveGspSuperClassName() {
        Class<?> gspSuperClass =  isCompileStaticMode() ? CompileStaticGroovyPage.class : (isModelRecordingModeEnabled() ? ModelRecordingGroovyPage.class : GroovyPage.class);
        return gspSuperClass.getName();
    }

    private boolean isModelRecordingModeEnabled() {
        return ModelRecordingGroovyPage.ENABLED;
    }

    /**
     * Determines if the line numbers array should be added to the generated Groovy class.
     * @return true if they should
     */
    private boolean shouldAddLineNumbers() {
        try {
            // for now, we support this through a system property.
            return Boolean.valueOf(System.getenv("GROOVY_PAGE_ADD_LINE_NUMBERS"));
        } catch (Exception e) {
            // something wild happened
            return false;
        }
    }

    /**
     * Adds the line numbers array to the end of the generated Groovy ModuleNode
     * in a way suitable for the LineNumberTransform AST transform to operate on it
     */
    private void addLineNumbers() {
        out.println();
        out.println("@LineNumber(");
        out.print("\tlines = [");
        // get the line numbers here.  this will mean that the last 2 lines will not be captured in the
        // line number information, but that's OK since a user cannot set a breakpoint there anyway.
        int[] lineNumbers = filterTrailing0s(out.getLineNumbers());

        for (int i = 0; i < lineNumbers.length; i++) {
            out.print(lineNumbers[i]);
            if (i < lineNumbers.length - 1) {
                out.print(", ");
            }
        }
        out.println("],");
        out.println("\tsourceName = \"" + sourceName + "\"");
        out.println(")");
        out.println("class ___LineNumberPlaceholder { }");
    }

    /**
     * Filters trailing 0s from the line number array
     * @param lineNumbers the line number array
     * @return a new array that removes all 0s from the end of it
     */
    private int[] filterTrailing0s(int[] lineNumbers) {
        int startLocation = lineNumbers.length - 1;
        for (int i = lineNumbers.length -1; i >= 0; i--) {
            if (lineNumbers[i] > 0) {
                startLocation = i + 1;
                break;
            }
        }

        int[] newLineNumbers = new int[startLocation];
        System.arraycopy(lineNumbers, 0, newLineNumbers, 0, startLocation);
        return newLineNumbers;
    }

    private void endTag() {
        if (!finalPass) return;

        String tagName = scan.getToken().trim();
        String ns = scan.getNamespace();

        if (tagMetaStack.isEmpty())
            throw new GrailsTagException(
                    "Found closing Grails tag with no opening [" + tagName + "]", pageName,
                    getCurrentOutputLineNumber());

        TagMeta tm = tagMetaStack.pop();
        String lastInStack = tm.name;
        String lastNamespaceInStack = tm.namespace;

        // if the tag name is blank then it has been closed by the start tag ie <tag />
        if (GrailsStringUtils.isBlank(tagName)) {
            tagName = lastInStack;
        }

        if (!lastInStack.equals(tagName) || !lastNamespaceInStack.equals(ns)) {
            throw new GrailsTagException("Grails tag [" + lastNamespaceInStack +
                    ":" + lastInStack + "] was not closed", pageName, getCurrentOutputLineNumber());
        }

        if (GroovyPage.DEFAULT_NAMESPACE.equals(ns) && tagRegistry.isSyntaxTag(tagName)) {
            if (tm.instance instanceof GroovySyntaxTag) {
                GroovySyntaxTag tag = (GroovySyntaxTag) tm.instance;
                tag.doEndTag();
            }
            else {
                throw new GrailsTagException("Grails tag [" + tagName +
                        "] was not closed", pageName,
                        getCurrentOutputLineNumber());
            }
        }
        else {
            int bodyTagIndex = -1;
            if (!tm.emptyTag && !tm.bufferMode) {
                bodyTagIndex = tagIndex;
                out.println("})");
                closureLevel--;
            }

            if (tm.bufferMode && tm.bufferPartNumber != -1) {
                if (!bodyVarsDefined.contains(tm.tagIndex)) {
                    //out.print("def ");
                    bodyVarsDefined.add(tm.tagIndex);
                }
                out.println("createClosureForHtmlPart(" + tm.bufferPartNumber + ", " + tm.tagIndex + ")");
                bodyTagIndex = tm.tagIndex;
                tm.bufferMode = false;
            }

            if (jspTags.containsKey(ns)) {
                String uri = jspTags.get(ns);
                out.println("jspTag = getJspTag('" + uri + "', '" + tagName + "')");
                out.println("if (!jspTag) throw new GrailsTagException('Unknown JSP tag " +
                        ns + ":" + tagName + "')");
                out.print("jspTag.doTag(out," + attrsVarsMapDefinition.get(tagIndex) + ",");
                if (bodyTagIndex > -1) {
                    out.print("getBodyClosure(" + bodyTagIndex + ")");
                } else {
                    out.print("null");
                }
                out.println(")");
            }
            else {
                if (tm.hasAttributes) {
                    out.println("invokeTag('" + tagName + "','" + ns + "'," +
                            getCurrentOutputLineNumber() + "," + attrsVarsMapDefinition.get(tagIndex) +
                            "," + bodyTagIndex + ")");
                }
                else {
                    out.println("invokeTag('" + tagName + "','" + ns + "'," +
                            getCurrentOutputLineNumber() + ",[:]," + bodyTagIndex + ")");
                }
            }
        }

        tm.bufferMode = false;

        tagIndex--;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void startTag() {
        if (!finalPass) return;

        tagIndex++;

        String text;
        StringBuilder buf = new StringBuilder(scan.getToken());
        String ns = scan.getNamespace();

        boolean emptyTag = false;

        state = scan.nextToken();
        while (state != HTML && state != GEND_TAG && state != GEND_EMPTY_TAG && state != EOF) {
            if (state == GTAG_EXPR) {
                buf.append("${");
                buf.append(scan.getToken().trim());
                buf.append("}");
            }
            else {
                buf.append(scan.getToken());
            }
            state = scan.nextToken();
        }
        if (state == GEND_EMPTY_TAG) {
            emptyTag = true;
        }

        doNextScan = false;

        text = buf.toString();

        String tagName;
        Map attrs = new LinkedHashMap();

        Matcher m=WHITESPACE_PATTERN.matcher(text);

        if (m.find()) { // ignores carriage returns and new lines
            tagName = text.substring(0, m.start());
            if (state != EOF) {
                String attrTokens = text.substring(m.start(), text.length());
                populateMapWithAttributes(attrs, attrTokens);
            }
        } else {
            tagName = text;
        }

        if (state == EOF) {
            throw new GrailsTagException(
                    "Unexpected end of file encountered parsing Tag [" + tagName + "] for " + className +
                    ". Are you missing a closing brace '}'?", pageName,
                    getCurrentOutputLineNumber());
        }

        flushTagBuffering();

        TagMeta tm = new TagMeta();
        tm.name = tagName;
        tm.namespace = ns;
        tm.hasAttributes = !attrs.isEmpty();
        tm.lineNumber = getCurrentOutputLineNumber();
        tm.emptyTag = emptyTag;
        tm.tagIndex = tagIndex;
        tagMetaStack.push(tm);

        if (GroovyPage.DEFAULT_NAMESPACE.equals(ns) && tagRegistry.isSyntaxTag(tagName)) {
            if (tagContext == null) {
                tagContext = new HashMap<Object, Object>();
                tagContext.put(GroovyPage.OUT, out);
                tagContext.put(GroovyPageParser.class, this);
            }
            GroovySyntaxTag tag = (GroovySyntaxTag) tagRegistry.newTag(tagName);
            tag.init(tagContext);
            tag.setAttributes(attrs);

            if (tag.isKeepPrecedingWhiteSpace() && currentlyBufferingWhitespace) {
                flushBufferedWhiteSpace();
            }
            else if (!tag.isAllowPrecedingContent() && previousContentWasNonWhitespace) {
                throw new GrailsTagException("Tag [" + tag.getName() +
                        "] cannot have non-whitespace characters directly preceding it.", pageName,
                        getCurrentOutputLineNumber());
            }
            else {
                // If tag does not specify buffering of WS, we swallow it here
                clearBufferedWhiteSpace();
            }

            tag.doStartTag();

            tm.instance = tag;
        }
        else {
            // Custom taglibs have to always flush the whitespace, there's no
            // "allowPrecedingWhitespace" property on tags yet
            flushBufferedWhiteSpace();

            if (attrs.size() > 0) {
                FastStringWriter buffer = new FastStringWriter();
                buffer.print("[");
                for (Iterator<?> i = attrs.keySet().iterator(); i.hasNext();) {
                    String name = (String) i.next();
                    String cleanedName=name;
                    if (name.startsWith("\"") && name.endsWith("\"")) {
                        cleanedName="'" + name.substring(1,name.length()-1) + "'";
                    }
                    buffer.print(cleanedName);
                    buffer.print(':');

                    buffer.print(getExpressionText(attrs.get(name).toString()));
                    if (i.hasNext()) {
                        buffer.print(',');
                    }
                    else {
                        buffer.print("]");
                    }
                }
                attrsVarsMapDefinition.put(tagIndex, buffer.toString());
                buffer.close();
            }

            if (!emptyTag) {
                tm.bufferMode = true;
            }
        }
    }

    private void writeTagBodyStart(TagMeta tm) {
        if (tm.bufferMode) {
            tm.bufferMode = false;
            if (!bodyVarsDefined.contains(tm.tagIndex)) {
                //out.print("def ");
                bodyVarsDefined.add(tm.tagIndex);
            }
            out.println("createTagBody(" + tm.tagIndex + ", {->");
            closureLevel++;
        }
    }

    private void clearBufferedWhiteSpace() {
        whitespaceBuffer.delete(0, whitespaceBuffer.length());
        currentlyBufferingWhitespace = false;
    }

    // Write out any whitespace we saved between tags
    private void flushBufferedWhiteSpace() {
        if (currentlyBufferingWhitespace) {
            appendHtmlPart(null);
        }
        currentlyBufferingWhitespace = false;
    }

    private void populateMapWithAttributes(Map<String, String> attrs, String attrTokens) {
        attrTokens = attrTokens.trim();
        int startPos=0;
        while(startPos < attrTokens.length()) {
            // parse name (before '=' character)
            int equalsignPos = attrTokens.indexOf('=', startPos);
            if (equalsignPos == -1) {
                throw new GrailsTagException("Expecting '=' after attribute name (" + attrTokens + ").", pageName, getCurrentOutputLineNumber());
            }
            String name = attrTokens.substring(startPos, equalsignPos).trim();

            // parse value
            startPos = equalsignPos + 1;
            char ch = attrTokens.charAt(startPos++);
            while(Character.isWhitespace(ch) && startPos < attrTokens.length()) {
                ch = attrTokens.charAt(startPos++);
            }
            if (!(ch=='\'' || ch=='"')) {
                throw new GrailsTagException("Attribute value must be quoted (" + attrTokens + ").", pageName, getCurrentOutputLineNumber());
            }
            char quoteChar = ch;

            GroovyPageExpressionParser expressionParser = new GroovyPageExpressionParser(attrTokens, startPos, quoteChar, (char)0, false);
            int endQuotepos = expressionParser.parse();
            if (endQuotepos==-1) {
                throw new GrailsTagException("Attribute value quote wasn't closed (" + attrTokens + ").", pageName, getCurrentOutputLineNumber());
            }

            String val=attrTokens.substring(startPos, endQuotepos);

            if (val.startsWith("${") && val.endsWith("}") && !expressionParser.isContainsGstrings()) {
                val = val.substring(2, val.length() - 1);
            }
            else if (!(val.startsWith("[") && val.endsWith("]"))) {
                if (val.indexOf('"')==-1) {
                    quoteChar = '"';
                }
                String quoteStr;
                // use multiline groovy string if the value contains newlines
                if (val.indexOf('\n') != -1 || val.indexOf('\r') != -1) {
                    if (quoteChar=='"') {
                        quoteStr = MULTILINE_GROOVY_STRING_DOUBLEQUOTES;
                    } else {
                        quoteStr = MULTILINE_GROOVY_STRING_SINGLEQUOTES;
                    }
                } else {
                    quoteStr = String.valueOf(quoteChar);
                }
                val = quoteStr + val + quoteStr;
            }
            attrs.put("\"" + name + "\"", val);
            startPos = endQuotepos + 1;
        }
    }

    private void pageImport(String value) {
        String[] imports = IMPORT_SEMICOLON_PATTERN.split(value.subSequence(0, value.length()));
        for (int ix = 0; ix < imports.length; ix++) {
            out.print("import ");
            out.print(imports[ix]);
            out.println();
        }
    }

    private static String readStream(InputStream in, String gspEncoding) throws IOException {
        if (gspEncoding == null) {
        	gspEncoding  = DEFAULT_ENCODING;
        }
        return IOUtils.toString(in, gspEncoding);
    }

    public static String getGspEncoding(){
        return DEFAULT_ENCODING;
    }

    private void script(boolean gsp) {
        flushTagBuffering();
        if (!finalPass) return;

        out.println();
        write(scan.getToken().trim(), gsp);
        out.println();
        out.println();
    }

    private void write(CharSequence text, boolean gsp) {
        if (!gsp) {
            out.print(text);
            return;
        }

        for (int ix = 0, ixz = text.length(); ix < ixz; ix++) {
            char c = text.charAt(ix);
            String rep = null;
            if (Character.isWhitespace(c)) {
                for (ix++; ix < ixz; ix++) {
                    if (Character.isWhitespace(text.charAt(ix))) {
                        continue;
                    }
                    ix--;
                    rep = " ";
                    break;
                }
            }
            else if (c == '&') {
                if (match("&semi;", text, ix)) {
                    rep = ";";
                    ix += 5;
                }
                else if (match("&amp;", text, ix)) {
                    rep = "&";
                    ix += 4;
                }
                else if (match("&lt;", text, ix)) {
                    rep = "<";
                    ix += 3;
                }
                else if (match("&gt;", text, ix)) {
                    rep = ">";
                    ix += 3;
                }
            }
            else if (c == '<') {
                if (match("<br>", text, ix) || match("<hr>", text, ix)) {
                    rep = "\n";
                    //incrementLineNumber();
                    ix += 3;
                }
                else {
                    int end = match(PARA_BREAK, text, ix);
                    if (end <= 0)
                        end = match(ROW_BREAK, text, ix);
                    if (end > 0) {
                        rep = "\n";
                        //incrementLineNumber();
                        ix = end;
                    }
                }
            }
            if (rep != null) {
                out.print(rep);
            }
            else {
                out.print(c);
            }
        }
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public List<String> getHtmlParts() {
        return htmlParts;
    }

    public String[] getHtmlPartsArray() {
        return htmlParts.toArray(new String[htmlParts.size()]);
    }

    public boolean isInClosure() {
        return closureLevel > 0;
    }

    public String getExpressionCodecDirectiveValue() {
        return expressionCodecDirectiveValue;
    }

    public String getPageName() {
        return pageName;
    }

    public String getOutCodecDirectiveValue() {
        return outCodecDirectiveValue;
    }

    public String getStaticCodecDirectiveValue() {
        return staticCodecDirectiveValue;
    }

    public String getTaglibCodecDirectiveValue() {
        return taglibCodecDirectiveValue;
    }

    public void setTaglibCodecDirectiveValue(String taglibCodecDirectiveValue) {
        this.taglibCodecDirectiveValue = taglibCodecDirectiveValue;
    }

    public void setExpressionCodecDirectiveValue(String expressionCodecDirectiveValue) {
        this.expressionCodecDirectiveValue = expressionCodecDirectiveValue;
    }

    public void setOutCodecDirectiveValue(String outCodecDirectiveValue) {
        this.outCodecDirectiveValue = outCodecDirectiveValue;
    }

    public void setStaticCodecDirectiveValue(String staticCodecDirectiveValue) {
        this.staticCodecDirectiveValue = staticCodecDirectiveValue;
    }

    public boolean isCompileStaticMode() {
        return compileStaticMode != null ? compileStaticMode : false;
    }

    public boolean isModelFieldsMode() {
        return modelFieldsMode;
    }
}
