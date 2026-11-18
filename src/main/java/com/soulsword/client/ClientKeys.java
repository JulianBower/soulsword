package com.soulsword.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class ClientKeys {
    public static final KeyMapping SWITCH_MODE = new KeyMapping(
            "key.soul_sword.switch_mode", InputConstants.KEY_X, "key.categories.soul_sword");

    private ClientKeys() {}
}
