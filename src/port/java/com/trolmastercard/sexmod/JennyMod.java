package com.trolmastercard.sexmod;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;

@Mod(modid = JennyMod.MOD_ID, name = "Jenny Mod 1.7.10 Port", version = "1.1.0-1.7.10", acceptableRemoteVersions = "*")
public final class JennyMod {
    public static final String MOD_ID = "sexmod";

    @Mod.Instance(MOD_ID)
    public static JennyMod instance;

    @SidedProxy(clientSide = "com.trolmastercard.sexmod.ClientProxy", serverSide = "com.trolmastercard.sexmod.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        int id = 0;
        for (PortNpc.Type type : PortNpc.Type.values()) {
            EntityRegistry.registerModEntity(type.entityClass, type.entityName, id++, this, 80, 3, true);
            GameRegistry.registerItem(new NpcSpawnItem(type), "spawn_" + type.entityName);
        }
        EntityRegistry.addSpawn(PortNpc.Slime.class, 10, 1, 1, EnumCreatureType.creature, new BiomeGenBase[]{BiomeGenBase.swampland});
        EntityRegistry.addSpawn(PortNpc.Bee.class, 5, 1, 1, EnumCreatureType.creature, new BiomeGenBase[]{BiomeGenBase.forest, BiomeGenBase.forestHills});
        EntityRegistry.addSpawn(PortNpc.Manglelie.class, 5, 1, 1, EnumCreatureType.creature, new BiomeGenBase[]{BiomeGenBase.hell});
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.registerRenderers();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PortAnimationCommand());
    }
}
