package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import org.jetbrains.annotations.Nullable;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;

/**
 * {@code %luckperms_...%}, same names as the LuckPerms PlaceholderAPI expansion: {@code prefix},
 * {@code suffix}, {@code primary_group_name}, {@code meta_<key>} and {@code has_permission_<permission>}.
 *
 * <p>Works with LuckPerms and with the operators list (which has no prefixes, so those are empty).
 * Values are cached per player until LuckPerms reports a change.
 */
final class LuckPermsNamespace implements PlaceholderNamespace {

    private static final String META = "meta_";
    private static final String HAS_PERMISSION = "has_permission_";

    private final PermissionService permissions;

    LuckPermsNamespace(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "prefix" -> Placeholder.formattedPlayer(player -> permissions.meta(player).prefix());
            case "suffix" -> Placeholder.formattedPlayer(player -> permissions.meta(player).suffix());
            case "primary_group_name" -> Placeholder.player(player -> permissions.meta(player).primaryGroup());
            default -> {
                if (params.startsWith(META) && params.length() > META.length()) {
                    String key = params.substring(META.length());
                    yield Placeholder.formattedPlayer(player -> permissions.meta(player).meta().getOrDefault(key, ""));
                }
                if (params.startsWith(HAS_PERMISSION) && params.length() > HAS_PERMISSION.length()) {
                    String permission = params.substring(HAS_PERMISSION.length());
                    yield Placeholder.player(player -> BuiltinPlaceholders.yesNo(permissions.hasPermission(player, permission)));
                }
                yield null;
            }
        };
    }
}
