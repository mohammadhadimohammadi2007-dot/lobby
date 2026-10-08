package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.minestom.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * A second, independent LuckPerms instance for {@link LuckPermsLiveIT}, started in its own JVM, the same way
 * LuckPerms on a Paper server or the proxy would be: same tables, SQL messaging. It gives a player the
 * {@code vip} group and tells the network, exactly like {@code /lp user <name> parent set vip} would.
 *
 * <p>Arguments: host port database user password tablePrefix playerUuid playerName
 */
public final class SecondLuckPermsInstance {

    static final String GROUP = "vip";
    static final String PREFIX = "&6[VIP] ";
    static final String PERMISSION = "lobby.command.info";
    static final String DONE = "SECOND_INSTANCE_DONE";

    private SecondLuckPermsInstance() {
    }

    public static void main(String[] args) throws Exception {
        IntegrationsConfig.Database database = new IntegrationsConfig.Database(
                args[0], Integer.parseInt(args[1]), args[2], args[3], args[4], 2);
        IntegrationsConfig.LuckPerms settings = new IntegrationsConfig.LuckPerms(true, "other-server", args[5], "sql");
        UUID playerId = UUID.fromString(args[6]);

        MinecraftServer.init();
        Path dataDir = Files.createTempDirectory("second-luckperms");
        LuckPerms luckPerms = LuckPermsMinestom.builder(dataDir)
                .configurationAdapter(plugin -> new LobbyConfigAdapter(plugin, database, settings))
                .enable();

        Group group = luckPerms.getGroupManager().createAndLoadGroup(GROUP).join();
        group.data().add(PrefixNode.builder(PREFIX, 100).build());
        group.data().add(Node.builder(PERMISSION).build());
        luckPerms.getGroupManager().saveGroup(group).join();
        // A group change is announced as a full update, like /lp does.
        luckPerms.getMessagingService().orElseThrow().pushUpdate();

        User user = luckPerms.getUserManager().loadUser(playerId, args[7]).join();
        // Same as /lp user <name> parent set vip: add the group and make it the stored primary group.
        user.data().add(InheritanceNode.builder(GROUP).build());
        user.setPrimaryGroup(GROUP);
        luckPerms.getUserManager().saveUser(user).join();
        luckPerms.getMessagingService().orElseThrow().pushUserUpdate(user);

        System.out.println(DONE);
        LuckPermsMinestom.disable();
        System.exit(0);
    }
}
