package com.trolmastercard.sexmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Client action menu for port NPCs; selected actions are validated on the server. */
public final class PortActionGui extends GuiScreen {
    private static final int PAGE_SIZE = 8;
    private final PortNpc npc;
    private final List<Option> options = new ArrayList<Option>();
    private int page;
    private int pageStart;
    private int visibleOptions;

    public PortActionGui(PortNpc npc) {
        this.npc = npc;
        this.createOptions();
    }

    private void createOptions() {
        PortNpc.Type type = this.npc.getPortType();
        if (type == PortNpc.Type.JENNY) {
            this.options.add(new Option("blowjob", "Blowjob (3 Emeralds)"));
            this.options.add(new Option("boobjob", "Boobjob (2 Ender Pearls)"));
            this.options.add(new Option("doggy", "Doggy (2 Diamonds)"));
            this.options.add(this.npc.isDressed()
                    ? new Option("strip", "Unclothe (1 Gold Ingot)")
                    : new Option("dress", "Dress up (free)"));
            this.options.add(new Option("auto", "Stop / Resume idle"));
            return;
        }

        Set<String> coveredClips = new HashSet<String>();
        if (this.npc.hasOutfitVariants()) {
            this.options.add(this.npc.isDressed()
                    ? new Option("strip", "Strip")
                    : new Option("dress", "Dress up (free)"));
        }
        switch (type) {
            case ELLIE:
                this.addAction("ellie_missionary", "Missionary", coveredClips);
                this.addAction("ellie_cowgirl", "Cowgirl", coveredClips);
                break;
            case BIA:
                this.addAction("talk_horny", "Talk", coveredClips);
                this.addAction("headpat", "Headpat", coveredClips);
                this.addAction("bia_anal", "Anal", coveredClips);
                this.addAction("bia_doggy", "Doggy", coveredClips);
                break;
            case BEE:
                this.addAction("sex_slow", "Sex", coveredClips);
                break;
            case CAT:
            case LUNA:
                this.addAction("sex", "Sex (3 Fish)", coveredClips);
                this.addAction("touchboobs", "Touch breasts (2 Fish)", coveredClips);
                this.addAction("headpat", "Headpat", coveredClips);
                break;
            case ALLIE:
                this.addAction("makemerichallie", "Make me rich", coveredClips);
                this.addAction("deepthroat", "Deepthroat", coveredClips);
                this.addAction("reversecowgirl", "Reverse cowgirl", coveredClips);
                break;
            case KOBOLD:
                this.addAction("kobold_anal", "Anal (3 Gold Ingots)", coveredClips);
                this.addAction("kobold_oral", "Oral (Iron Pickaxe)", coveredClips);
                this.addAction("kobold_mating", "Mating", coveredClips);
                break;
            case GALATH:
                this.addAction("galath_cowgirl", "Cowgirl", coveredClips);
                this.addAction("galath_anal", "Anal", coveredClips);
                this.addAction("galath_threesome", "Threesome", coveredClips);
                this.addAction("galath_ride", "Ride", coveredClips);
                break;
            default:
                break;
        }
        for (String name : PortAnimationFile.load(type.animations).getNames()) {
            int separator = name.lastIndexOf('.');
            String shortName = separator < 0 ? name : name.substring(separator + 1);
            if (!"idle".equals(shortName) && !"walk".equals(shortName) && !"null".equals(shortName)
                    && !coveredClips.contains(shortName.toLowerCase())) {
                this.options.add(new Option(shortName, shortName));
            }
        }
        this.options.add(new Option("auto", "Resume idle / walking"));
    }

    private void addAction(String action, String label, Set<String> coveredClips) {
        this.options.add(new Option(action, label));
        String clip = PortActionGui.actionClip(this.npc.getPortType(), action);
        if (clip != null) coveredClips.add(clip.toLowerCase());
    }

    private static String actionClip(PortNpc.Type type, String action) {
        if (type == PortNpc.Type.LUNA) {
            if ("sex".equals(action)) return "sitting_slow";
            if ("touchboobs".equals(action)) return "touch_boobs_slow";
            if ("headpat".equals(action)) return "head_pat";
        }
        if (type == PortNpc.Type.CAT) {
            if ("sex".equals(action)) return "sitting_slow";
            if ("touchboobs".equals(action)) return "touch_boobs_slow";
            if ("headpat".equals(action)) return "head_pat";
        }
        if (type == PortNpc.Type.ALLIE) {
            if ("makemerichallie".equals(action)) return "rich";
            if ("deepthroat".equals(action)) return "deepthroat_slow";
            if ("reversecowgirl".equals(action)) return "reverse_cowgirl_slow1";
        }
        if (type == PortNpc.Type.ELLIE) {
            if ("ellie_missionary".equals(action)) return "missionary_slow";
            if ("ellie_cowgirl".equals(action)) return "cowgirlslow2";
        }
        if (type == PortNpc.Type.BIA) {
            if ("bia_anal".equals(action)) return "anal_slow";
            if ("bia_doggy".equals(action)) return "prone_doggy_soft";
        }
        if (type == PortNpc.Type.KOBOLD) {
            if ("kobold_anal".equals(action)) return "analSoft";
            if ("kobold_oral".equals(action)) return "blowjobSlowL";
            if ("kobold_mating".equals(action)) return "mating_press_soft";
        }
        if (type == PortNpc.Type.GALATH) {
            if ("galath_cowgirl".equals(action)) return "bed_slow";
            if ("galath_anal".equals(action)) return "corrupt_slow";
            if ("galath_threesome".equals(action)) return "double_holding_slow";
            if ("galath_ride".equals(action)) return "bed_soft";
        }
        return action;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.pageStart = this.page * PAGE_SIZE;
        this.visibleOptions = Math.min(PAGE_SIZE, this.options.size() - this.pageStart);
        int x = this.width / 2 - 120;
        int y = 48;
        for (int i = 0; i < this.visibleOptions; i++) {
            Option option = this.options.get(this.pageStart + i);
            this.buttonList.add(new GuiButton(i, x, y, 240, 20, option.label));
            y += 22;
        }
        int footerY = this.height - 28;
        if (this.page > 0) this.buttonList.add(new GuiButton(1000, x, footerY, 75, 20, "Previous"));
        if (this.pageStart + this.visibleOptions < this.options.size()) {
            this.buttonList.add(new GuiButton(1001, x + 82, footerY, 75, 20, "Next"));
        }
        this.buttonList.add(new GuiButton(1002, x + 165, footerY, 75, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1000) {
            this.page--;
            this.initGui();
        } else if (button.id == 1001) {
            this.page++;
            this.initGui();
        } else if (button.id == 1002) {
            this.mc.displayGuiScreen(null);
        } else if (button.id >= 0 && button.id < this.visibleOptions) {
            Option option = this.options.get(this.pageStart + button.id);
            this.mc.thePlayer.sendChatMessage("/sexmod " + this.npc.getEntityId() + " " + option.action);
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, this.npc.getCommandSenderName() + " — Actions", this.width / 2, 20, 0xFFFFFF);
        if (this.npc.getPortType() == PortNpc.Type.JENNY) {
            this.drawCenteredString(this.fontRendererObj, "Right-click to open; payment is taken when you choose an action.", this.width / 2, 34, 0xD0D0D0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static final class Option {
        final String action;
        final String label;

        Option(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }
}
