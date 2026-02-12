package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Optional;
import java.util.function.Function;

public class BackticksRemover implements Function<String, String> {

    public static final String BACKTICK = "`";

    @Override
    public String apply(String string) {
        return Optional.ofNullable(string)
                .map(String::trim)
                .filter(s ->s.length()>=2 && s.startsWith(BACKTICK) && s.endsWith(BACKTICK))
                .map(s -> s.substring(1, s.length() - 1))
                .orElse(string);
    }
}
