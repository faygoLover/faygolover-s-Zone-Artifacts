package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import faygolover.zoneartifacts.tesla.TeslaConfig;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side copy of the Tesla routes in the current dimension and of the few config values the
 * client needs. A rendering/click aid only — the server re-validates every action.
 */
public final class TeslaClientCache {

    private static ResourceKey<Level> dimension = null;
    private static List<SyncTeslaRoutesPacket.Entry> routes = List.of();

    private static int spawnGrowTicks = TeslaConfig.DEFAULTS.spawnGrowTicks();
    private static ResourceLocation idleSound = TeslaConfig.DEFAULTS.idleSound();
    private static float idleVolume = TeslaConfig.DEFAULTS.idleVolume();
    private static float idlePitch = TeslaConfig.DEFAULTS.idlePitch();

    private TeslaClientCache() {
    }

    public static void setRoutes(ResourceKey<Level> dim, List<SyncTeslaRoutesPacket.Entry> entries) {
        dimension = dim;
        routes = entries;
    }

    public static List<SyncTeslaRoutesPacket.Entry> routesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? routes : List.of();
    }

    public static List<TeslaGeometry.WaypointRef> waypointsFor(ResourceKey<Level> dim) {
        List<TeslaGeometry.WaypointRef> refs = new ArrayList<>();
        for (SyncTeslaRoutesPacket.Entry entry : routesFor(dim)) {
            List<BlockPos> points = entry.points();
            for (int i = 0; i < points.size(); i++) {
                refs.add(new TeslaGeometry.WaypointRef(entry.id(), i, points.get(i)));
            }
        }
        return refs;
    }

    public static void setConfig(int growTicks, ResourceLocation sound, float volume, float pitch) {
        spawnGrowTicks = Math.max(1, growTicks);
        idleSound = sound;
        idleVolume = volume;
        idlePitch = pitch;
    }

    public static int spawnGrowTicks() {
        return spawnGrowTicks;
    }

    public static ResourceLocation idleSound() {
        return idleSound;
    }

    public static float idleVolume() {
        return idleVolume;
    }

    public static float idlePitch() {
        return idlePitch;
    }
}
