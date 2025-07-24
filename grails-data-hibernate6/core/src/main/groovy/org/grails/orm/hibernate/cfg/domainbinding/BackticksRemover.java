package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;
import java.util.function.Function;

public class BackticksRemover implements Function<String, String> {
    @Override
    public String apply(String string) {
        return Optional.ofNullable(string)
                .map(String::trim)
                .filter(s ->s.length()>=2 && s.startsWith("`") && s.endsWith("`"))
                .map(s -> s.substring(1, s.length() - 1))
                .orElse(string);
    }
}
