package faygolover.zoneartifacts.util;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Small JSON-reading helpers shared by every datapack-driven config loader in this mod (anomaly
 * types, Tesla types, ...), so each one throws the same clear "file + field" {@link
 * DatapackParseException} instead of a bare {@code NullPointerException}/{@code
 * ClassCastException} that just says a reload failed somewhere.
 */
public final class JsonHelper {

    public static JsonObject getObject(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonObject()) {
            throw new DatapackParseException(field, "missing or not an object");
        }
        return root.getAsJsonObject(field);
    }

    public static String getString(JsonObject obj, String field, String path) {
        if (!obj.has(field) || !obj.get(field).isJsonPrimitive()) {
            throw new DatapackParseException(path, "missing string field");
        }
        return obj.get(field).getAsString();
    }

    public static int getInt(JsonObject obj, String field, int def) {
        return obj.has(field) ? obj.get(field).getAsInt() : def;
    }

    public static float getFloat(JsonObject obj, String field, float def) {
        return obj.has(field) ? obj.get(field).getAsFloat() : def;
    }

    public static boolean getBool(JsonObject obj, String field, boolean def) {
        return obj.has(field) ? obj.get(field).getAsBoolean() : def;
    }

    public static int parseColor(JsonObject obj, String path) {
        String colorStr = obj.has("color") ? obj.get("color").getAsString() : "B8E8FF";
        try {
            return Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            throw new DatapackParseException(path + ".color", "not a valid hex RGB color (e.g. \"B8E8FF\"): '" + colorStr + "'");
        }
    }

    @Nullable
    public static ResourceLocation parseOptionalSound(JsonObject obj, String field, String path) {
        if (!obj.has(field)) return null;
        ResourceLocation id = ResourceLocation.tryParse(obj.get(field).getAsString());
        if (id == null) {
            throw new DatapackParseException(path + "." + field, "not a valid resource location");
        }
        return id;
    }

    public static ResourceLocation requireResourceLocation(JsonObject obj, String field, String path) {
        String raw = getString(obj, field, path + "." + field);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new DatapackParseException(path + "." + field, "not a valid resource location: '" + raw + "'");
        }
        return id;
    }

    private JsonHelper() {
    }
}
