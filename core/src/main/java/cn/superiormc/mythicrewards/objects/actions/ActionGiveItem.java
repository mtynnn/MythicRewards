package cn.superiormc.mythicrewards.objects.actions;

import cn.superiormc.mythicrewards.MythicRewards;
import cn.superiormc.mythicrewards.listeners.TrackerResult;
import cn.superiormc.mythicrewards.managers.ConfigManager;
import cn.superiormc.mythicrewards.methods.BuildItem;
import cn.superiormc.mythicrewards.utils.CommonUtil;
import cn.superiormc.mythicrewards.utils.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ActionGiveItem extends AbstractRunAction {

    public ActionGiveItem() {
        super("give_item");
        setRequiredArgs("item");
    }

    @Override
    protected void onDoAction(ObjectSingleAction singleAction, Player player, TrackerResult result) {
        if (MythicRewards.freeVersion && ConfigManager.configManager.getEntityMatchMap().size() > 4) {
            TextUtil.sendMessage(null, TextUtil.pluginPrefix() + " §cError: Free version can not use give_item or drop_item action when load more than 4 reward rule configs!");
        }
        ItemStack item = BuildItem.buildItemStack(player, singleAction.getSection().getConfigurationSection("item"));
        CommonUtil.giveOrDrop(player, item);
    }
}
