package com.trolmastercard.sexmod;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override
    public void registerRenderers() {
        for (PortNpc.Type type : PortNpc.Type.values()) {
            RenderingRegistry.registerEntityRenderingHandler(type.entityClass, new PortNpcRenderer(type));
        }
    }

    @Override
    public void openNpcMenu(PortNpc npc, EntityPlayer player) {
        Minecraft.getMinecraft().displayGuiScreen(new PortActionGui(npc));
    }
}
