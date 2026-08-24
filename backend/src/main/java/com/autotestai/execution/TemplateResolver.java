package com.autotestai.execution;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class TemplateResolver {

    private static final Pattern TEMPLATE =
            Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_.-]{0,119})}}", Pattern.CASE_INSENSITIVE);

    public String resolve(String value, Map<String, String> variables) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = TEMPLATE.matcher(value);
        StringBuilder resolved = new StringBuilder(value.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = variables.get(name);
            if (replacement == null) {
                throw new ExecutionException("Missing runtime variable: " + name);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        if (resolved.indexOf("{{") >= 0 || resolved.indexOf("}}") >= 0) {
            throw new ExecutionException("Request contains an invalid or unresolved variable template");
        }
        return resolved.toString();
    }
}
