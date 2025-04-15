/* Copyright 2004-2005 the original author or authors.
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

package grails.doc

import grails.doc.asciidoc.AsciiDocEngine
import grails.doc.internal.*
import groovy.io.FileType
import groovy.text.Template

import org.apache.commons.logging.LogFactory
import org.radeox.api.engine.WikiRenderEngine
import org.radeox.engine.context.BaseInitialRenderContext
import org.radeox.engine.context.BaseRenderContext
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import groovy.ant.AntBuilder

/**
 * Coordinated the DocEngine the produce documentation based on the gdoc format.
 *
 * @see DocEngine
 *
 * @author Graeme Rocher
 * @since 1.2
 */
class DocPublisher {
    static final String TOC_FILENAME = "toc.yml"

    /** The source directory of the documentation */
    File src
    /** The target directory to publish to */
    File target
    /** The temporary work directory */
    File workDir
    /** Directory containing the project's API documentation. */
    File apiDir
    /** The directory containing any images to use (will override defaults) **/
    File images
    /** The directory containing any CSS to use (will override defaults) **/
    File css
    /** The directory containing any fonts to use (will override defaults) **/
    File fonts
    /** The directory containing any Javascript to use (will override defaults) **/
    File js
    /** The directory cotnaining any templates to use (will override defaults) **/
    File style
    /**
     * The properties fie to populate the engine properties from
     */
    File propertiesFile
    /** The AntBuilder instance to use */
    AntBuilder ant
    /** The language we're generating for (gets its own sub-directory). Defaults to '' */
    String language = ""
    /** The encoding to use (default is UTF-8) */
    String encoding = "UTF-8"
    /** The title of the documentation */
    String title
    /** The subtitle of the documentation */
    String subtitle = ""
    /** The version of the documentation */
    String version
    /** The authors of the documentation */
    String authors = ""
    /** The translators of the documentation (if any) */
    String translators = ""
    /** The documentation license */
    String license = ""
    /** The copyright message */
    String copyright = ""
    /** The footer to include */
    String footer = ""
    /** HTML markup that renders the left logo */
    String logo
    /** HTML markup that renders the right logo */
    String sponsorLogo
    /**
     * The source repository
     */
    String sourceRepo

    /** Properties used to configure the DocEngine */
    Properties engineProperties

    boolean asciidoc = false

    def output
    private BaseRenderContext context
    private WikiRenderEngine engine
    private customMacros = []

    DocPublisher() {
        this(null, null)
    }

    DocPublisher(File src, File target, out = LogFactory.getLog(DocPublisher)) {
        this.src = src
        this.target = target
        this.output = out

        try {
            engineProperties.load(getClass().classLoader.getResourceAsStream("grails/doc/doc.properties"))
        }
        catch (e) {
            // ignore
        }
    }

    /** Returns the engine properties. */
    Properties getEngineProperties() { engineProperties }

    /** Sets the engine properties. Allows clients to override the defaults. */
    void setEngineProperties(Properties p) {
        engineProperties = p
    }

    /**
     * Registers a custom Radeox macro. If the macro has an 'initialContext'
     * property, it is set to the render context before first use.
     */
    void registerMacro(macro) {
        customMacros << macro
    }

    void publish() {
        // Adds encodeAsUrlPath(), encodeAsUrlFragment() and encodeAsHtml()
        // methods to String.
        use(StringEscapeCategory) {
            catPublish()
        }
    }

    private void catPublish() {
        initialize()
        if (!src?.exists()) {
            return
        }

        // unpack documentation resources
        String docResources = "${workDir}/doc-resources"
        ant.mkdir(dir: docResources)
        unpack(dest: docResources, src: "grails-doc-files.jar")

        def refDocsDir = calculateLanguageDir(target?.absolutePath ?: "./docs")
        def refGuideDir = new File(refDocsDir, "guide")
        def refPagesDir = "$refGuideDir/pages"

        ant.mkdir(dir: refDocsDir)
        ant.mkdir(dir: refGuideDir)
        ant.mkdir(dir: refPagesDir)
        ant.mkdir(dir: "$refDocsDir/ref")

        String imgsDir = new File(refDocsDir, calculatePathToResources("img")).path
        File fontsDir = new File(refDocsDir, calculatePathToResources("fonts"))
        ant.mkdir(dir: imgsDir)
        ant.mkdir(dir: fontsDir )
        String cssDir = new File(refDocsDir, calculatePathToResources("css")).path
        ant.mkdir(dir: cssDir)
        String jsDir = new File(refDocsDir, calculatePathToResources("js")).path
        ant.mkdir(dir: jsDir)
        ant.mkdir(dir: "${refDocsDir}/ref")

        ant.copy(todir: imgsDir, overwrite: true) {
            fileset(dir: "${docResources}/img")
        }

        if (images && images.exists()) {
            ant.copy(todir: imgsDir, overwrite: true, failonerror:false) {
                fileset(dir: images)
            }
        }

        ant.copy(todir: cssDir, overwrite: true) {
            fileset(dir: "${docResources}/css")
        }
        ant.copy(todir: fontsDir, overwrite: true) {
            fileset(dir: "${docResources}/fonts")
        }

        if (css && css.exists()) {
            ant.copy(todir: cssDir, overwrite: true, failonerror:false) {
                fileset(dir: css)
            }
        }
        if (fonts && fonts.exists()) {
            ant.copy(todir: fontsDir, overwrite: true, failonerror:false) {
                fileset(dir: fonts)
            }
        }
        ant.copy(todir: jsDir, overwrite: true) {
            fileset(dir: "${docResources}/js")
        }
        if (js && js.exists()) {
            ant.copy(todir: jsDir, overwrite: true, failonerror:false) {
                fileset(dir: js)
            }
        }
        if (style && style.exists()) {
            ant.copy(todir: "${docResources}/style", overwrite: true, failonerror:false) {
                fileset(dir: style)
            }
        }

        // Build the table of contents as a tree of nodes. We currently support
        // two strategies for this:
        //
        //  1. From a toc.yml file
        //  2. From the gdoc filenames
        //
        // The first strategy is used if the TOC file exists, otherwise we call
        // back to the old way of doing it, which means putting the section
        // numbers in the gdoc filenames.
        def guideSrcDir = new File(src, "guide")
        def yamlTocFile = new File(guideSrcDir, TOC_FILENAME)
        def guide
        def ext = asciidoc ? ".adoc" : ".gdoc"
        if (yamlTocFile.exists()) {
            def tocStrategy = new YamlTocStrategy(new FileResourceChecker(guideSrcDir), ext)
            guide = tocStrategy.generateToc(yamlTocFile)

            // A set of all gdoc files.
            def files = []
            def pattern = asciidoc ? ~/^.+\.adoc$/ : ~/^.+\.gdoc$/
            guideSrcDir.traverse(type: FileType.FILES, nameFilter: pattern) {
                // We need relative file paths with '/' separators, since those
                // are what are stored in the UserGuideNodes.
                files << (it.absolutePath - guideSrcDir.absolutePath)[1..-1].
                        replace(File.separator as char, '/' as char)
            }

            if (!verifyToc(guideSrcDir, files, guide)) {
                throw new RuntimeException("Encountered errors while building table of contents. Aborting.")
            }

            for (ch in guide.children) {
                overrideAliasesFromToc(ch)
            }
        }
        else {

            def files = guideSrcDir.listFiles()?.findAll { it.name.endsWith(ext) } ?: []
            guide = new LegacyTocStrategy().generateToc(files)
        }

        // When migrating from the old style docs to the new style, existing
        // external links that use URL fragment identifiers will break. To
        // mitigate against this problem, the user can provide a list of mappings
        // from the new fragment identifiers to the old ones. The docs will then
        // include both.
        def legacyLinksFile = new File(guideSrcDir, "links.yml")
        def legacyLinks = [:]
        if (legacyLinksFile.exists()) {
            legacyLinksFile.withInputStream { input ->
                legacyLinks = new Yaml(new SafeConstructor(new LoaderOptions())).load(input)
            }
        }

        def templateEngine = new groovy.text.SimpleTemplateEngine()

        // Reference menu items.
        def sectionFilter = { it.directory && !it.name.startsWith('.') } as FileFilter
        def files = new File(src, "ref").listFiles(sectionFilter)?.toList()?.sort() ?: []
        def refCategories = files.collect { f ->
            new Expando(
                    name: f.name,
                    usage: new File("${src}/ref/${f.name}$ext"),
                    sections: f.listFiles().findAll { it.name.endsWith(ext) }.sort())
        }

        def fullToc = new StringBuilder()

        def pathToRoot = ".."
        Map vars = new LinkedHashMap(engineProperties)
        vars.putAll(
            encoding: encoding,
            title: title,
            docTitle: title,
            subtitle: subtitle,
            footer: footer, // TODO - add a way to specify footer
            authors: authors,
            translators: translators,
            version: version,
            refMenu: refCategories,
            toc: guide,
            copyright: copyright,
            logo: injectPath(logo, pathToRoot),
            sponsorLogo: injectPath(sponsorLogo, pathToRoot),
            single: false,
            path: pathToRoot,
            resourcesPath: calculatePathToResources(pathToRoot),
            prev: null,
            next: null,
            legacyLinks: legacyLinks,
            sourceRepo: sourceRepo,
        )

        if(engine instanceof AsciiDocEngine) {
            // pass attributes to asciidoc
            ((AsciiDocEngine)engine).attributes.putAll(
                    version: version,
                    apiDocs: "https://docs.grails.org/${version}/api/",
                    sourceRepo: sourceRepo
            )
            ((AsciiDocEngine)engine).attributes.putAll(
                    engineProperties
            )
        }


        // Build the user guide sections first.
        def template = templateEngine.createTemplate(new File("${docResources}/style/guideItem.html").newReader(encoding))
        def sectionTemplate = templateEngine.createTemplate(new File("${docResources}/style/section.html").newReader(encoding))
        def fullContents = new StringBuilder()

        def chapterVars
        def chapters = guide.children
        chapters.eachWithIndex{ chapter, i ->
            chapterVars = [*:vars, chapterNumber: i + 1]
            if (i != 0) {
                chapterVars['prev'] = chapters[i - 1]
            }
            if (i != (chapters.size() - 1)) {
                chapterVars['next'] = chapters[i + 1]
            }
            chapterVars.sectionNumber = (i + 1).toString()
            writeChapter(chapter, template, sectionTemplate, guideSrcDir, refGuideDir.path, fullContents, chapterVars)
        }

        files = new File("${src}/ref").listFiles()?.toList()?.sort() ?: []
        def reference = [:]
        template = templateEngine.createTemplate(new File("${docResources}/style/referenceItem.html").newReader(encoding))

        pathToRoot = "../.."
        vars.logo = injectPath(logo, pathToRoot)
        vars.sponsorLogo = injectPath(sponsorLogo, pathToRoot)
        vars.path = pathToRoot
        vars.resourcesPath = calculatePathToResources(pathToRoot)

        // Generate the reference section of the guide.
        for (f in files) {
            if (f.directory && !f.name.startsWith(".")) {
                def section = f.name
                vars.section = section

                new File("${refDocsDir}/ref/${section}").mkdirs()
                def textiles = f.listFiles().findAll { it.name.endsWith(ext)}.sort()
                def usageFile = new File("${src}/ref/${section}${ext}")
                if (usageFile.exists()) {
                    def data = usageFile.getText("UTF-8")
                    context.set(DocEngine.SOURCE_FILE, usageFile)
                    context.set(DocEngine.CONTEXT_PATH, pathToRoot)
                    context.set(DocEngine.API_CONTEXT_PATH, vars.resourcesPath)
                    output.warn "Rendering document file $usageFile.name"
                    vars.content = engine.render(data, context)
                    vars.sourcePath = "ref/$usageFile.name"
                    new File("${refDocsDir}/ref/${section}/Usage.html").withWriter(encoding) {out ->
                        template.make(vars).writeTo(out)
                    }
                }
                for (txt in textiles) {
                    def name = txt.name[0..-6]
                    def data = txt.getText("UTF-8")
                    context.set(DocEngine.SOURCE_FILE, txt.name)
                    context.set(DocEngine.CONTEXT_PATH, pathToRoot)
                    context.set(DocEngine.API_CONTEXT_PATH, vars.resourcesPath)
                    output.warn "Rendering document file $txt.name"
                    vars.content = engine.render(data, context)
                    vars.sourcePath = "ref/${section}/$txt.name"
                    new File("${refDocsDir}/ref/${section}/${name}.html").withWriter(encoding) {out ->
                        template.make(vars).writeTo(out)
                    }
                }
            }
        }

        vars.remove("section")
        vars.content = fullContents.toString()
        vars.single = true

        pathToRoot = ".."
        vars.logo = injectPath(logo, pathToRoot)
        vars.sponsorLogo = injectPath(sponsorLogo, pathToRoot)
        vars.path = pathToRoot
        vars.resourcesPath = calculatePathToResources(pathToRoot)

        template = templateEngine.createTemplate(new File("${docResources}/style/layout.html").newReader(encoding))
        new File("${refGuideDir}/single.html").withWriter(encoding) {out ->
            template.make(vars).writeTo(out)
        }

        vars.content = ""
        vars.single = false
        new File("${refGuideDir}/index.html").withWriter(encoding) {out ->
            template.make(vars).writeTo(out)
        }

        pathToRoot = "."
        vars.logo = injectPath(logo, pathToRoot)
        vars.sponsorLogo = injectPath(sponsorLogo, pathToRoot)
        vars.path = pathToRoot
        vars.resourcesPath = calculatePathToResources(pathToRoot)

        new File("${refDocsDir}/index.html").withWriter(encoding) {out ->
            template.make(vars).writeTo(out)
        }

        ant.echo "Built user manual at ${refDocsDir}/index.html"
    }

    void writeChapter(
            section,
            Template layoutTemplate,
            Template sectionTemplate,
            File guideSrcDir,
            String targetDir,
            fullContents,
            vars) {
        fullContents << writePage(section, layoutTemplate, sectionTemplate, guideSrcDir, targetDir, "", "..", 0, vars)
    }

    String writePage(
            section,
            Template layoutTemplate,
            Template sectionTemplate,
            File guideSrcDir,
            String targetDir,
            String subDir,
            path,
            level,
            vars) {
        def sourceFile = new File(guideSrcDir, section.file)
        context.set(DocEngine.SOURCE_FILE, sourceFile)
        context.set(DocEngine.CONTEXT_PATH, path)

        def varsCopy = [*:vars]
        varsCopy.putAll(engineProperties)
        varsCopy.name = section.name
        varsCopy.title = section.title
        varsCopy.path = path
        varsCopy.level = level
        varsCopy.sectionToc = section.children
        varsCopy.sourcePath = section.file
        output.warn "Rendering document file $sourceFile.name"
        varsCopy.content = engine.render(sourceFile.getText("UTF-8"), context)

        // First create the section content, which usually consists of a header
        // and the translated gdoc content.
        def sectionContent = new StringWriter()
        sectionTemplate.make(varsCopy).writeTo(sectionContent)

        // Aggregate the section content and sub-sections.
        def accumulatedContent = new StringBuilder()
        accumulatedContent << sectionContent.toString()

        // Create the sub-section pages.
        level++
        final sectionNumber = varsCopy.sectionNumber
        int subSectionNumber = 1
        for (s in section.children) {
            varsCopy.sectionNumber = "$sectionNumber.$subSectionNumber"
            accumulatedContent << writePage(s, layoutTemplate, sectionTemplate, guideSrcDir, targetDir, "pages", path, level, varsCopy)
            subSectionNumber++
        }

        // Reset the section number in the template vars.
        varsCopy.sectionNumber = sectionNumber

        // TODO PAL - I don't see why these pages are necessary, plus there seems
        // to be no way to get embedded images to display properly (since the path
        // passed to the Wiki rendering engine is wrong for pages written to a
        // 'pages' subdirectory). Keeping them in case someone, somewhere depends
        // on them.
        //
        // Create the HTML page for this section, which includes the content
        // from all the sub-sections too.
        if (subDir) {
            if (subDir.endsWith('/')) subDir = subDir[0..-2]
            targetDir = "$targetDir/$subDir"

            varsCopy.path = "../${path}"
            varsCopy.logo = injectPath(logo, varsCopy.path)
            varsCopy.sponsorLogo = injectPath(sponsorLogo, varsCopy.path)
        }

        new File("${targetDir}/${section.name}.html").withWriter(encoding) { writer ->
            varsCopy.content = accumulatedContent.toString()
            layoutTemplate.make(varsCopy).writeTo(writer)
        }

        return varsCopy.content
    }

    protected void initialize() {
        if (language) {
            src = new File(src, language)
        }

        if (!workDir) {
            workDir = new File(System.getProperty("java.io.tmpdir"))
        }
        if (!apiDir) {
            apiDir = target
        }
        if (!ant) {
            ant = new AntBuilder()
        }
        def metaProps = DocPublisher.metaClass.properties
        Properties props
        if(engineProperties != null) {
            props = engineProperties
        }
        else {
            props = new Properties()
            engineProperties = props
        }


        if(propertiesFile?.exists()) {
            if(propertiesFile.name.endsWith('.properties')) {
                propertiesFile.withInputStream {
                    props.load(it)
                }
            }
            else if(propertiesFile.name.endsWith('.yml')) {
                propertiesFile.withInputStream { input ->
                    def ymls = new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(input)
                    for(yml in ymls) {
                        if(yml instanceof Map) {
                            def config = yml.grails?.doc
                            if(config instanceof Map) {
                                flattenKeys(props, (Map) config,[], true)
                            }
                        }
                    }
                }

            }

        }


        for (MetaProperty mp in metaProps) {
            if (mp.type == String) {
                def value = props[mp.name]
                if (value) {
                    this[mp.name] = value
                }
            }
        }

        context = new BaseInitialRenderContext()
        initContext(context, "..")

        if(asciidoc) {
            engine = new AsciiDocEngine(context)
        }
        else {
            engine = new DocEngine(context)
        }

        engine.engineProperties = props
        context.renderEngine = engine

        // Add any custom macros registered with this publisher to the engine.
        for (m in customMacros) {
            if (m.metaClass.hasProperty(m, "initialContext")) {
                m.initialContext = context
            }
            engine.addMacro(m)
        }
    }

    private void flattenKeys(Map<String, Object> flatConfig, Map currentMap, List<String> path, boolean forceStrings) {
        currentMap.each { key, value ->
            String stringKey = String.valueOf(key)
            if(value != null) {
                if(value instanceof Map) {
                    flattenKeys(flatConfig, (Map)value, ((path + [stringKey]) as List<String>).asImmutable(), forceStrings)
                } else {
                    String fullKey
                    if(path) {
                        fullKey = path.join('.') + '.' + stringKey
                    } else {
                        fullKey = stringKey
                    }
                    if(value instanceof Collection) {
                        if(forceStrings) {
                            flatConfig.put(fullKey, ((Collection)value).join(","))
                        } else {
                            flatConfig.put(fullKey, value)
                        }
                        int index = 0
                        for(Object item: (Collection)value) {
                            String collectionKey = "${fullKey}[${index}]".toString()
                            flatConfig.put(collectionKey, forceStrings ? String.valueOf(item) : item)
                            index++
                        }
                    } else {
                        flatConfig.put(fullKey, forceStrings ? String.valueOf(value) : value)
                    }
                }
            }
        }
    }

    /**
     * Checks the table of contents (a tree of {@link UserGuideNode}s) for
     * duplicate section/alias names and invalid file paths.
     * @return <code>false</code> if any errors are detected.
     */
    protected verifyToc(File baseDir, gdocFiles, toc) {
        def hasErrors = false
        def sectionsFound = [] as Set
        def gdocsNotInToc = gdocFiles as Set

        // Defensive copy
        if (gdocsNotInToc.is(gdocFiles)) gdocsNotInToc = new HashSet(gdocFiles)

        for (ch in toc.children) {
            hasErrors |= verifyTocInternal(baseDir, ch, sectionsFound, gdocsNotInToc, [])
        }

        if (gdocsNotInToc) {
            for (gdoc in gdocsNotInToc) {
                output.warn "No TOC entry found for '${gdoc}'"
            }
        }

        return !hasErrors
    }

    private verifyTocInternal(File baseDir, section, existing, gdocFiles, pathElements) {
        def hasErrors = false
        def fullName = pathElements ? "${pathElements.join('/')}/${section.name}" : section.name

        // Has this section name already been used?
        if (section.name in existing) {
            hasErrors = true
            output.error "Duplicate section name: ${fullName}"
        }

        // Does the file path for the gdoc exist?
        if (!section.file || !new File(baseDir, section.file).exists()) {
            hasErrors = true
            output.error "No file found for '${fullName}'"
        }
        else {
            // Found this gdoc file in the TOC.
            gdocFiles.remove section.file
        }

        existing << section.name

        for (s in section.children) {
            hasErrors |= verifyTocInternal(baseDir, s, existing, gdocFiles, pathElements + section.name)
        }

        return hasErrors
    }

    private String calculateLanguageDir(startPath, endPath = '') {
        def elements = [startPath, language, endPath]
        elements = elements.findAll { it }
        return elements.join('/')
    }

    private String injectPath(String source, String path) {
        if (!source) return source

        def templateEngine = new groovy.text.SimpleTemplateEngine()
        def out = new StringWriter()
        templateEngine.createTemplate(source).make(path: calculatePathToResources(path)).writeTo(out)
        return out.toString()
    }

    private String calculatePathToResources(String pathToRoot) {
        return language ? '../' + pathToRoot : pathToRoot
    }

    private initContext(context, path) {
        context.set(DocEngine.CONTEXT_PATH, path)
        context.set(DocEngine.BASE_DIR, src.absolutePath)
        context.set(DocEngine.API_BASE_PATH, apiDir.absolutePath)
        context.set(DocEngine.API_CONTEXT_PATH, calculatePathToResources(path))
        context.set(DocEngine.RESOURCES_CONTEXT_PATH, calculatePathToResources(path))
        return context
    }

    private unpack(Map args) {

        def dir = args["dest"] ?: "."
        def src = args["src"]
        def overwriteOption = args["overwrite"] == null ? true : args["overwrite"]

        // Can't unjar a file from within a JAR, so we copy it to
        // the destination directory first.
        try {
            URL url = getClass().getClassLoader().getResource(src)
            if (url) {
                url.withInputStream { InputStream input ->
                    new File("$dir/$src").withOutputStream { out ->
                        def buffer = new byte[1024]
                        int len
                        while ((len = input.read(buffer)) != -1) {
                            out.write(buffer, 0, len)
                        }
                    }
                }
            }
            // Now unjar it, excluding the META-INF directory.
            ant.unjar(dest: dir, src: "${dir}/${src}", overwrite: overwriteOption) {
                patternset {
                    exclude(name: "META-INF/**")
                }
            }
        }
        finally {
            // Don't need the JAR file any more, so remove it.
            ant.delete(file: "${dir}/${src}", failonerror:false)
        }
    }

    private overrideAliasesFromToc(node) {
        engine.engineProperties.setProperty "alias.${node.name}", node.file - ".gdoc"

        for (section in node.children) {
            overrideAliasesFromToc(section)
        }
    }
}
