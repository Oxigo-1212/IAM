package org.IAM.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that a non-null string is a syntactically valid http or https URI with a host.
 */
@Documented
@Constraint(validatedBy = SafeWebUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeWebUrl {
    String message() default "Avatar URL must be a valid http or https URL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
