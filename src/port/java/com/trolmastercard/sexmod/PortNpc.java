package com.trolmastercard.sexmod;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Shared legacy-Forge entity shell used by the 1.7.10 compatibility port. */
public class PortNpc extends EntityCreature {
    public enum Type {
        JENNY("jenny", "Jenny", "jenny/jennydressed.geo.json", "jenny/jennynude.geo.json", "jenny/jenny.png", "jenny/jenny.animation.json", 0xE6B36D, Jenny.class),
        ELLIE("ellie", "Ellie", "ellie/dressed.geo.json", "ellie/nude.geo.json", "ellie/ellie.png", "ellie/ellie.animation.json", 0xB54545, Ellie.class),
        BIA("bia", "Bia", "bia/biadressed.geo.json", "bia/bianude.geo.json", "bia/bia.png", "bia/bia.animation.json", 0xD48A62, Bia.class),
        BEE("bee", "Bee", "bee/bee.geo.json", null, "bee/bee.png", "bee/bee.animation.json", 0xE3C64C, Bee.class),
        ALLIE("allie", "Allie", "allie/allie.geo.json", null, "allie/allie.png", "allie/allie.animation.json", 0x8C65AD, Allie.class),
        CAT("cat", "Cat", "cat/cat.geo.json", null, "cat/cat.png", "cat/cat.animation.json", 0xBE7E4A, Cat.class),
        GOBLIN("goblin", "Goblin", "goblin/goblin.geo.json", null, "goblin/goblin.png", "goblin/goblin.animation.json", 0x59A342, Goblin.class),
        KOBOLD("kobold", "Kobold", "kobold/kobold.geo.json", null, "kobold/kobold.png", "kobold/kobold.animation.json", 0x8E6950, Kobold.class),
        SLIME("slime", "Slime", "slime/dressed.geo.json", "slime/nude.geo.json", "slime/slime.png", "slime/slime.animation.json", 0x67C89A, Slime.class),
        LUNA("luna", "Luna", "cat/cat.geo.json", null, "cat/cat.png", "cat/cat.animation.json", 0xB99BCB, Luna.class),
        GALATH("galath", "Galath", "galath/galath.geo.json", null, "galath/galath.png", "galath/galath.animation.json", 0xB84343, Galath.class),
        MANGLELIE("manglelie", "Manglelie", "manglelie/manglelie.geo.json", null, "manglelie/manglelie.png", "manglelie/manglelie.animation.json", 0x8B3554, Manglelie.class);

        public final String entityName;
        public final String displayName;
        public final String geometry;
        public final String nudeGeometry;
        public final String texture;
        public final String animations;
        public final int eggColor;
        public final Class<? extends PortNpc> entityClass;

        Type(String entityName, String displayName, String geometry, String nudeGeometry, String texture, String animations, int eggColor, Class<? extends PortNpc> entityClass) {
            this.entityName = entityName;
            this.displayName = displayName;
            this.geometry = geometry;
            this.nudeGeometry = nudeGeometry;
            this.texture = texture;
            this.animations = animations;
            this.eggColor = eggColor;
            this.entityClass = entityClass;
        }
    }

    private final Type type;
    private String observedAnimation = "auto";
    private int animationStartedTick;
    private String cueAnimation = "";
    private float lastCueSeconds = -0.001F;
    private int outfitTargetAfterAnimation;
    private String queuedAnimation;

    protected PortNpc(World world, Type type) {
        super(world);
        this.type = type;
        this.setSize(0.72F, 1.8F);
        this.getNavigator().setAvoidsWater(true);
        this.tasks.addTask(1, new EntityAIWander(this, 0.7D));
        this.tasks.addTask(2, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(3, new EntityAILookIdle(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(30, Integer.valueOf(1));
        this.dataWatcher.addObject(31, "auto");
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        String current = this.getAnimationMode();
        if (!current.equals(this.observedAnimation)) {
            this.observedAnimation = current;
            this.animationStartedTick = this.ticksExisted;
            this.cueAnimation = current;
            this.lastCueSeconds = -0.001F;
        }

        if ("auto".equals(current)) return;
        PortAnimationFile animationFile = PortAnimationFile.load(this.type.animations);
        float elapsedSeconds = (this.ticksExisted - this.animationStartedTick) / 20.0F;
        float clipLength = animationFile.getLength(current);
        boolean loops = animationFile.loops(current);

        if (!this.worldObj.isRemote && clipLength > 0.0F && elapsedSeconds >= clipLength) {
            String followUp = this.queuedAnimation != null ? this.queuedAnimation : this.getActionFollowUp(current);
            if (followUp != null && animationFile.resolve(followUp) != null) {
                if (this.outfitTargetAfterAnimation != 0) {
                    this.setDressed(this.outfitTargetAfterAnimation == 1);
                    this.outfitTargetAfterAnimation = 0;
                }
                this.queuedAnimation = null;
                this.setAnimationMode(followUp);
            } else if (!loops && !animationFile.holdsLastFrame(current)) {
                if (this.outfitTargetAfterAnimation != 0) {
                    this.setDressed(this.outfitTargetAfterAnimation == 1);
                    this.outfitTargetAfterAnimation = 0;
                }
                this.setAnimationMode("auto");
            }
            if (!loops && !animationFile.holdsLastFrame(current) || followUp != null) return;
        }

        if (this.worldObj.isRemote) {
            float sampleSeconds = loops && clipLength > 0.0F ? elapsedSeconds % clipLength : elapsedSeconds;
            if (!current.equals(this.cueAnimation)) {
                this.cueAnimation = current;
                this.lastCueSeconds = -0.001F;
            }
            for (String cue : animationFile.getSoundEvents(current, this.lastCueSeconds, sampleSeconds)) {
                PortCueRegistry.play(this, this.type.entityName, cue, this.rand);
            }
            this.lastCueSeconds = sampleSeconds;
        }
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.25D);
    }

    public Type getPortType() {
        return this.type;
    }

    public boolean isDressed() {
        return this.dataWatcher.getWatchableObjectInt(30) != 0;
    }

    public void setDressed(boolean dressed) {
        this.dataWatcher.updateObject(30, Integer.valueOf(dressed ? 1 : 0));
    }

    public boolean hasOutfitVariants() {
        return this.type.nudeGeometry != null;
    }

    public void setOutfitTargetAfterAnimation(int target) {
        this.outfitTargetAfterAnimation = target;
    }

    public void setQueuedAnimation(String animation) {
        this.queuedAnimation = animation;
    }

    private String getActionFollowUp(String current) {
        if (this.type != Type.JENNY) return null;
        if (current.endsWith("blowjobintro")) return "blowjobsuck";
        if (current.endsWith("paizuri_start")) return "paizuri_slow";
        return null;
    }

    public String getAnimationMode() {
        return this.dataWatcher.getWatchableObjectString(31);
    }

    public void setAnimationMode(String animation) {
        if (animation == null || animation.length() == 0) animation = "auto";
        animation = animation.toLowerCase();
        this.dataWatcher.updateObject(31, animation);
        this.observedAnimation = animation;
        this.animationStartedTick = this.ticksExisted;
        this.cueAnimation = animation;
        this.lastCueSeconds = -0.001F;
    }

    public float getAnimationElapsedTicks(float partialTicks) {
        return this.ticksExisted - this.animationStartedTick + partialTicks;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("PortAnimation", this.getAnimationMode());
        tag.setBoolean("PortDressed", this.isDressed());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("PortAnimation")) this.setAnimationMode(tag.getString("PortAnimation"));
        if (tag.hasKey("PortDressed")) this.setDressed(tag.getBoolean("PortDressed"));
    }

    @Override
    public String getCommandSenderName() {
        return this.hasCustomNameTag() ? super.getCommandSenderName() : this.type.displayName;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        if (this.worldObj.isRemote) JennyMod.proxy.openNpcMenu(this, player);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source == null ? null : source.getEntity();
        // In the original mod these characters enter a downed/action state rather
        // than being removed by a couple of ordinary player hits.
        if (attacker instanceof EntityPlayer) return false;
        return super.attackEntityFrom(source, amount);
    }

    public static final class Jenny extends PortNpc { public Jenny(World w) { super(w, Type.JENNY); } }
    public static final class Ellie extends PortNpc { public Ellie(World w) { super(w, Type.ELLIE); } }
    public static final class Bia extends PortNpc { public Bia(World w) { super(w, Type.BIA); } }
    public static final class Bee extends PortNpc { public Bee(World w) { super(w, Type.BEE); } }
    public static final class Allie extends PortNpc { public Allie(World w) { super(w, Type.ALLIE); } }
    public static final class Cat extends PortNpc { public Cat(World w) { super(w, Type.CAT); } }
    public static final class Goblin extends PortNpc { public Goblin(World w) { super(w, Type.GOBLIN); } }
    public static final class Kobold extends PortNpc { public Kobold(World w) { super(w, Type.KOBOLD); } }
    public static final class Slime extends PortNpc { public Slime(World w) { super(w, Type.SLIME); } }
    public static final class Luna extends PortNpc { public Luna(World w) { super(w, Type.LUNA); } }
    public static final class Galath extends PortNpc { public Galath(World w) { super(w, Type.GALATH); } }
    public static final class Manglelie extends PortNpc { public Manglelie(World w) { super(w, Type.MANGLELIE); } }
}
