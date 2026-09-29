package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Webs of one dimension: each a set of threads (straight segments between exact points on
 * blocks — not tied to blocks, so any number of webs can share a block) with its own settings. A
 * thread that cut someone is snapped until it grows back (runtime only).
 */
public class WebSavedData extends SavedData {

    private static final String ID = "fl_zone_arts_webs";

    public static final class Strand {
        public final Vec3 a;
        public final Vec3 b;
        /** Game time it grows back at; 0 = whole. */
        public long brokenUntil;

        public Strand(Vec3 a, Vec3 b) {
            this.a = a;
            this.b = b;
        }
    }

    public static final class Web {
        public final int id;
        public final List<Strand> strands = new ArrayList<>();
        public float damage;
        public double regrowSeconds;
        public int intensity;

        Web(int id) {
            this.id = id;
            this.damage = ModCommonConfig.WEB_DAMAGE.get().floatValue();
            this.regrowSeconds = ModCommonConfig.WEB_REGROW_SECONDS.get();
            this.intensity = ModCommonConfig.WEB_INTENSITY.get();
        }

        public double distanceSqTo(Vec3 p) {
            double best = Double.MAX_VALUE;
            for (Strand s : strands) best = Math.min(best, segmentDistanceSq(s.a, s.b, p));
            return best;
        }
    }

    private final Map<Integer, Web> webs = new LinkedHashMap<>();
    private int nextId = 1;

    public static WebSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WebSavedData::load, WebSavedData::new, ID);
    }

    public Collection<Web> webs() {
        return webs.values();
    }

    @Nullable
    public Web get(int id) {
        return webs.get(id);
    }

    public Web create() {
        Web web = new Web(nextId++);
        webs.put(web.id, web);
        setDirty();
        return web;
    }

    public void remove(int id) {
        if (webs.remove(id) != null) setDirty();
    }

    public static double segmentDistanceSq(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len));
        return a.add(ab.scale(t)).distanceToSqr(p);
    }

    public static WebSavedData load(CompoundTag tag) {
        WebSavedData data = new WebSavedData();
        data.nextId = Math.max(1, tag.getInt("next_id"));
        for (Tag t : tag.getList("webs", Tag.TAG_COMPOUND)) {
            CompoundTag w = (CompoundTag) t;
            Web web = new Web(w.getInt("id"));
            if (w.contains("damage")) web.damage = w.getFloat("damage");
            if (w.contains("regrow")) web.regrowSeconds = w.getDouble("regrow");
            if (w.contains("intensity")) web.intensity = w.getInt("intensity");
            for (Tag st : w.getList("strands", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) st;
                web.strands.add(new Strand(new Vec3(s.getDouble("ax"), s.getDouble("ay"), s.getDouble("az")),
                        new Vec3(s.getDouble("bx"), s.getDouble("by"), s.getDouble("bz"))));
            }
            data.webs.put(web.id, web);
            data.nextId = Math.max(data.nextId, web.id + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("next_id", nextId);
        ListTag list = new ListTag();
        for (Web web : webs.values()) {
            CompoundTag w = new CompoundTag();
            w.putInt("id", web.id);
            w.putFloat("damage", web.damage);
            w.putDouble("regrow", web.regrowSeconds);
            w.putInt("intensity", web.intensity);
            ListTag strands = new ListTag();
            for (Strand s : web.strands) {
                CompoundTag st = new CompoundTag();
                st.putDouble("ax", s.a.x);
                st.putDouble("ay", s.a.y);
                st.putDouble("az", s.a.z);
                st.putDouble("bx", s.b.x);
                st.putDouble("by", s.b.y);
                st.putDouble("bz", s.b.z);
                strands.add(st);
            }
            w.put("strands", strands);
            list.add(w);
        }
        tag.put("webs", list);
        return tag;
    }
}
