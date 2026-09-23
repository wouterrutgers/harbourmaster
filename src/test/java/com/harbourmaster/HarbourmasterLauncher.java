package com.harbourmaster;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HarbourmasterLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(HarbourmasterPlugin.class);
        RuneLite.main(args);
    }
}
