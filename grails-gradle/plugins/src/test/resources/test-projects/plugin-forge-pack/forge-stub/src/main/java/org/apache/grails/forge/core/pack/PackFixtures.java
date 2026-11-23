package org.apache.grails.forge.core.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stands in for the class of Forge that generates the fixtures of a feature pack. It is started as
 * Forge is: with the directory of the pack, the directory to generate in and, optionally, a file that
 * lists the fixtures. It writes a line for each fixture and then the line Forge ends with, and ends
 * with 0 when every fixture was generated, with 1 when one was refused or the pack has problems, and
 * with anything else when it could not generate.
 *
 * <p>What it generates is no application: a directory with a script of the name of the Gradle wrapper,
 * which says what it was started with and ends as a build that passed, or, for a fixture whose name
 * starts with {@code broken}, as one whose task failed, and for one whose name starts with
 * {@code failing}, as one that also says what went wrong. A fixture whose name starts with
 * {@code bare} has no wrapper, and one whose name starts with {@code refused} is refused. The wrapper
 * of one whose name starts with {@code slow} does not end, and that of one whose name starts with
 * {@code lingering} ends and leaves a process behind that has its output. Each directory has the
 * repositories the stand-in was given in {@code repositories.txt}, as an application has them in its
 * build file. Without a list there are the four fixtures Forge makes of a pack that names no type of
 * application. A descriptor can tell it to end as a Forge that went
 * wrong does. Of every repository it is given that is a directory it says which jars it has, and what
 * the entry {@code marker.txt} of each says, which is how far a stand-in sees into what an application
 * would be built with.</p>
 */
public final class PackFixtures {

    private PackFixtures() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("stand-in: expected two or three arguments and got " + args.length);
            System.exit(2);
        }
        Path directory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        System.err.println("stand-in fixtures of: " + directory);
        System.err.println("stand-in fixtures in: " + output);
        System.err.println("stand-in fixtures class path: " + System.getProperty("java.class.path"));
        System.err.println("stand-in fixtures repositories: " + System.getenv("GRAILS_REPO_URL"));
        sayJars(System.getenv("GRAILS_REPO_URL"));
        List<String> fixtures = new ArrayList<>();
        if (args.length == 3) {
            for (String line : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
                System.err.println("stand-in fixture line: " + line);
                fixtures.add(line);
            }
        } else {
            System.err.println("stand-in fixtures are the defaults");
            fixtures.add("web web");
            fixtures.add("rest-api rest-api");
            fixtures.add("web-plugin web-plugin");
            fixtures.add("plugin plugin");
            fixtures.add("rest-api-plugin rest-api-plugin");
        }
        Path descriptor = directory.resolve("feature.yml");
        String text = Files.isRegularFile(descriptor, LinkOption.NOFOLLOW_LINKS) ? Files.readString(descriptor, StandardCharsets.UTF_8) : null;
        if (text == null) {
            System.out.println("pack: There is no feature.yml in the directory, or it is not a file");
            System.out.println("feature pack fixtures: generated=0 refused=0");
            System.exit(1);
        }
        if (text.contains("stand-in: fixtures-crash")) {
            System.err.println("The fixtures of the feature pack could not be generated: stand-in");
            System.exit(2);
        }
        if (text.contains("stand-in: fixtures-ended")) {
            // as a template that ends the JVM does
            System.out.println("generated web");
            System.exit(0);
        }
        if (text.contains("stand-in: fixtures-miscounted")) {
            System.out.println("generated web");
            System.out.println("feature pack fixtures: generated=2 refused=0");
            System.exit(0);
        }
        if (text.contains("stand-in: fixtures-noisy")) {
            // as a template that writes to the standard output itself, around what Forge leaves it
            System.out.println("written by a template");
        }
        boolean problems = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("colour:")) {
                System.out.println("pack: the descriptor: has the key 'colour', which this version of Forge does not read here");
                problems = true;
            }
        }
        if (problems) {
            System.out.println("feature pack fixtures: generated=0 refused=0");
            System.exit(1);
        }
        int generated = 0;
        int refused = 0;
        for (String fixture : fixtures) {
            String[] words = fixture.split(" ");
            String name = words[0];
            if (name.startsWith("refused")) {
                System.out.println("refused " + name + ": The feature pack acme-audit is not for an application of the type plugin: its "
                        + "descriptor names [web, rest-api] \u001b[31m");
                refused++;
                continue;
            }
            Path application = Files.createDirectories(output.resolve(name));
            Files.writeString(application.resolve("settings.gradle"), "rootProject.name = 'shop'\n");
            Files.writeString(application.resolve("repositories.txt"), String.valueOf(System.getenv("GRAILS_REPO_URL")));
            if (name.startsWith("slow")) {
                Files.writeString(application.resolve("slow"), "");
            }
            if (name.startsWith("lingering")) {
                Files.writeString(application.resolve("lingers"), "");
            }
            if (name.startsWith("broken")) {
                Files.writeString(application.resolve("fails"), "");
            }
            if (name.startsWith("failing")) {
                Files.writeString(application.resolve("says"), "");
            }
            if (!name.startsWith("bare")) {
                Path wrapper = Files.writeString(application.resolve("gradlew"), String.join("\n",
                        "#!/bin/sh",
                        "echo \"stand-in wrapper arguments: $*\"",
                        "echo \"stand-in wrapper directory: $(pwd -P)\"",
                        "echo \"stand-in wrapper JAVA_HOME: $JAVA_HOME\"",
                        "if [ -f slow ]; then",
                        "  echo 'stand-in wrapper does not end'",
                        "  sleep 600 &",
                        "  sleep 600",
                        "fi",
                        "if [ -f lingers ]; then",
                        "  sleep 45 &",
                        "fi",
                        "if [ -f says ]; then",
                        "  echo '> Task :generateI18nDescriptor FAILED'",
                        "  echo 'FAILURE: Build failed with an exception.'",
                        "  echo '* What went wrong:'",
                        "  echo \"Execution failed for task ':generateI18nDescriptor'.\"",
                        "  echo '> The plugin ships message bundles outside its own namespace'",
                        "  echo ''",
                        "  echo '* Try:'",
                        "  echo '> Run with --info'",
                        "  echo '* Exception is:'",
                        "  i=0; while [ $i -lt 40 ]; do echo \"	at stack.Frame$i\"; i=$((i+1)); done",
                        "  echo 'BUILD FAILED in 1s'",
                        "  exit 1",
                        "fi",
                        "if [ -f fails ]; then",
                        "  echo '> Task :compileJava'",
                        "  echo '> Task :compileGroovy FAILED'",
                        "  i=0; while [ $i -lt 40 ]; do echo \"compiler line $i\"; i=$((i+1)); done",
                        "  printf '%0400d\\n' 0",
                        "  printf 'startup failed: \\033[31mAudit.groovy: 3: unable to resolve class Missing\\n' >&2",
                        "  echo 'BUILD FAILED in 1s'",
                        "  exit 1",
                        "fi",
                        "echo 'BUILD SUCCESSFUL in 1s'",
                        ""));
                wrapper.toFile().setExecutable(true);
                Files.writeString(application.resolve("gradlew.bat"), String.join("\r\n",
                        "@echo off",
                        "echo stand-in wrapper arguments: %*",
                        "echo stand-in wrapper directory: %CD%",
                        "echo stand-in wrapper JAVA_HOME: %JAVA_HOME%",
                        "if exist fails (",
                        "  echo ^> Task :compileGroovy FAILED",
                        "  echo startup failed: Audit.groovy: 3: unable to resolve class Missing",
                        "  echo BUILD FAILED in 1s",
                        "  exit /b 1",
                        ")",
                        "echo BUILD SUCCESSFUL in 1s",
                        ""));
            }
            System.out.println("generated " + name);
            generated++;
        }
        System.out.println("feature pack fixtures: generated=" + generated + " refused=" + refused);
        System.exit(refused == 0 ? 0 : 1);
    }

    // each jar of a repository that is a directory, by its path in the repository, with what its marker.txt says
    private static void sayJars(String repositories) throws IOException {
        if (repositories == null) {
            return;
        }
        for (String entry : repositories.split(";")) {
            Path repository = Path.of(entry);
            if (!Files.isDirectory(repository)) {
                continue;
            }
            List<Path> jars;
            try (Stream<Path> walked = Files.walk(repository)) {
                jars = walked.filter(path -> path.toString().endsWith(".jar")).sorted().toList();
            }
            for (Path jar : jars) {
                String marker = "-";
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry found = zip.getEntry("marker.txt");
                    if (found != null) {
                        marker = new String(zip.getInputStream(found).readAllBytes(), StandardCharsets.UTF_8).trim();
                    }
                }
                System.err.println("stand-in fixtures repository jar: " + repository.relativize(jar).toString().replace('\\', '/') + " marker=" + marker);
            }
        }
    }
}
