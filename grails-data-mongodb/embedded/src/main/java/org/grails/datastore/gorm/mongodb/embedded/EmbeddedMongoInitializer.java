/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.mongodb.embedded;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.core.SpringProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 * Starts an embedded MongoDB before the application context refreshes and publishes its
 * connection URL into whichever configuration properties the application reads, so an
 * application runs without a MongoDB installation and without Docker.
 *
 * <p>This is an {@link ApplicationContextInitializer} rather than an auto configuration
 * because the URL has to be in the {@code Environment} before the datastore bean that
 * reads it is created.
 *
 * <p>Asked for by the URL, the way an in-memory SQL database is. An application names the
 * embedded server where it would otherwise name a host, and the environment that wants one says
 * so where it already says which database to talk to:
 *
 * <pre>
 * environments:
 *     development:
 *         grails:
 *             mongodb:
 *                 url: mongodb://embedded/bookstore
 *     production:
 *         grails:
 *             mongodb:
 *                 url: mongodb://localhost:27017/bookstore
 * </pre>
 *
 * <p>Nothing else switches it on, and nothing switches it off: a URL naming a host is served by
 * the driver and no server is started, exactly as an application with {@code h2} on the classpath
 * and a PostgreSQL URL never starts H2. A port may be given -- {@code mongodb://embedded:27018/db}
 * -- and is otherwise chosen below.
 *
 * <p>The rest configures the server behind that URL rather than which server to reach:
 * <table>
 * <caption>Supported properties</caption>
 * <tr><td>{@code embedded.mongodb.backend}</td><td>{@code in-memory} or {@code flapdoodle};
 *     defaults to flapdoodle when it is on the classpath, otherwise in-memory</td></tr>
 * <tr><td>{@code embedded.mongodb.property-names}</td><td>comma separated properties that may
 *     name an embedded server, {@code grails.mongodb.url} by default</td></tr>
 * <tr><td>{@code embedded.mongodb.database-dir}</td><td>keeps the data after the server
 *     stops, which only the flapdoodle backend can do</td></tr>
 * <tr><td>{@code embedded.mongodb.version}</td><td>the MongoDB version, where the backend
 *     can choose one</td></tr>
 * </table>
 *
 * <p>A host genuinely named {@code embedded} cannot be reached through these properties; name it
 * by its address or its fully qualified name instead.
 *
 * <p>Making the target property configurable is what keeps this useful outside Grails
 * Data: point {@code property-names} at {@code spring.data.mongodb.uri} and it serves a
 * plain Spring Boot application, although Spring Data users are generally better served
 * by flapdoodle's own {@code de.flapdoodle.embed.mongo.spring3x} auto configuration,
 * which this deliberately does not duplicate.
 *
 * @author Grails
 * @since 8.0
 */
public class EmbeddedMongoInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * The host that means "start one and connect me to it" rather than an address to reach.
     */
    public static final String EMBEDDED_HOST = "embedded";

    public static final String BACKEND = "embedded.mongodb.backend";

    public static final String PROPERTY_NAMES = "embedded.mongodb.property-names";

    public static final String DATABASE_DIR = "embedded.mongodb.database-dir";

    public static final String VERSION = "embedded.mongodb.version";

    public static final String DEFAULT_PROPERTY_NAME = "grails.mongodb.url";

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMongoInitializer.class);

    private static final String PROPERTY_SOURCE_NAME = "embeddedMongoDB";

    private static final String DEFAULT_DATABASE = "test";

    private static final int DEFAULT_PORT = 27017;

    private static final int DEFAULT_SERVER_PORT = 8080;

    /**
     * A URL asking for an embedded server, with the port and database it asks for. Credentials are
     * matched so that one copied from a real connection is still recognised, and then ignored:
     * there is nothing to authenticate against.
     */
    private static final Pattern EMBEDDED_URL = Pattern.compile(
            "^mongodb(?:\\+srv)?://(?:[^@/]*@)?" + EMBEDDED_HOST + "(?::(\\d+))?(?:/([^?]*))?(?:\\?.*)?$");

    /**
     * The servers this JVM started, by port. Keyed rather than a single field because two
     * application contexts in one JVM can ask for different ports, and consulted so that a
     * devtools restart reuses its own server without mistaking any other listener for one.
     */
    private static final Map<Integer, RunningEmbeddedMongo> STARTED = new ConcurrentHashMap<>();

    /**
     * Flapdoodle first, so that adding it to an application is all it takes to move from
     * the in-memory reimplementation to a real mongod.
     */
    private final List<EmbeddedMongoBackend> backends;

    public EmbeddedMongoInitializer() {
        this(defaultBackends(EmbeddedMongoInitializer.class.getClassLoader()));
    }

    /**
     * The backends to offer, which is only ever the ones whose library is present.
     *
     * <p>Asking a backend whether it is available means holding one, and holding one means loading
     * its class -- which resolves the types named in its methods, so constructing the flapdoodle
     * backend without flapdoodle fails before it can answer. Since it is deliberately not a
     * dependency of this module, that is the ordinary case: the initializer could not be created at
     * all, and an application asking for the in-memory server got a NoClassDefFoundError naming a
     * library it never asked for.</p>
     *
     * <p>So the question is asked of the class loader instead, by a name rather than by a type.</p>
     */
    static List<EmbeddedMongoBackend> defaultBackends(ClassLoader classLoader) {
        List<EmbeddedMongoBackend> backends = new ArrayList<>();
        if (ClassUtils.isPresent(FlapdoodleMongoBackend.MONGOD_CLASS, classLoader)) {
            backends.add(new FlapdoodleMongoBackend());
        }
        backends.add(new InMemoryMongoBackend());
        return backends;
    }

    EmbeddedMongoInitializer(List<EmbeddedMongoBackend> backends) {
        this.backends = backends;
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        Set<String> propertyNames = propertyNames(environment);
        Matcher asked = firstAskingForEmbedded(environment, propertyNames);
        if (asked == null) {
            return;
        }
        if (SpringProperties.getFlag(AbstractAotProcessor.AOT_PROCESSING)) {
            // Ahead-of-time processing writes bean definitions out as code. It refreshes a context
            // to read them, but nothing in it is meant to run, and a database no one will query is
            // of no use to it. Starting one is also unrecoverable: the server listens on a
            // non-daemon thread and is stopped only by a JVM shutdown hook, so generation finished
            // and then hung, holding the port, until it was killed.
            log.debug("Not starting an embedded MongoDB: this is ahead-of-time processing");
            return;
        }

        int port = resolvePort(environment, asked.group(1));
        String database = resolveDatabase(asked.group(2));

        String url;
        RunningEmbeddedMongo started = STARTED.get(port);
        if (started != null) {
            // A devtools restart reuses this JVM and this class is loaded from a jar, so it
            // survives in the base classloader along with the server the previous
            // application context started. Only a server this initializer started is reused;
            // anything else holding the port makes the start below fail with an error that
            // says so, rather than publishing a MongoDB url pointing at an unrelated service.
            url = "mongodb://" + started.getHost() + ":" + started.getPort() + "/" + database;
            log.info("Reusing the embedded MongoDB this JVM already started at {}", url);
        }
        else {
            url = start(environment, port, database);
        }

        Map<String, Object> published = new HashMap<>();
        for (String propertyName : propertyNames) {
            published.put(propertyName, url);
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, published));

        // Registered as a singleton rather than a bean definition because this runs before
        // any definitions are read, and the server it manages already exists by now. The
        // JVM shutdown hook above still covers the case where the context never refreshes.
        RunningEmbeddedMongo running = STARTED.get(port);
        if (running != null) {
            applicationContext.getBeanFactory()
                    .registerSingleton(EmbeddedMongoLifecycle.BEAN_NAME, new EmbeddedMongoLifecycle(running));
        }
    }

    /**
     * Failures throw rather than returning quietly: falling through would leave the target
     * properties pointing at whatever the application configured, which is exactly what
     * enabling this was meant to replace.
     */
    private String start(ConfigurableEnvironment environment, int port, String database) {
        EmbeddedMongoBackend backend = selectBackend(environment);
        EmbeddedMongoSettings settings = new EmbeddedMongoSettings(port,
                environment.getProperty(VERSION), environment.getProperty(DATABASE_DIR));

        RunningEmbeddedMongo running;
        try {
            running = backend.start(settings);
        }
        catch (IllegalStateException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to start the " + backend.getName() +
                    " embedded MongoDB on port " + port + ", which something else may already be using. " +
                    "Name a free port as mongodb://" + EMBEDDED_HOST + ":<port>/<database>, or name a host " +
                    "instead of " + EMBEDDED_HOST + " to use an external MongoDB.", ex);
        }

        STARTED.put(port, running);

        // Registered on the JVM rather than the application context so that it survives a
        // devtools restart and fires only once, when the JVM itself exits.
        Runtime.getRuntime().addShutdownHook(new Thread(running::stop));

        String url = "mongodb://" + running.getHost() + ":" + running.getPort() + "/" + database;
        log.info("Embedded MongoDB started at {} using the {} backend", url, backend.getName());
        return url;
    }

    private EmbeddedMongoBackend selectBackend(ConfigurableEnvironment environment) {
        String requested = environment.getProperty(BACKEND);
        if (requested != null && !requested.isEmpty()) {
            for (EmbeddedMongoBackend backend : this.backends) {
                if (backend.getName().equals(requested)) {
                    if (!backend.isAvailable()) {
                        throw new IllegalStateException(BACKEND + "=" + requested +
                                " but its library is not on the classpath. Add it as a dependency, or choose one of " +
                                availableNames() + ".");
                    }
                    return backend;
                }
            }
            if (FlapdoodleMongoBackend.NAME.equals(requested)) {
                throw new IllegalStateException(BACKEND + "=" + requested +
                        " but its library is not on the classpath. Add it as a dependency, or choose one of " +
                        availableNames() + ".");
            }
            throw new IllegalStateException(BACKEND + "=" + requested + " is not a known backend. Use one of " +
                    this.backends.stream().map(EmbeddedMongoBackend::getName).collect(Collectors.joining(", ")) +
                    ".");
        }

        for (EmbeddedMongoBackend backend : this.backends) {
            if (backend.isAvailable()) {
                return backend;
            }
        }
        throw new IllegalStateException("A url asked for " + EMBEDDED_HOST +
                " but no embedded MongoDB backend is on the classpath. " +
                "Add de.bwaldvogel:mongo-java-server for an in-memory server, or " +
                "de.flapdoodle.embed:de.flapdoodle.embed.mongo for a real mongod.");
    }

    private List<String> availableNames() {
        List<String> names = new ArrayList<>();
        for (EmbeddedMongoBackend backend : this.backends) {
            if (backend.isAvailable()) {
                names.add(backend.getName());
            }
        }
        return names;
    }

    private Set<String> propertyNames(ConfigurableEnvironment environment) {
        Set<String> propertyNames = new LinkedHashSet<>();
        for (String propertyName : environment.getProperty(PROPERTY_NAMES, DEFAULT_PROPERTY_NAME).split(",")) {
            String trimmed = propertyName.trim();
            if (!trimmed.isEmpty()) {
                propertyNames.add(trimmed);
            }
        }
        if (propertyNames.isEmpty()) {
            propertyNames.add(DEFAULT_PROPERTY_NAME);
        }
        return propertyNames;
    }

    /**
     * The first of the configured properties asking for an embedded server, or null when none is.
     *
     * <p>The properties are read in the order they were named, so an application publishing the
     * same URL into several of them settles which one describes the server by the order it listed
     * them rather than by which happened to be looked at first.</p>
     */
    private Matcher firstAskingForEmbedded(ConfigurableEnvironment environment, Set<String> propertyNames) {
        for (String propertyName : propertyNames) {
            String url = environment.getProperty(propertyName);
            if (url != null) {
                Matcher matcher = EMBEDDED_URL.matcher(url.trim());
                if (matcher.matches()) {
                    return matcher;
                }
            }
        }
        return null;
    }

    /**
     * The port the URL asked for, or one offset by however far the server port has moved, so two
     * applications that did not name a port run side by side without colliding.
     */
    private int resolvePort(ConfigurableEnvironment environment, String urlPort) {
        if (urlPort != null && !urlPort.isEmpty()) {
            return Integer.parseInt(urlPort);
        }
        int serverPort = Integer.parseInt(environment.getProperty("server.port", String.valueOf(DEFAULT_SERVER_PORT)));
        return serverPort == 0 ? DEFAULT_PORT : DEFAULT_PORT + (serverPort - DEFAULT_SERVER_PORT);
    }

    /**
     * The database the URL named, which is the application's own configuration and the only place
     * one is written down now that the URL is what asks for a server at all.
     */
    private String resolveDatabase(String urlDatabase) {
        return urlDatabase == null || urlDatabase.isEmpty() ? DEFAULT_DATABASE : urlDatabase;
    }

}
