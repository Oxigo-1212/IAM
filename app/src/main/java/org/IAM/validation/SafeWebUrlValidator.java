package org.IAM.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Rejects non-web URI schemes (such as javascript: or data:) and malformed URLs.
 */
public class SafeWebUrlValidator implements ConstraintValidator<SafeWebUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                && ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                && host != null
                && !host.isBlank();
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
