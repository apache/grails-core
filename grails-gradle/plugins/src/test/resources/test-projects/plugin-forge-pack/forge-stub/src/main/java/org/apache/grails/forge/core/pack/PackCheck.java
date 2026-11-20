package org.apache.grails.forge.core.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stands in for the class of Forge that checks a feature pack. It is started as Forge is: the directory
 * of the pack is its one argument, its problems go to the standard output, one on a line, and it ends
 * with 0 for none, having written the one line Forge writes for a pack it checked, with 1 for some and
 * with anything else when it could not check. What it takes for a problem is made up, and enough for a
 * test to tell what of a pack it was shown; a descriptor can also tell it to end as a Forge that went
 * wrong does.
 */
public final class PackCheck {

    private PackCheck() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("stand-in: expected the directory of the pack and got " + args.length + " arguments");
            System.exit(2);
        }
        Path directory = Path.of(args[0]);
        System.err.println("stand-in directory: " + directory);
        System.err.println("stand-in class path: " + System.getProperty("java.class.path"));
        System.err.println("stand-in working directory: " + System.getProperty("user.dir"));
        System.err.println("stand-in verbosity of SLF4J: " + System.getProperty("slf4j.internal.verbosity"));
        List<String> problems = new ArrayList<>();
        Path descriptor = directory.resolve("feature.yml");
        if (!Files.isRegularFile(descriptor, LinkOption.NOFOLLOW_LINKS)) {
            problems.add("There is no feature.yml in the directory, or it is not a file");
        } else {
            String text = Files.readString(descriptor, StandardCharsets.UTF_8);
            if (text.contains("stand-in: crash")) {
                System.err.println("SLF4J(W): No SLF4J providers were found.");
                System.err.println("SLF4J(W): Defaulting to no-operation (NOP) logger implementation");
                System.err.println("SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.");
                System.err.println("stand-in crashed");
                System.exit(3);
            }
            if (text.contains("stand-in: silent")) {
                System.exit(1);
            }
            if (text.contains("stand-in: unchecked")) {
                System.err.println("The feature pack could not be checked: stand-in");
                System.exit(2);
            }
            if (text.contains("stand-in: ended")) {
                // as a template that ends the JVM does
                System.exit(0);
            }
            if (text.contains("stand-in: writes-and-passes")) {
                // as a template that writes to the standard output itself, around what Forge leaves it
                System.out.println("written by a template");
                System.out.println("feature pack checked: templates=1 applications=4");
                System.out.println("and again");
                System.exit(0);
            }
            if (text.contains("stand-in: names-older-java")) {
                // the words of the launcher of Java, said by something else: not at the start of a line
                System.err.println("The feature pack could not be checked: Error: LinkageError occurred while loading main class "
                        + "org.apache.grails.forge.core.pack.PackCheck java.lang.UnsupportedClassVersionError: Audit (class file version 70.0)");
                System.err.println("a template said: Error: Could not find or load main class org.apache.grails.forge.core.pack.PackCheck");
                System.exit(2);
            }
            if (text.contains("stand-in: says-and-passes")) {
                System.out.println("a line that is not the one of a pack that was checked");
                System.exit(0);
            }
            if (text.contains("stand-in: older-java")) {
                System.err.println("Error: LinkageError occurred while loading main class org.apache.grails.forge.core.pack.PackCheck");
                System.err.println("\tjava.lang.UnsupportedClassVersionError: org/apache/grails/forge/core/pack/PackCheck has been compiled by a "
                        + "more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only "
                        + "recognizes class file versions up to 61.0");
                System.exit(1);
            }
            if (text.contains("stand-in: noisy")) {
                for (int line = 0; line < 100; line++) {
                    problems.add("problem " + line + " \u001b[31mred\u0007 caf\u00e9");
                }
            }
            for (String line : text.split("\n")) {
                if (line.startsWith("colour:")) {
                    problems.add("the descriptor: has the key 'colour', which this version of Forge does not read here");
                }
            }
            Path templates = directory.resolve("templates");
            if (Files.isDirectory(templates, LinkOption.NOFOLLOW_LINKS)) {
                List<Path> entries;
                try (Stream<Path> paths = Files.walk(templates)) {
                    entries = paths.sorted().collect(Collectors.toList());
                }
                for (Path entry : entries) {
                    String name = templates.relativize(entry).toString().replace('\\', '/');
                    if (Files.isSymbolicLink(entry)) {
                        problems.add("files[0].from: '" + name + "' is not a file under templates of the pack, or is a link or is reached through one");
                    } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                            && Files.readString(entry, StandardCharsets.UTF_8).contains("${nope}")) {
                        problems.add("files[0].from: The template " + name + " cannot be rendered: There is no 'nope' for a template");
                    }
                }
            }
        }
        for (String problem : problems) {
            System.out.println(problem);
        }
        if (problems.isEmpty()) {
            System.out.println("feature pack checked: templates=1 applications=4");
        }
        System.exit(problems.isEmpty() ? 0 : 1);
    }
}
