package com.trolmastercard.sexmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads and samples the Bedrock animation JSON shipped with each character. */
public final class PortAnimationFile {
    private static final Map<String, PortAnimationFile> CACHE = new HashMap<String, PortAnimationFile>();
    private final Map<String, Clip> clips = new LinkedHashMap<String, Clip>();
    private final List<String> names = new ArrayList<String>();

    public static synchronized PortAnimationFile load(String path) {
        PortAnimationFile cached = CACHE.get(path);
        if (cached != null) return cached;
        PortAnimationFile parsed = new PortAnimationFile();
        parsed.read(path);
        CACHE.put(path, parsed);
        return parsed;
    }

    public List<String> getNames() {
        return Collections.unmodifiableList(this.names);
    }

    public String resolve(String requested) {
        if (requested == null || requested.length() == 0) return null;
        String key = requested.toLowerCase();
        if (this.clips.containsKey(key)) return key;
        for (String candidate : this.names) {
            if (candidate.endsWith("." + key)) return candidate;
        }
        return null;
    }

    public float getLength(String requested) {
        String key = this.resolve(requested);
        return key == null ? 0.0F : this.clips.get(key).length;
    }

    public boolean loops(String requested) {
        String key = this.resolve(requested);
        return key != null && this.clips.get(key).loop;
    }

    public boolean holdsLastFrame(String requested) {
        String key = this.resolve(requested);
        return key != null && this.clips.get(key).holdLastFrame;
    }

    public List<String> getSoundEvents(String requested, float afterSeconds, float throughSeconds) {
        String key = this.resolve(requested);
        if (key == null) return Collections.emptyList();
        Clip clip = this.clips.get(key);
        if (clip.soundEvents.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        if (throughSeconds >= afterSeconds) {
            appendSoundEvents(clip, afterSeconds, throughSeconds, result);
        } else if (clip.loop && clip.length > 0.0F) {
            appendSoundEvents(clip, afterSeconds, clip.length, result);
            appendSoundEvents(clip, -0.001F, throughSeconds, result);
        }
        return result;
    }

    private static void appendSoundEvents(Clip clip, float afterSeconds, float throughSeconds, List<String> result) {
        for (Map.Entry<Float, String> event : clip.soundEvents.entrySet()) {
            float time = event.getKey().floatValue();
            if (time > afterSeconds && time <= throughSeconds) result.add(event.getValue());
        }
    }

    public BonePose sample(String requested, String boneName, float seconds) {
        String key = this.resolve(requested);
        if (key == null) return BonePose.EMPTY;
        Clip clip = this.clips.get(key);
        BoneTrack track = clip.bones.get(boneName);
        if (track == null) return BonePose.EMPTY;
        float time = seconds;
        if (clip.length > 0.0F) {
            if (clip.loop) time = time % clip.length;
            else if (time > clip.length) time = clip.length;
        }
        return new BonePose(track.rotation == null ? null : track.rotation.sample(time),
                track.position == null ? null : track.position.sample(time),
                track.scale == null ? null : track.scale.sample(time));
    }

    private void read(String path) {
        InputStream stream = PortAnimationFile.class.getResourceAsStream("/assets/sexmod/animations/" + path);
        if (stream == null) {
            System.err.println("[sexmod] Missing port animations: " + path);
            return;
        }
        try {
            JsonObject file = new JsonParser().parse(new InputStreamReader(stream, "UTF-8")).getAsJsonObject();
            JsonObject animations = file.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> animation : animations.entrySet()) {
                if (!animation.getValue().isJsonObject()) continue;
                String key = animation.getKey().toLowerCase();
                Clip clip = Clip.read(animation.getValue().getAsJsonObject());
                this.clips.put(key, clip);
                this.names.add(key);
            }
            Collections.sort(this.names, new Comparator<String>() {
                @Override public int compare(String left, String right) { return left.compareTo(right); }
            });
        } catch (Exception exception) {
            System.err.println("[sexmod] Could not read port animations " + path + ": " + exception);
        } finally {
            try { stream.close(); } catch (Exception ignored) { }
        }
    }

    private static final class Clip {
        float length;
        boolean loop;
        boolean holdLastFrame;
        final Map<String, BoneTrack> bones = new HashMap<String, BoneTrack>();
        final TreeMap<Float, String> soundEvents = new TreeMap<Float, String>();

        static Clip read(JsonObject object) {
            Clip clip = new Clip();
            clip.length = number(object, "animation_length", 0.0F);
            JsonElement loop = object.get("loop");
            clip.loop = loop != null && (loop.isJsonPrimitive() && (loop.getAsJsonPrimitive().isBoolean()
                    ? loop.getAsBoolean() : "loop".equalsIgnoreCase(loop.getAsString())));
            clip.holdLastFrame = loop != null && loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isString()
                    && "hold_on_last_frame".equalsIgnoreCase(loop.getAsString());
            readSoundEvents(object.get("sound_effects"), clip);
            JsonObject boneObjects = object.getAsJsonObject("bones");
            if (boneObjects == null) return clip;
            for (Map.Entry<String, JsonElement> bone : boneObjects.entrySet()) {
                if (!bone.getValue().isJsonObject()) continue;
                JsonObject channels = bone.getValue().getAsJsonObject();
                BoneTrack track = new BoneTrack();
                track.rotation = Track.read(channels.get("rotation"));
                track.position = Track.read(channels.get("position"));
                track.scale = Track.read(channels.get("scale"));
                clip.bones.put(bone.getKey(), track);
            }
            return clip;
        }

        private static void readSoundEvents(JsonElement element, Clip clip) {
            if (element == null || !element.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                try {
                    JsonElement cue = entry.getValue();
                    String effect = cue.isJsonObject() && cue.getAsJsonObject().has("effect")
                            ? cue.getAsJsonObject().get("effect").getAsString()
                            : cue.isJsonPrimitive() ? cue.getAsString() : null;
                    if (effect != null && effect.length() != 0) {
                        clip.soundEvents.put(Float.valueOf(Float.parseFloat(entry.getKey())), effect);
                    }
                } catch (RuntimeException ignored) { }
            }
        }
    }

    private static final class BoneTrack {
        Track rotation;
        Track position;
        Track scale;
    }

    private static final class Track {
        final TreeMap<Float, Key> frames = new TreeMap<Float, Key>();

        static Track read(JsonElement element) {
            if (element == null) return null;
            Track track = new Track();
            if (element.isJsonArray()) {
                Key key = Key.read(element);
                if (key != null) track.frames.put(Float.valueOf(0.0F), key);
                return track.frames.isEmpty() ? null : track;
            }
            if (!element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            if (object.has("vector")) {
                track.frames.put(Float.valueOf(0.0F), Key.read(object));
                return track;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonObject()
                            && (value.getAsJsonObject().has("pre") || value.getAsJsonObject().has("post"))) {
                        JsonObject frameObject = value.getAsJsonObject();
                        String easing = frameObject.has("easing") ? frameObject.get("easing").getAsString()
                                : frameObject.has("lerp_mode") ? frameObject.get("lerp_mode").getAsString() : "linear";
                        Key pre = frameObject.has("pre") ? Key.readVector(frameObject.get("pre"), easing) : null;
                        Key post = frameObject.has("post") ? Key.readVector(frameObject.get("post"), easing) : null;
                        if (pre != null) track.frames.put(Float.valueOf(time), pre);
                        if (post != null) track.frames.put(Float.valueOf(time + 0.000001F), post);
                    } else {
                        Key frame = Key.read(value);
                        if (frame != null) track.frames.put(Float.valueOf(time), frame);
                    }
                } catch (NumberFormatException ignored) { }
            }
            return track.frames.isEmpty() ? null : track;
        }

        float[] sample(float time) {
            Map.Entry<Float, Key> low = this.frames.floorEntry(Float.valueOf(time));
            Map.Entry<Float, Key> high = this.frames.higherEntry(Float.valueOf(time));
            if (low == null) return this.frames.firstEntry().getValue().vector.clone();
            if (high == null) return low.getValue().vector.clone();
            float span = high.getKey().floatValue() - low.getKey().floatValue();
            if (span <= 0.0F) return high.getValue().vector.clone();
            float amount = (time - low.getKey().floatValue()) / span;
            amount = ease(low.getValue().easing, amount);
            float[] result = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                result[axis] = low.getValue().vector[axis]
                        + (high.getValue().vector[axis] - low.getValue().vector[axis]) * amount;
            }
            return result;
        }
    }

    private static final class Key {
        float[] vector;
        String easing;

        static Key read(JsonElement element) {
            if (element == null) return null;
            JsonElement vectorElement = element;
            String easing = "linear";
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                vectorElement = object.has("vector") ? object.get("vector") : object.get("post");
                if (object.has("easing")) easing = object.get("easing").getAsString();
                else if (object.has("lerp_mode")) easing = object.get("lerp_mode").getAsString();
            }
            return readVector(vectorElement, easing);
        }

        static Key readVector(JsonElement vectorElement, String easing) {
            if (vectorElement != null && vectorElement.isJsonObject()) {
                JsonObject wrapper = vectorElement.getAsJsonObject();
                if (wrapper.has("vector")) vectorElement = wrapper.get("vector");
            }
            if (vectorElement == null || !vectorElement.isJsonArray()) return null;
            JsonArray array = vectorElement.getAsJsonArray();
            if (array.size() < 3) return null;
            Key key = new Key();
            key.vector = new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
            key.easing = easing;
            return key;
        }
    }

    public static final class BonePose {
        static final BonePose EMPTY = new BonePose(null, null, null);
        public final float[] rotation;
        public final float[] position;
        public final float[] scale;

        BonePose(float[] rotation, float[] position, float[] scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static float ease(String name, float t) {
        if (name == null) return t;
        String easing = name.toLowerCase();
        if ("step".equals(easing)) return t >= 1.0F ? 1.0F : 0.0F;
        if (easing.contains("sine")) {
            if (easing.startsWith("easeinout")) return (float)(-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
            if (easing.startsWith("easein")) return (float)(1.0D - Math.cos(t * Math.PI / 2.0D));
            if (easing.startsWith("easeout")) return (float)Math.sin(t * Math.PI / 2.0D);
        }
        boolean inOut = easing.startsWith("easeinout");
        boolean in = easing.startsWith("easein");
        if (easing.contains("quad")) return inOut
                ? (t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow(-2.0D * t + 2.0D, 2.0D) / 2.0F)
                : in ? t * t : 1.0F - (1.0F - t) * (1.0F - t);
        if (easing.contains("cubic")) return inOut
                ? (t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0F)
                : in ? t * t * t : 1.0F - (float)Math.pow(1.0F - t, 3.0D);
        if (easing.contains("quart")) return inOut
                ? (t < 0.5F ? 8.0F * t * t * t * t : 1.0F - (float)Math.pow(-2.0D * t + 2.0D, 4.0D) / 2.0F)
                : in ? t * t * t * t : 1.0F - (float)Math.pow(1.0F - t, 4.0D);
        if (easing.contains("quint")) return inOut
                ? (t < 0.5F ? 16.0F * t * t * t * t * t : 1.0F - (float)Math.pow(-2.0D * t + 2.0D, 5.0D) / 2.0F)
                : in ? t * t * t * t * t : 1.0F - (float)Math.pow(1.0F - t, 5.0D);
        if (easing.contains("expo")) {
            if (inOut) return t == 0.0F ? 0.0F : t == 1.0F ? 1.0F : t < 0.5F
                    ? (float)Math.pow(2.0D, 20.0D * t - 10.0D) / 2.0F
                    : (2.0F - (float)Math.pow(2.0D, -20.0D * t + 10.0D)) / 2.0F;
            return in ? (t == 0.0F ? 0.0F : (float)Math.pow(2.0D, 10.0D * t - 10.0D))
                    : (t == 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0D, -10.0D * t));
        }
        if (easing.contains("circ")) return inOut
                ? (t < 0.5F ? (1.0F - (float)Math.sqrt(1.0D - Math.pow(2.0D * t, 2.0D))) / 2.0F
                        : ((float)Math.sqrt(1.0D - Math.pow(-2.0D * t + 2.0D, 2.0D)) + 1.0F) / 2.0F)
                : in ? 1.0F - (float)Math.sqrt(1.0D - t * t)
                        : (float)Math.sqrt(1.0D - (t - 1.0F) * (t - 1.0F));
        if (easing.contains("back")) {
            float c1 = 1.70158F;
            if (inOut) {
                float c2 = c1 * 1.525F;
                return t < 0.5F
                        ? (float)(Math.pow(2.0D * t, 2.0D) * ((c2 + 1.0F) * 2.0D * t - c2)) / 2.0F
                        : (float)(Math.pow(2.0D * t - 2.0D, 2.0D) * ((c2 + 1.0F) * (2.0D * t - 2.0D) + c2) + 2.0D) / 2.0F;
            }
            float c3 = c1 + 1.0F;
            return in ? c3 * t * t * t - c1 * t * t
                    : 1.0F + c3 * (float)Math.pow(t - 1.0D, 3.0D) + c1 * (float)Math.pow(t - 1.0D, 2.0D);
        }
        if (easing.contains("bounce")) {
            if (inOut) return t < 0.5F ? (1.0F - easeOutBounce(1.0F - 2.0F * t)) / 2.0F
                    : (1.0F + easeOutBounce(2.0F * t - 1.0F)) / 2.0F;
            return in ? 1.0F - easeOutBounce(1.0F - t) : easeOutBounce(t);
        }
        if (easing.contains("elastic")) {
            if (t == 0.0F || t == 1.0F) return t;
            if (inOut) {
                double c = 2.0D * Math.PI / 4.5D;
                return t < 0.5F ? (float)(-(Math.pow(2.0D, 20.0D * t - 10.0D) * Math.sin((20.0D * t - 11.125D) * c)) / 2.0D)
                        : (float)(Math.pow(2.0D, -20.0D * t + 10.0D) * Math.sin((20.0D * t - 11.125D) * c) / 2.0D + 1.0D);
            }
            double c = 2.0D * Math.PI / 3.0D;
            return in ? (float)(-Math.pow(2.0D, 10.0D * t - 10.0D) * Math.sin((t * 10.0D - 10.75D) * c))
                    : (float)(Math.pow(2.0D, -10.0D * t) * Math.sin((t * 10.0D - 0.75D) * c) + 1.0D);
        }
        return t;
    }

    private static float easeOutBounce(float t) {
        float n = 7.5625F;
        float d = 2.75F;
        if (t < 1.0F / d) return n * t * t;
        if (t < 2.0F / d) { t -= 1.5F / d; return n * t * t + 0.75F; }
        if (t < 2.5F / d) { t -= 2.25F / d; return n * t * t + 0.9375F; }
        t -= 2.625F / d;
        return n * t * t + 0.984375F;
    }
}
