#! /usr/bin/env groovy

/**
 * Script that recursively compares everything within {hidden} macros in a
 * user guide translation with another gdoc source directory. By default,
 * the translation is compared against the reference 'en' docs.
 */
if (!args || args.size() > 3) {
    println """\
        USAGE
        
          left_to_do LANG [LANG] [-nc]

        where

          LANG = a language code, e.g. 'en' or 'pt_BR'
          -nc means: copy the new file if the target LANG file does not exist

        Compares the contents of the {hidden} blocks in the user guide for the first language
        against the entire contents of the second language. By default, the second language
        is 'en', i.e. the reference user guide (which has no {hidden} blocks).\
        """.stripIndent()
    System.exit 1
}


// Create a temporary directory to dump the extracted {hidden} contents to.
def tmpDocDir = new File("tmp")
if (tmpDocDir.exists()) {
    println "Removing the old temporary output directory"
    tmpDocDir.deleteDir()
}

tmpDocDir.mkdirs()

// Recursively copy the {hidden} blocks from the source language to the temporary
// directory, ensuring the resulting files have the same relative paths and names
// as the originals.
def srcDir = new File("src", args[0])
if (!srcDir.exists()) {
    println "No source directory for language '${args[0]}'"
    System.exit 2
}
def refDir = new File("src", args.size() >= 2 ? args[1] : 'en')
def isNc = (args.size() >= 3 && args[2]=='-nc') ? true :false
def ant = new AntBuilder()

srcDir.eachFileRecurse { f ->
    if (f.directory) {
        // Recreate this directory in the temporary output folder.
        new File(tmpDocDir, relativePath(srcDir, f)).mkdir()
    }
    else {
        // Read the source file, create a target file with the same
        // name and relative location, and write out the contents of
        // {hidden} blocks to that target file. Note the target file
        // will include the {hidden} macros.
        def relPathStr = relativePath(srcDir, f)
        def targetFile = new File(refDir, relPathStr)
        if (isNc && !targetFile.exists()) {
            if (!targetFile.parentFile.exists()) {
                targetFile.parentFile.mkdirs()
            }
            ant.copy(file:f,tofile:targetFile)
        }
        targetFile = new File(tmpDocDir, relPathStr)
        writeHiddenToFile f.text, targetFile
    }
}

// Now recursively diff the generated files against the reference directory.
generateDiff refDir, tmpDocDir

/// End of script execution ///


def relativePath(File baseDir, File file) {
    def relativePath = file.absolutePath - baseDir.absolutePath
    if (relativePath[0] == File.separator) relativePath = relativePath[1..-1]
    return relativePath
}

def writeHiddenToFile(String text, File target) {
    target.withWriter { w ->
        def marker = 0

        while (marker != -1) {
            def startHidden = findNextMacro(text, marker, "hidden")
            def startCode = findNextMacro(text, marker, "code")

            def start
            def macro
            def writeMacro = false
            if (startHidden != -1 && (startHidden < startCode || startCode == -1)) {
                macro = "hidden"
                start = startHidden
            }
            else {
                macro = "code"
                start = startCode
                writeMacro = true
            }
            def end = start == -1 ? -1 : findNextMacro(text, start + 1, macro)

            if (end != -1) {
                if (!writeMacro) {
                    start = start + macroLength(macro)
                }
                else {
                    end = end + macroLength(macro)
                }

                w << text.substring(start, end)
                
                if (writeMacro) w << '\n'

                marker = end
                if (!writeMacro) marker++
            }
            else {
                marker = -1
            }
        }
    }
}

def generateDiff(File refDir, File targetDir) {
    def pb = new ProcessBuilder(["diff", "--unified", "--recursive", refDir.absolutePath, targetDir.absolutePath])
    pb.redirectErrorStream true

    def process = pb.start()
    process.consumeProcessOutputStream System.out
    process.waitFor()
}

def findNextMacro(String text, start, macro) {
    // This matches {macro} and {macro:...} unless the opening
    // brace is escaped with a backslash. The first parentheses
    // correspond to a negative lookbehind to exclude '\{'.
    def m = text =~ /(?<!\\)\{${macro}(?::[^\}]+)?\}/
    m.region start, text.size()
    return m.find() ? m.start() : -1
}

def macroLength(macro) {
    // Length of macro name + 2 for the '{' and '}'
    return macro.size() + 2
}
