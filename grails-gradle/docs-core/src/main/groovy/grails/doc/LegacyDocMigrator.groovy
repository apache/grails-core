package grails.doc

import grails.doc.internal.LegacyTocStrategy
import grails.doc.internal.StringEscapeCategory

/**
 * <p>Migrates gdoc-based user guides from the old style, in which the section
 * numbers are included in the filenames, and the new style which uses a
 * YAML-based TOC file to organise the sections. It doesn't do a perfect job
 * but it does a lot of the hard work and you can fine tune the generated gdocs
 * afterwards.</p>
 * <p>The migration will not only rename and restructure the gdoc files, but it
 * will also generate a toc.yml file that reproduces the existing guide structure.
 * Additional files include:</p>
 * <ul>
 * <li><tt>links.yml</tt> - a YAML file mapping new section names to the old ones.
 * Ensures that URL fragments in old external links continue to work.</li>
 * <li><tt>rewriteRules.txt</tt> - a simple text file that maps the old chapter
 * HTML filenames to their new ones. Useful for creating Apache URL rewrite rules.
 * </li>
 * </ul>
 * <p>The names of the new sections are based on the old section names, so they
 * may not be ideal. Also, the new style requires that every section has a unique
 * name, although the documentation publishing will pick up and warn of duplicates.
 * </p>
 */
class LegacyDocMigrator {
    private static final String EOL = System.getProperty("line.separator")

    private guideSrcDir
    private aliasMap
    private outDir

    LegacyDocMigrator(File guideSrcDir, aliasMap) {
        this(guideSrcDir, new File(guideSrcDir.parentFile, "migratedGuide"), aliasMap)
    }

    LegacyDocMigrator(File guideSrcDir, File outDir, aliasMap) {
        this.guideSrcDir = guideSrcDir
        this.outDir = outDir
        this.aliasMap = aliasMap.collectEntries { key, value -> [value, key] }
    }

    def migrate() {
        outDir.mkdirs()

        def files = guideSrcDir.listFiles()?.findAll { it.name.endsWith(".gdoc") } ?: []
        def guide = new LegacyTocStrategy().generateToc(files)

        def legacyLinkMap = new File(outDir, "links.yml")
        legacyLinkMap.withWriter('UTF-8') { w ->
            guide.children.each(this.&migrateSection.rcurry([], w))
        }

        def tocFile = new File(outDir, "toc.yml")
        tocFile.withWriter('UTF-8') { w ->
            guide.children.each(this.&writeSectionToToc.rcurry(w, 0))
        }

        // A mapping that can be utilised by Apache HTTPD URL rewriting.
        def rewriteRulesFile = new File(outDir, "rewriteRules.txt")
        rewriteRulesFile.withPrintWriter('UTF-8') { w ->
            for (section in guide.children) {
                w.println "${StringEscapeCategory.encodeAsUrlPath(section.name)}.html -> ${StringEscapeCategory.encodeAsUrlPath(alias(section))}.html"
            }
        }
    }

    private migrateSection(section, pathElements, writer) {
        def alias = alias(section)
        def newDir = new File(outDir, pathElements.join('/'))
        def newFile = new File(newDir, "${alias}.gdoc")
        def oldFile = new File(guideSrcDir, section.file)
        newFile.bytes = oldFile.bytes

        writer << alias << ': ' << section.name << EOL

        if (section.children) {
            newDir = new File(newDir, alias)
            newDir.mkdirs()
            for (s in section.children) {
                migrateSection(s, pathElements + alias, writer)
            }
        }
    }

    private writeSectionToToc(section, writer, indent) {
        writer << '  ' * indent << alias(section) << ": "
        if (section.children) {
            indent++
            writer << EOL << '  ' * indent << "title: " << section.title << EOL

            for (s in section.children) {
                writeSectionToToc s, writer, indent
            }
        }
        else {
            writer << section.title << EOL
        }
    }

    private alias(section) {
        def alias = aliasMap[section.name]
        if (!alias) {
            alias = naturalNameToCamelCase(section.title)
            aliasMap[section.name] = alias
        }
        return alias
    }

    private naturalNameToCamelCase(name) {
        if (!name) return name

        // Start by breaking the natural name into words.
        def parts = name.split(/\s+/)

        // Lower case the first letter according to Java Beans rules.
        parts[0] = java.beans.Introspector.decapitalize(parts[0])

        // The rest of the name parts should have their first letter capitalised.
        for (int i = 1; i < parts.size(); i++) {
            parts[i] = parts[i].capitalize()
        }

        return parts.join('')
    }
}
