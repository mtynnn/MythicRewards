package cn.superiormc.mythicrewards.managers;

import cn.superiormc.mythicrewards.MythicRewards;
import cn.superiormc.mythicrewards.objects.LicenseType;
import cn.superiormc.mythicrewards.utils.CommonUtil;
import cn.superiormc.mythicrewards.utils.TextUtil;

import java.io.File;

public class InitManager {

    public static InitManager initManager;

    private boolean firstLoad = false;

    public InitManager() {
        initManager = this;
        File file = new File(MythicRewards.instance.getDataFolder(), "config.yml");
        if (!file.exists()) {
            MythicRewards.instance.saveDefaultConfig();
            firstLoad = true;
        }
        init();
    }

    public void init() {
        resourceOutput("rules/All.yml", false);
        resourceOutput("rules/MultiPack.yml", false);
        resourceOutput("rules/Rank.yml", false);
        resourceOutput("rules/Pack.yml", false);
        resourceOutput("rules/Separate.yml", false);
        resourceOutput("rules/SeparatePack.yml", false);
        resourceOutput("languages/en_US.yml", true);
        resourceOutput("languages/zh_CN.yml", true);
    }

    private void resourceOutput(String fileName, boolean regenerate) {
        File tempVal1 = new File(MythicRewards.instance.getDataFolder(), fileName);
        if (!tempVal1.exists()) {
            if (!firstLoad && !regenerate) {
                return;
            }
            File tempVal2 = new File(fileName);
            if (tempVal2.getParentFile() != null) {
                CommonUtil.mkDir(tempVal2.getParentFile());
            }
            MythicRewards.instance.saveResource(tempVal2.getPath(), false);
        }
    }

    public boolean isFirstLoad() {
        return firstLoad;
    }
}
