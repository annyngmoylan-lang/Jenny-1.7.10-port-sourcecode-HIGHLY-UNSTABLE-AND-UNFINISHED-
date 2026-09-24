package com.trolmastercard.sexmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

/** Selects one of the original animation clips on a loaded port NPC. */
public final class PortAnimationCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "sexmod";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sexmod <entity-id|nearest> <animation|auto|list|strip|dress|blowjob|boobjob|doggy>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) throw new CommandException(this.getCommandUsage(sender));
        int entityId = -1;
        Entity entity;
        if ("nearest".equalsIgnoreCase(args[0])) {
            entity = findNearestNpc(sender);
            if (entity == null) throw new CommandException("No port NPC found within 16 blocks");
        } else {
            entityId = parseInt(sender, args[0]);
            entity = sender.getEntityWorld().getEntityByID(entityId);
        }
        if (!(entity instanceof PortNpc)) throw new CommandException("Entity id " + entityId + " is not a loaded port NPC");
        PortNpc npc = (PortNpc)entity;
        if (sender instanceof EntityPlayer && ((EntityPlayer)sender).getDistanceSqToEntity(npc) > 256.0D) {
            throw new CommandException("Move within 16 blocks of the NPC first");
        }

        String requested = args[1].toLowerCase();
        if (sender instanceof EntityPlayer && this.processAction((EntityPlayer)sender, npc, requested)) return;

        PortAnimationFile animationFile = PortAnimationFile.load(npc.getPortType().animations);
        String targetName = entityId < 0 ? "nearest" : Integer.toString(entityId);
        if ("list".equals(requested)) {
            List<String> names = new ArrayList<String>();
            for (String key : animationFile.getNames()) {
                int separator = key.lastIndexOf('.');
                names.add(separator < 0 ? key : key.substring(separator + 1));
            }
            sender.addChatMessage(new ChatComponentText("Animations: " + join(names)));
            return;
        }
        if ("auto".equals(requested)) {
            npc.setAnimationMode("auto");
            sender.addChatMessage(new ChatComponentText(npc.getPortType().displayName + " returned to idle/walk animation."));
            return;
        }
        String resolved = animationFile.resolve(requested);
        if (resolved == null) throw new CommandException("Unknown animation '" + requested + "'. Use /sexmod " + targetName + " list");
        npc.setAnimationMode(resolved);
        sender.addChatMessage(new ChatComponentText("Playing " + resolved + " on " + npc.getPortType().displayName));
    }

    private boolean processAction(EntityPlayer player, PortNpc npc, String requested) throws CommandException {
        if ("strip".equals(requested)) {
            if (!npc.hasOutfitVariants()) throw new CommandException("This character has no alternate outfit model");
            if (!npc.isDressed()) throw new CommandException(npc.getPortType().displayName + " is already unclothed");
            PortAnimationFile file = PortAnimationFile.load(npc.getPortType().animations);
            String strip = file.resolve("strip");
            if (strip == null) throw new CommandException("No strip animation is available for this character");
            if (npc.getPortType() == PortNpc.Type.JENNY) this.charge(player, Items.gold_ingot, 1, "1 gold ingot");
            npc.setOutfitTargetAfterAnimation(2);
            npc.setQueuedAnimation(null);
            npc.setAnimationMode(strip);
            player.addChatMessage(new ChatComponentText("Playing the strip animation on " + npc.getPortType().displayName));
            return true;
        }
        if ("dress".equals(requested) || "dressup".equals(requested)) {
            if (!npc.hasOutfitVariants()) throw new CommandException("This character has no alternate outfit model");
            if (npc.isDressed()) throw new CommandException(npc.getPortType().displayName + " is already dressed");
            npc.setDressed(true);
            npc.setOutfitTargetAfterAnimation(0);
            npc.setQueuedAnimation(null);
            npc.setAnimationMode("auto");
            player.addChatMessage(new ChatComponentText(npc.getPortType().displayName + " is dressed again."));
            return true;
        }

        if ("blowjob".equals(requested) || "boobjob".equals(requested) || "doggy".equals(requested)) {
            if (npc.getPortType() != PortNpc.Type.JENNY) {
                throw new CommandException("That action is not available for this character");
            }
            Item costItem;
            int cost;
            String clip;
            if ("blowjob".equals(requested)) {
                costItem = Items.emerald;
                cost = 3;
                clip = "blowjobsuck";
            } else if ("boobjob".equals(requested)) {
                costItem = Items.ender_pearl;
                cost = 2;
                clip = "paizuri_slow";
            } else {
                costItem = Items.diamond;
                cost = 2;
                clip = "doggyslow";
            }
            PortAnimationFile file = PortAnimationFile.load(npc.getPortType().animations);
            String resolved = file.resolve(clip);
            if (resolved == null) throw new CommandException("The selected animation is missing from the port");
            String price = cost + " " + (costItem == Items.ender_pearl ? "ender pearls" : costItem.getItemStackDisplayName(new ItemStack(costItem, 1)) + "s");
            this.charge(player, costItem, cost, price);
            if (npc.isDressed()) {
                String strip = file.resolve("strip");
                if (strip == null) throw new CommandException("Jenny's strip animation is missing from the port");
                npc.setOutfitTargetAfterAnimation(2);
                npc.setQueuedAnimation(resolved);
                npc.setAnimationMode(strip);
            } else {
                npc.setOutfitTargetAfterAnimation(0);
                npc.setQueuedAnimation(null);
                npc.setAnimationMode(resolved);
            }
            player.addChatMessage(new ChatComponentText("Selected " + requested + " for Jenny."));
            return true;
        }
        if (this.processMappedAction(player, npc, requested)) return true;
        return false;
    }

    private boolean processMappedAction(EntityPlayer player, PortNpc npc, String requested) throws CommandException {
        PortNpc.Type type = npc.getPortType();
        String clip = null;
        Item item = null;
        int amount = 0;
        int metadata = -1;
        String price = "";
        boolean stripFirst = false;

        if (type == PortNpc.Type.ELLIE) {
            if ("ellie_missionary".equals(requested)) { clip = "missionary_slow"; stripFirst = true; }
            if ("ellie_cowgirl".equals(requested)) { clip = "cowgirlslow2"; stripFirst = true; }
        } else if (type == PortNpc.Type.BIA) {
            if ("bia_anal".equals(requested)) { clip = "anal_slow"; stripFirst = true; }
            if ("bia_doggy".equals(requested)) { clip = "prone_doggy_soft"; stripFirst = true; }
        } else if (type == PortNpc.Type.CAT || type == PortNpc.Type.LUNA) {
            if ("sex".equals(requested)) {
                clip = "sitting_slow"; item = Items.fish; amount = 3; metadata = 0; price = "3 raw fish";
            } else if ("touchboobs".equals(requested)) {
                clip = "touch_boobs_slow"; item = Items.fish; amount = 2; metadata = 1; price = "2 salmon";
            } else if ("headpat".equals(requested)) {
                clip = "head_pat";
            }
        } else if (type == PortNpc.Type.ALLIE) {
            if ("makemerichallie".equals(requested)) clip = "rich";
            else if ("deepthroat".equals(requested)) clip = "deepthroat_slow";
            else if ("reversecowgirl".equals(requested)) clip = "reverse_cowgirl_slow1";
        } else if (type == PortNpc.Type.KOBOLD) {
            if ("kobold_anal".equals(requested)) {
                clip = "analSoft"; item = Items.gold_ingot; amount = 3; price = "3 gold ingots";
            } else if ("kobold_oral".equals(requested)) {
                clip = "blowjobSlowL"; item = Items.iron_pickaxe; amount = 1; price = "an iron pickaxe";
            } else if ("kobold_mating".equals(requested)) {
                clip = "mating_press_soft";
            }
        } else if (type == PortNpc.Type.GALATH) {
            if ("galath_cowgirl".equals(requested)) clip = "bed_slow";
            else if ("galath_anal".equals(requested)) clip = "corrupt_slow";
            else if ("galath_threesome".equals(requested)) clip = "double_holding_slow";
            else if ("galath_ride".equals(requested)) clip = "bed_soft";
        }

        if (clip == null) return false;
        PortAnimationFile file = PortAnimationFile.load(type.animations);
        String resolved = file.resolve(clip);
        if (resolved == null) throw new CommandException("The selected action animation is missing from the port: " + clip);
        if (item != null) this.charge(player, item, amount, metadata, price);
        if (stripFirst && npc.isDressed() && npc.hasOutfitVariants()) {
            String strip = file.resolve("strip");
            if (strip == null) throw new CommandException("The outfit transition animation is missing from the port");
            npc.setOutfitTargetAfterAnimation(2);
            npc.setQueuedAnimation(resolved);
            npc.setAnimationMode(strip);
        } else {
            npc.setOutfitTargetAfterAnimation(0);
            npc.setQueuedAnimation(null);
            npc.setAnimationMode(resolved);
        }
        player.addChatMessage(new ChatComponentText("Selected " + requested + " for " + type.displayName + "."));
        return true;
    }

    private void charge(EntityPlayer player, Item item, int amount, String display) throws CommandException {
        this.charge(player, item, amount, -1, display);
    }

    private void charge(EntityPlayer player, Item item, int amount, int metadata, String display) throws CommandException {
        if (player.capabilities.isCreativeMode) return;
        int found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == item && (metadata < 0 || stack.getMetadata() == metadata)) found += stack.stackSize;
        }
        if (found < amount) throw new CommandException("You need " + display + " to choose that action");
        int remaining = amount;
        for (int slot = 0; slot < player.inventory.mainInventory.length && remaining > 0; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            if (stack == null || stack.getItem() != item || (metadata >= 0 && stack.getMetadata() != metadata)) continue;
            int removed = Math.min(remaining, stack.stackSize);
            stack.stackSize -= removed;
            remaining -= removed;
            if (stack.stackSize <= 0) player.inventory.mainInventory[slot] = null;
        }
        player.inventory.markDirty();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private static Entity findNearestNpc(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayer)) throw new CommandException("The nearest option can only be used by a player");
        EntityPlayer player = (EntityPlayer)sender;
        AxisAlignedBB range = player.boundingBox.expand(16.0D, 16.0D, 16.0D);
        List<?> candidates = player.worldObj.getEntitiesWithinAABB(PortNpc.class, range);
        PortNpc nearest = null;
        double distance = Double.MAX_VALUE;
        for (Object candidate : candidates) {
            PortNpc npc = (PortNpc)candidate;
            double candidateDistance = player.getDistanceSqToEntity(npc);
            if (candidateDistance < distance) {
                distance = candidateDistance;
                nearest = npc;
            }
        }
        return nearest;
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }
}
