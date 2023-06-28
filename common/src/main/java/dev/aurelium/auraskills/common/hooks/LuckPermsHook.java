package dev.aurelium.auraskills.common.hooks;

import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.data.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

public class LuckPermsHook extends PermissionsHook {

    private final LuckPerms luckPerms;

    public LuckPermsHook(AuraSkillsPlugin plugin) {
        super(plugin);
        this.luckPerms = LuckPermsProvider.get();
    }

    @Override
    public void setPermission(PlayerData playerData, String permission, boolean value) {
        luckPerms.getUserManager().modifyUser(playerData.getUuid(), user ->
                user.data().add(Node.builder(permission).value(value).build()));
    }

    @Override
    public void unsetPermission(PlayerData playerData, String permission, boolean value) {
        luckPerms.getUserManager().modifyUser(playerData.getUuid(), user ->
                user.data().remove(Node.builder(permission).value(value).build()));
    }


}