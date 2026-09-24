package com.trolmastercard.sexmod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.MathHelper;

public final class NpcSpawnItem extends Item {
    private final PortNpc.Type type;

    public NpcSpawnItem(PortNpc.Type type) {
        this.type = type;
        this.setUnlocalizedName("sexmod.spawn_" + type.entityName);
        this.setTextureName("minecraft:spawn_egg");
        this.setCreativeTab(CreativeTabs.tabMisc);
        this.setMaxStackSize(16);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "Spawn " + this.type.displayName;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (side == 0) --y;
        if (side == 1) ++y;
        if (side == 2) --z;
        if (side == 3) ++z;
        if (side == 4) --x;
        if (side == 5) ++x;

        if (!world.isRemote) {
            try {
                PortNpc npc = this.type.entityClass.getConstructor(World.class).newInstance(world);
                npc.setLocationAndAngles(x + 0.5D, y, z + 0.5D, MathHelper.wrapAngleTo180_float(world.rand.nextFloat() * 360.0F), 0.0F);
                if (world.spawnEntityInWorld(npc) && !player.capabilities.isCreativeMode) --stack.stackSize;
            } catch (Exception exception) {
                throw new RuntimeException("Could not create " + this.type.displayName, exception);
            }
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return pass == 0 ? this.type.eggColor : 0xF1F1F1;
    }

}
