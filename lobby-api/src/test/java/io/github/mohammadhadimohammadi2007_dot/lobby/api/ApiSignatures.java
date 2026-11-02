package io.github.mohammadhadimohammadi2007_dot.lobby.api;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.Experimental;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.Since;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The public API as lines of text, read from the compiled classes: one line per public type and per public or
 * protected member. Comparing two versions of these lines shows what an API change does to addons.
 *
 * <p>A line ends with {@value #MUST_IMPLEMENT} when addons have to implement it: an abstract method of a type addons
 * may implement or extend (one without {@link NonExtendable}, not sealed or final). Adding such a line breaks every
 * addon that implements the type, so it counts as a breaking change, like removing a line.
 */
final class ApiSignatures {

    static final String MUST_IMPLEMENT = " [addons implement]";
    private static final String PACKAGE = "io.github.mohammadhadimohammadi2007_dot.lobby.api";

    /**
     * One line of the API.
     *
     * @param text         the line, as written to the API file
     * @param experimental marked {@link Experimental} (itself or its type): not part of the compatibility promise
     * @param since        its {@link Since} version (or its type's), or {@code null}
     */
    record Signature(String text, boolean experimental, String since) {
    }

    private ApiSignatures() {
    }

    /** Every line of the API in the compiled classes, sorted. */
    static List<Signature> read() throws IOException {
        List<Signature> lines = new ArrayList<>();
        for (Class<?> type : publicTypes()) {
            lines.addAll(of(type));
        }
        lines.sort(Comparator.comparing(Signature::text));
        return lines;
    }

    private static List<Class<?>> publicTypes() throws IOException {
        Path root;
        try {
            root = Path.of(ApiVersion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/').replace('/', '.');
                name = name.substring(0, name.length() - ".class".length());
                if (name.endsWith("package-info") || !name.startsWith(PACKAGE)) {
                    continue;
                }
                Class<?> type;
                try {
                    type = Class.forName(name, false, ApiVersion.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
                if (visible(type)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    /** True for a public type that addons can reach (nested ones only inside public types). */
    private static boolean visible(Class<?> type) {
        for (Class<?> at = type; at != null; at = at.getDeclaringClass()) {
            if (!Modifier.isPublic(at.getModifiers()) || at.isAnonymousClass() || at.isSynthetic()) {
                return false;
            }
        }
        return true;
    }

    private static List<Signature> of(Class<?> type) {
        boolean typeExperimental = experimental(type);
        String typeSince = since(type);
        boolean implementable = implementable(type);
        String name = type.getTypeName();
        List<Signature> lines = new ArrayList<>();
        lines.add(new Signature(header(type), typeExperimental, typeSince));
        for (Field field : type.getDeclaredFields()) {
            if (member(field.getModifiers()) && !field.isSynthetic()) {
                String text = name + " field " + modifiers(field.getModifiers()) + field.getGenericType().getTypeName()
                        + " " + field.getName();
                lines.add(signature(text, field, typeExperimental, typeSince));
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            // Enum constructors cannot be called from outside, whatever their modifier says.
            if (member(constructor.getModifiers()) && !constructor.isSynthetic() && !type.isEnum()) {
                String text = name + " constructor " + modifiers(constructor.getModifiers()) + "("
                        + types(constructor.getGenericParameterTypes()) + ")" + throwsClause(constructor.getGenericExceptionTypes());
                lines.add(signature(text, constructor, typeExperimental, typeSince));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!member(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            boolean abstractMethod = Modifier.isAbstract(method.getModifiers());
            // abstract/default do not matter to callers; whether addons must implement it does (MUST_IMPLEMENT).
            String modifiers = modifiers(method.getModifiers() & ~Modifier.ABSTRACT);
            String generics = method.getTypeParameters().length == 0 ? "" : "<" + Arrays.stream(method.getTypeParameters())
                    .map(variable -> variable.getName() + bounds(variable.getBounds())).collect(Collectors.joining(", ")) + "> ";
            String text = name + " method " + modifiers + generics + method.getGenericReturnType().getTypeName() + " "
                    + method.getName() + "(" + types(method.getGenericParameterTypes()) + ")"
                    + throwsClause(method.getGenericExceptionTypes())
                    + (abstractMethod && implementable ? MUST_IMPLEMENT : "");
            lines.add(signature(text, method, typeExperimental, typeSince));
        }
        return lines;
    }

    private static String header(Class<?> type) {
        String kind = type.isAnnotation() ? "annotation" : type.isEnum() ? "enum" : type.isRecord() ? "record"
                : type.isInterface() ? "interface" : "class";
        StringBuilder text = new StringBuilder(type.getTypeName()).append(" ").append(kind);
        if (type.getTypeParameters().length > 0) {
            text.append(" <").append(Arrays.stream(type.getTypeParameters())
                    .map(variable -> variable.getName() + bounds(variable.getBounds())).collect(Collectors.joining(", ")))
                    .append(">");
        }
        int modifiers = type.getModifiers() & (Modifier.FINAL | Modifier.ABSTRACT | Modifier.STATIC);
        if (type.isInterface() || type.isEnum() || type.isRecord()) {
            modifiers &= ~(Modifier.ABSTRACT | Modifier.FINAL | Modifier.STATIC);
        }
        if (modifiers != 0) {
            text.append(" ").append(Modifier.toString(modifiers));
        }
        if (type.isSealed()) {
            text.append(" sealed permits ").append(Arrays.stream(type.getPermittedSubclasses()).map(Class::getTypeName)
                    .sorted().collect(Collectors.joining(", ")));
        }
        Type superclass = type.getGenericSuperclass();
        if (superclass != null && superclass != Object.class && !type.isEnum() && !type.isRecord()) {
            text.append(" extends ").append(superclass.getTypeName());
        }
        Type[] interfaces = type.getGenericInterfaces();
        if (interfaces.length > 0 && !type.isAnnotation()) {
            text.append(" implements ").append(types(interfaces));
        }
        if (type.isAnnotationPresent(NonExtendable.class)) {
            text.append(" [lobby implements]");
        }
        return text.toString();
    }

    /** True if addons may implement or extend the type, so its abstract methods are theirs to write. */
    static boolean implementable(Class<?> type) {
        if (type.isAnnotationPresent(NonExtendable.class) || type.isSealed() || type.isEnum() || type.isRecord()
                || type.isAnnotation() || Modifier.isFinal(type.getModifiers())) {
            return false;
        }
        if (type.isInterface()) {
            return true;
        }
        // A class addons can only extend if it has a constructor they can call.
        return Arrays.stream(type.getDeclaredConstructors()).anyMatch(constructor -> member(constructor.getModifiers()));
    }

    private static Signature signature(String text, AnnotatedElement element, boolean typeExperimental, String typeSince) {
        String since = since(element);
        return new Signature(text, typeExperimental || experimental(element), since != null ? since : typeSince);
    }

    private static boolean experimental(AnnotatedElement element) {
        return element.isAnnotationPresent(Experimental.class);
    }

    private static String since(AnnotatedElement element) {
        Since since = element.getAnnotation(Since.class);
        return since == null ? null : since.value();
    }

    private static boolean member(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String modifiers(int modifiers) {
        String text = Modifier.toString(modifiers & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC
                | Modifier.FINAL | Modifier.ABSTRACT));
        return text.isEmpty() ? "" : text + " ";
    }

    private static String types(Type[] types) {
        return Arrays.stream(types).map(Type::getTypeName).collect(Collectors.joining(", "));
    }

    private static String bounds(Type[] bounds) {
        List<Type> real = Arrays.stream(bounds).filter(bound -> bound != Object.class).toList();
        return real.isEmpty() ? "" : " extends " + real.stream().map(Type::getTypeName).collect(Collectors.joining(" & "));
    }

    private static String throwsClause(Type[] exceptions) {
        return exceptions.length == 0 ? "" : " throws " + types(exceptions);
    }
}
