package com.trolmastercard.sexmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/** Renders the original Bedrock geometry in GeckoLib's coordinate convention. */
public final class PortGeoModel extends ModelBase {
    private final String geometryPath;
    private final String animationPath;
    private Bone[] roots;
    private PortAnimationFile animations;
    private float textureWidth = 64.0F;
    private float textureHeight = 64.0F;
    private boolean loaded;

    public PortGeoModel(String geometryPath, String animationPath) {
        this.geometryPath = geometryPath;
        this.animationPath = animationPath;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!this.loaded) this.loadGeometry();
        if (this.roots == null) return;
        if (this.animations == null) this.animations = PortAnimationFile.load(this.animationPath);
        String animationName = null;
        float animationTime;
        if (entity instanceof PortNpc) {
            PortNpc npc = (PortNpc)entity;
            String mode = npc.getAnimationMode();
            if ("auto".equals(mode)) {
                String requested = limbSwingAmount > 0.08F ? "walk" : "idle";
                animationName = this.animations.resolve(requested);
                if (animationName == null && "walk".equals(requested)) animationName = this.animations.resolve("run");
                animationTime = (npc.ticksExisted + ageInTicks - npc.ticksExisted) / 20.0F;
            } else {
                animationName = this.animations.resolve(mode);
                animationTime = npc.getAnimationElapsedTicks(ageInTicks - npc.ticksExisted) / 20.0F;
            }
        } else {
            animationTime = ageInTicks / 20.0F;
        }
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        GL11.glDisable(GL11.GL_CULL_FACE);
        for (Bone root : this.roots) {
            root.render(this.textureWidth, this.textureHeight, limbSwing, limbSwingAmount, netHeadYaw, headPitch,
                    this.animations, animationName, animationTime);
        }
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    private void loadGeometry() {
        this.loaded = true;
        String resource = "/assets/sexmod/geo/" + this.geometryPath;
        InputStream stream = PortGeoModel.class.getResourceAsStream(resource);
        if (stream == null) {
            System.err.println("[sexmod] Missing port geometry: " + resource);
            return;
        }
        try {
            JsonObject file = new JsonParser().parse(new InputStreamReader(stream, "UTF-8")).getAsJsonObject();
            JsonArray geometries = file.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.size() == 0) return;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description != null) {
                this.textureWidth = number(description, "texture_width", 64.0F);
                this.textureHeight = number(description, "texture_height", 64.0F);
            }
            JsonArray boneArray = geometry.getAsJsonArray("bones");
            Map<String, Bone> bones = new LinkedHashMap<String, Bone>();
            List<Bone> ordered = new ArrayList<Bone>();
            for (JsonElement element : boneArray) {
                Bone bone = Bone.read(element.getAsJsonObject());
                bones.put(bone.name, bone);
                ordered.add(bone);
            }
            List<Bone> rootsList = new ArrayList<Bone>();
            for (Bone bone : ordered) {
                Bone parent = bone.parent == null ? null : bones.get(bone.parent);
                if (parent == null) rootsList.add(bone);
                else parent.children.add(bone);
            }
            this.roots = rootsList.toArray(new Bone[rootsList.size()]);
        } catch (Exception exception) {
            System.err.println("[sexmod] Could not read port geometry " + resource + ": " + exception);
        } finally {
            try { stream.close(); } catch (Exception ignored) { }
        }
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static float[] vector(JsonObject object, String key, float[] fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) return fallback;
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) return fallback;
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static final class Bone {
        String name;
        String parent;
        boolean hidden;
        boolean mirror;
        float inflate;
        float[] pivot = new float[]{0.0F, 0.0F, 0.0F};
        float[] rotation = new float[]{0.0F, 0.0F, 0.0F};
        final List<Cube> cubes = new ArrayList<Cube>();
        final List<Bone> children = new ArrayList<Bone>();

        static Bone read(JsonObject object) {
            Bone bone = new Bone();
            bone.name = object.get("name").getAsString();
            bone.parent = object.has("parent") ? object.get("parent").getAsString() : null;
            bone.hidden = isHiddenByDefault(bone.name);
            bone.mirror = object.has("mirror") && object.get("mirror").getAsBoolean();
            bone.inflate = number(object, "inflate", 0.0F);
            bone.pivot = vector(object, "pivot", bone.pivot);
            bone.rotation = vector(object, "rotation", bone.rotation);
            if (object.has("cubes")) {
                for (JsonElement element : object.getAsJsonArray("cubes")) {
                    bone.cubes.add(Cube.read(element.getAsJsonObject(), bone.pivot, bone.mirror, bone.inflate));
                }
            }
            return bone;
        }

        void render(float textureWidth, float textureHeight, float limbSwing, float limbSwingAmount, float headYaw, float headPitch,
                    PortAnimationFile animations, String animationName, float animationTime) {
            if (this.hidden) return;
            GL11.glPushMatrix();

            PortAnimationFile.BonePose pose = animationName == null ? PortAnimationFile.BonePose.EMPTY
                    : animations.sample(animationName, this.name, animationTime);
            float[] actualRotation = pose.rotation == null ? this.rotation : pose.rotation;
            if (pose.position != null) GL11.glTranslatef(-pose.position[0], pose.position[1], pose.position[2]);

            // GeoBuilder negates X pivots and X/Y rotations before rendering.
            GL11.glTranslatef(-this.pivot[0], this.pivot[1], this.pivot[2]);
            float xRotation = -actualRotation[0];
            float yRotation = -actualRotation[1];
            float zRotation = actualRotation[2];
            String lowerName = this.name.toLowerCase();
            float swing = (float)Math.cos(limbSwing * 0.6662F) * limbSwingAmount;
            if (animationName == null && (lowerName.contains("leg") || lowerName.contains("thigh"))) {
                xRotation += swing * (lowerName.endsWith("l") || lowerName.contains("left") ? 35.0F : -35.0F);
            } else if (animationName == null && (lowerName.contains("shin") || lowerName.contains("lowerleg"))) {
                xRotation += Math.max(0.0F, -swing) * -22.0F;
            } else if (animationName == null && (lowerName.contains("arm") || lowerName.contains("shoulder"))) {
                xRotation += swing * (lowerName.endsWith("l") || lowerName.contains("left") ? -20.0F : 20.0F);
            }
            if (animationName == null && (lowerName.equals("head") || lowerName.startsWith("head"))) {
                yRotation += headYaw;
                xRotation -= headPitch;
            }

            GL11.glRotatef(zRotation, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(yRotation, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(xRotation, 1.0F, 0.0F, 0.0F);
            if (pose.scale != null) GL11.glScalef(pose.scale[0], pose.scale[1], pose.scale[2]);
            GL11.glTranslatef(this.pivot[0], -this.pivot[1], -this.pivot[2]);

            for (Cube cube : this.cubes) cube.render(textureWidth, textureHeight);
            for (Bone child : this.children) child.render(textureWidth, textureHeight, limbSwing, limbSwingAmount, headYaw, headPitch,
                    animations, animationName, animationTime);
            GL11.glPopMatrix();
        }

        private static boolean isHiddenByDefault(String name) {
            // These are alternate player/item rigs or action-only slime props.
            return "steve".equals(name) || "items".equals(name)
                    || "bedSlime".equals(name) || "bedSlimeLayer".equals(name);
        }
    }

    private static final class Cube {
        float[] origin;
        float[] size;
        float[] pivot;
        float[] rotation;
        boolean mirror;
        boolean mirrorGeometry;
        float inflate;
        JsonObject uv;
        float[] boxUv;

        static Cube read(JsonObject object, float[] parentPivot, boolean parentMirror, float parentInflate) {
            Cube cube = new Cube();
            cube.origin = vector(object, "origin", new float[]{0.0F, 0.0F, 0.0F});
            cube.size = vector(object, "size", new float[]{0.0F, 0.0F, 0.0F});
            cube.pivot = vector(object, "pivot", parentPivot);
            cube.rotation = vector(object, "rotation", new float[]{0.0F, 0.0F, 0.0F});
            cube.mirror = object.has("mirror") && object.get("mirror").getAsBoolean();
            cube.mirrorGeometry = cube.mirror || parentMirror;
            cube.inflate = number(object, "inflate", parentInflate);
            if (object.has("uv") && object.get("uv").isJsonObject()) {
                cube.uv = object.getAsJsonObject("uv");
            } else if (object.has("uv") && object.get("uv").isJsonArray()) {
                JsonArray box = object.getAsJsonArray("uv");
                if (box.size() >= 2) cube.boxUv = new float[]{box.get(0).getAsFloat(), box.get(1).getAsFloat()};
            }
            return cube;
        }

        void render(float textureWidth, float textureHeight) {
            float inflate = this.inflate;
            float x1 = -(this.origin[0] + this.size[0]) - inflate;
            float x2 = x1 + this.size[0] + inflate * 2.0F;
            float y1 = this.origin[1] - inflate;
            float y2 = y1 + this.size[1] + inflate * 2.0F;
            float z1 = this.origin[2] - inflate;
            float z2 = z1 + this.size[2] + inflate * 2.0F;
            float[][] points = {
                {x1,y1,z1}, {x1,y1,z2}, {x1,y2,z1}, {x1,y2,z2},
                {x2,y1,z1}, {x2,y1,z2}, {x2,y2,z1}, {x2,y2,z2}
            };
            GL11.glPushMatrix();
            GL11.glTranslatef(-this.pivot[0], this.pivot[1], this.pivot[2]);
            GL11.glRotatef(this.rotation[2], 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(-this.rotation[1], 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-this.rotation[0], 1.0F, 0.0F, 0.0F);
            GL11.glTranslatef(this.pivot[0], -this.pivot[1], -this.pivot[2]);

            if (this.boxUv != null) {
                float u = this.boxUv[0];
                float v = this.boxUv[1];
                float sx = this.size[0];
                float sy = this.size[1];
                float sz = this.size[2];
                this.faceRect(textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{6,7,5,4} : new int[]{3,2,0,1}, -1.0F,0.0F,0.0F, u+sz+sx,v+sz, sz,sy,0);
                this.faceRect(textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{3,2,0,1} : new int[]{6,7,5,4}, 1.0F,0.0F,0.0F, u,v+sz, sz,sy,0);
                this.faceRect(textureWidth, textureHeight, points, new int[]{2,6,4,0}, 0.0F,0.0F,-1.0F, u+sz,v+sz, sx,sy,0);
                this.faceRect(textureWidth, textureHeight, points, new int[]{7,3,1,5}, 0.0F,0.0F,1.0F, u+sz+sx+sz,v+sz, sx,sy,0);
                this.faceRect(textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{0,4,5,1} : new int[]{3,7,6,2}, 0.0F,1.0F,0.0F, u+sz,v, sx,sz,0);
                this.faceRect(textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{3,7,6,2} : new int[]{0,4,5,1}, 0.0F,-1.0F,0.0F, u+sz+sx,v, sx,sz,0);
            } else if (this.uv != null) {
                this.face("west", textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{6,7,5,4} : new int[]{3,2,0,1}, -1.0F, 0.0F, 0.0F);
                this.face("east", textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{3,2,0,1} : new int[]{6,7,5,4}, 1.0F, 0.0F, 0.0F);
                this.face("north", textureWidth, textureHeight, points, new int[]{2,6,4,0}, 0.0F, 0.0F, -1.0F);
                this.face("south", textureWidth, textureHeight, points, new int[]{7,3,1,5}, 0.0F, 0.0F, 1.0F);
                this.face("up", textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{0,4,5,1} : new int[]{3,7,6,2}, 0.0F, 1.0F, 0.0F);
                this.face("down", textureWidth, textureHeight, points, this.mirrorGeometry ? new int[]{3,7,6,2} : new int[]{0,4,5,1}, 0.0F, -1.0F, 0.0F);
            }
            GL11.glPopMatrix();
        }

        private void faceRect(float textureWidth, float textureHeight, float[][] points, int[] indices,
                              float nx, float ny, float nz, float u, float v, float uSize, float vSize, int uvRotation) {
            float u0 = u / textureWidth;
            float u1 = (u + uSize) / textureWidth;
            float v0 = v / textureHeight;
            float v1 = (v + vSize) / textureHeight;
            this.drawFace(points, indices, nx, ny, nz, rotatedUvs(u0, v0, u1, v1, uvRotation, !this.mirror));
        }

        private void face(String name, float textureWidth, float textureHeight, float[][] points, int[] indices, float nx, float ny, float nz) {
            if (this.uv == null || !this.uv.has(name)) return;
            JsonObject face = this.uv.getAsJsonObject(name);
            float[] uv = pair(face, "uv", new float[]{0.0F, 0.0F});
            float[] uvSize = pair(face, "uv_size", new float[]{0.0F, 0.0F});
            float u0 = uv[0] / textureWidth;
            float u1 = (uv[0] + uvSize[0]) / textureWidth;
            float v0 = uv[1] / textureHeight;
            float v1 = (uv[1] + uvSize[1]) / textureHeight;
            this.drawFace(points, indices, nx, ny, nz, rotatedUvs(u0, v0, u1, v1, number(face, "uv_rotation", 0.0F), !this.mirror));
        }

        private void drawFace(float[][] points, int[] indices, float nx, float ny, float nz, float[] faceUvs) {
            if (points == null || indices == null || faceUvs == null || indices.length < 4 || faceUvs.length < 8) return;
            for (int i = 0; i < 4; i++) {
                int point = indices[i];
                if (point < 0 || point >= points.length || points[point] == null || points[point].length < 3) return;
            }
            if (this.mirror) nx = -nx;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glNormal3f(nx, ny, nz);
            for (int i = 0; i < 4; i++) {
                GL11.glTexCoord2f(faceUvs[i * 2], faceUvs[i * 2 + 1]);
                GL11.glVertex3f(points[indices[i]][0], points[indices[i]][1], points[indices[i]][2]);
            }
            GL11.glEnd();
        }

        private static float[] rotatedUvs(float u, float v, float u2, float v2, float rotationDegrees, boolean mirrorU) {
            float[] base = new float[]{u,v,u2,v,u2,v2,u,v2};
            if (mirrorU) {
                for (int i = 0; i < 8; i += 2) base[i] = base[i] == u ? u2 : u;
            }
            int rotation = (((int)rotationDegrees % 360) + 360) % 360;
            int start = rotation == 90 ? 1 : rotation == 180 ? 2 : rotation == 270 ? 3 : 0;
            float[] result = new float[8];
            for (int vertex = 0; vertex < 4; vertex++) {
                int source = (vertex + start) & 3;
                result[vertex * 2] = base[source * 2];
                result[vertex * 2 + 1] = base[source * 2 + 1];
            }
            return result;
        }

        private static float[] pair(JsonObject object, String key, float[] fallback) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonArray()) return fallback;
            JsonArray array = element.getAsJsonArray();
            if (array.size() < 2) return fallback;
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()};
        }
    }
}
