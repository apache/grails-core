package grails.doc.internal

/**
 * Simple class that checks whether a path relative to a base directory exists
 * or not. Each instance of the class can have its own base directory.
 */
class FileResourceChecker {
    private final File baseDir

    FileResourceChecker(File baseDir) {
        this.baseDir = baseDir
    }

    boolean exists(path) {
        return new File(baseDir, path).exists()
    }
}
