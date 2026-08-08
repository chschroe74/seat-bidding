package de.gigaworks.seatbidding.exception;

import java.util.regex.Pattern;

final class ExceptionHelper {
    
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{}");
    
    private ExceptionHelper() {
    }
    
    static String prepareMessage(String message, Object... arguments) {
        var matcher = PLACEHOLDER.matcher(message);
        var parsed = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            String replacement = index >= arguments.length || arguments[index] == null
                    ? "null"
                    : arguments[index].toString();
            matcher.appendReplacement(parsed, java.util.regex.Matcher.quoteReplacement(replacement));
            index++;
        }
        matcher.appendTail(parsed);
        return parsed.toString();
    }
    
}
