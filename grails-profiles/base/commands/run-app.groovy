description("Runs a Grails application") {
    usage "grails run-app"
    synonyms 'run'
    flag name:'debug-jvm', description:"Starts the JVM in debug mode allowing attachment of a remote debugger"
    flag name:'https', description:"Starts Grails in HTTPS mode on port 8443"
    flag name:'port', description:"Specifies the port which to start Grails on (defaults to 8080 or 8443 for HTTPS)"
    flag name:'host', description:"Specifies the host to bind to"
    flag name:'verbose', description:"Show more output from the build during startup"
}

if(!commandLine.isEnvironmentSet()) {
    System.setProperty('grails.env', 'dev')
} else {
    System.setProperty('grails.env', commandLine.environment)
}
// add debug flag if present
try {

    def arguments = []

    if( !(flag('verbose') || console.verbose)) {
        arguments << '--quiet'
    }

    // bootRun (configured by GrailsGradlePlugin) tells the forked application to write its PID to
    // build/run-app.pid, which stop-app reads to terminate it. Clear any stop marker left by a
    // previous stop-app so a genuine startup failure is not mistaken for a deliberate shutdown.
    File pidFile = org.grails.cli.gradle.RunningApplicationProcess.pidFile(buildDir)
    org.grails.cli.gradle.RunningApplicationProcess.clearStopRequest(buildDir)

    arguments.addAll commandLine.remainingArgs

    Integer port = flag('port')?.toInteger() ?: config.getProperty('server.port', Integer)
    String host = flag('host') ?: config.getProperty('server.address', String)

    commandLine.systemProperties.each { key, value ->
        arguments << "-D${key}=$value".toString()
    }

    if(port) {
        arguments << "-Dgrails.server.port=$port"
    }
    if(host) {
        arguments << "-Dgrails.server.address=$host"
    }

    if(flag('https')) {
        if(!port) {
            arguments << "-Dgrails.server.port=8443"
        }
        if(!System.getProperty('grails.server.ssl.key-store')) {
            def keystoreDir = new File(buildDir, "ssl")
            def keystoreFile = new File(keystoreDir, 'keystore')

            boolean keyStoreParametersAvailable = false
            if(!keystoreFile.exists()) {
                console.updateStatus "Generating SSL certificate"
                if ( createSSLCertificate(keystoreDir) ) {
                    keyStoreParametersAvailable = true
                    console.updateStatus "Generated SSL certificate"
                }
                else {
                    console.warn "Unable to automatically generate SSL certificate, manual configuration required. Set 'server.ssl.key-store' in application.yml"
                }
            }
            else {
                keyStoreParametersAvailable = true
            }
            if(keyStoreParametersAvailable) {
                arguments << "-Dgrails.server.ssl.key-store=${keystoreFile}"
                arguments << "-Dgrails.server.ssl.key-store-password=123456"
                arguments << "-Dgrails.server.ssl.key-password=123456"
            }
        }
    }

    if(org.grails.cli.gradle.RunningApplicationProcess.isRunning(pidFile)) {
        console.error "An application started with run-app is already running for this project. Run 'stop-app' first."
        return false
    }

    console.updateStatus "Running application..."

    if(!org.grails.cli.GrailsCli.isInteractiveModeActive()) {
        if(flag('debug-jvm')) {
            gradle."bootRun --debug-jvm"(*arguments)
        }
        else {
            gradle."bootRun"(*arguments)
        }
    }
    else {
        def future
        if(flag('debug-jvm')) {
            future = gradle.async."bootRun --debug-jvm"(*arguments)
        }
        else {
            future = gradle.async."bootRun"(*arguments)
        }

        while(!isServerAvailable(host ?: "localhost", port ?: 8080)) {
            console.indicateProgress()
            if(future.done) {
                // the server exited for some reason, so break
                if(future.get() instanceof Throwable) {
                    break
                }
            }
            sleep(100);
        }

        System.setProperty("run-app.running", "true")
        if(!Boolean.getBoolean("run-app.shutdown.hook.registered")) {
            System.setProperty("run-app.shutdown.hook.registered", "true")
            addShutdownHook {
                if(Boolean.getBoolean("run-app.running")) {
                    try {
                        stopApp()
                    }
                    catch(e) {
                        // ignore
                    }
                }
            }
            isShutdownHookRegistered = true
        }

        sleep 500
    }

}
catch(org.gradle.tooling.BuildCancelledException e) {
    console.updateStatus("Application stopped")
    return true
}
catch(Throwable e) {
    // A deliberate stop-app terminates the bootRun process, which surfaces here as a build
    // failure; report it as a clean shutdown rather than a startup failure.
    if(org.grails.cli.gradle.RunningApplicationProcess.isStopRequested(buildDir)) {
        console.updateStatus("Application stopped")
        return true
    }
    console.error "Failed to start server", e
    return false
}

protected boolean createSSLCertificate(File keystoreDir) {

    if (!keystoreDir.exists() && !keystoreDir.mkdir()) {
        throw new RuntimeException("Unable to create keystore folder: $keystoreDir.canonicalPath")
    }

    def keyToolClass = getKeyToolClass()
    if(keyToolClass) {
        try {
            def keystoreFile = new File(keystoreDir, "keystore")
            keyToolClass.main(
                "-genkey",
                "-alias", "localhost",
                "-dname", "CN=localhost,OU=Test,O=Test,C=US",
                "-keyalg", "RSA",
                "-validity", "365",
                "-storepass", "key",
                "-keystore", keystoreFile.absolutePath,
                "-storepass", "123456",
                "-keypass", "123456")
        }
        catch(Throwable e) {
            return false
        }

        return true
    }
    else {
        return false
    }
}

protected Class getKeyToolClass() {
    try {
        try {
            // Sun JDK 8
            return Class.forName( 'sun.security.tools.keytool.Main' )
        }
        catch(ClassNotFoundException e1) {
            try {
                // Sun pre-JDK 8
                return Class.forName( 'sun.security.tools.KeyTool' )
            }
            catch (ClassNotFoundException e2) {
                // no try/catch for this one, if neither is found let it fail
                return Class.forName( 'com.ibm.crypto.tools.KeyTool' )
            }
        }
    }
    catch(Throwable e) {
        return null
    }
}
