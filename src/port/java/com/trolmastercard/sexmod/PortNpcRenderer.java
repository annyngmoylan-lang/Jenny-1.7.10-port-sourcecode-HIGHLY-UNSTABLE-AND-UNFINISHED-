package com.trolmastercard.sexmod;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** Uses Gecko's world-space transform instead of RenderLiving's vanilla biped flip. */
public final class PortNpcRenderer extends RenderLiving {
    private final PortNpc.Type type;
    private final PortGeoModel dressedModel;
    private final PortGeoModel nudeModel;

    public PortNpcRenderer(PortNpc.Type type) {
        super(new ModelBase() { }, 0.35F);
        this.type = type;
        this.dressedModel = new PortGeoModel(type.geometry, type.animations);
        this.nudeModel = type.nudeGeometry == null ? this.dressedModel : new PortGeoModel(type.nudeGeometry, type.animations);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof PortNpc) || entity.isInvisible()) return;
        PortNpc npc = (PortNpc)entity;
        float bodyYaw = interpolateAngle(npc.prevRenderYawOffset, npc.renderYawOffset, partialTicks);
        float headYaw = interpolateAngle(npc.prevRotationYawHead, npc.rotationYawHead, partialTicks) - bodyYaw;
        headYaw = -MathHelper.wrapAngleTo180_float(headYaw);
        float headPitch = -(npc.prevRotationPitch + (npc.rotationPitch - npc.prevRotationPitch) * partialTicks);
        float limbSwingAmount = npc.prevLimbSwingAmount + (npc.limbSwingAmount - npc.prevLimbSwingAmount) * partialTicks;
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;
        float limbSwing = npc.limbSwing - npc.limbSwingAmount * (1.0F - partialTicks);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            int packedLight = npc.getBrightnessForRender(partialTicks);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    (float)(packedLight & 65535), (float)(packedLight >>> 16));
            GL11.glTranslatef((float)x, (float)y, (float)z);
            GL11.glRotatef(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
            this.bindEntityTexture(entity);
            PortGeoModel model = npc.isDressed() ? this.dressedModel : this.nudeModel;
            model.render(npc, limbSwing, limbSwingAmount, npc.ticksExisted + partialTicks,
                    headYaw, headPitch, 0.0625F);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static float interpolateAngle(float previous, float current, float partialTicks) {
        float difference = current - previous;
        while (difference < -180.0F) difference += 360.0F;
        while (difference >= 180.0F) difference -= 360.0F;
        return previous + difference * partialTicks;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation(JennyMod.MOD_ID, "textures/entity/" + this.type.texture);
    }
}
