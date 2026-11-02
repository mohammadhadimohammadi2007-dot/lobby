package io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Implemented by the lobby, never by addons: addons call it, but must not implement or extend it.
 *
 * <p>That is what lets the API grow without breaking anyone: a minor version may add methods to such a type,
 * which would break an addon that implements it. Types without this annotation that addons implement (such as
 * {@code LobbyAddon} or {@code CustomAction}) only ever get new methods with a default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NonExtendable {
}
