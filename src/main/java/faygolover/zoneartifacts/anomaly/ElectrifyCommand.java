package faygolover.zoneartifacts.anomaly;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.ElectrifyPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * {@code /fl_zone_arts electrify <targets> <seconds> [damage]} (permission level 2) — puts the
 * electrification effect on any entities: arcs crawling over them for {@code seconds}. With a
 * {@code damage} above 0, living targets also take that much shock damage, once, at the start
 * (same damage type as the anomalies), with the usual hit sound.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class ElectrifyCommand {

    /** Intensity used for command-applied electrification (the anomalies' standard). */
    private static final int COMMAND_INTENSITY = 3;

    private ElectrifyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fl_zone_arts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("electrify")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.05f, 600.0f))
                                        .executes(ctx -> run(ctx, 0.0f))
                                        .then(Commands.argument("damage", FloatArgumentType.floatArg(0.0f, 1000.0f))
                                                .executes(ctx -> run(ctx, FloatArgumentType.getFloat(ctx, "damage"))))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, float damage) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        float seconds = FloatArgumentType.getFloat(ctx, "seconds");
        int ticks = Math.max(1, Math.round(seconds * 20.0f));

        for (Entity target : targets) {
            ElectrifyPacket.send(target, ticks, 0, COMMAND_INTENSITY);
            if (damage > 0 && target instanceof LivingEntity living && target.level() instanceof ServerLevel level) {
                AnomalyCombat.hurt(level, living, Electra.DAMAGE_TYPE, damage);
                AnomalyCombat.playRandom(level, living.getBoundingBox().getCenter(), Electra.HIT_SOUNDS, Electra.HIT_VOLUME);
            }
        }

        int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal("[Электризация] Целей: " + count + ", "
                + seconds + " с" + (damage > 0 ? ", урон " + damage : "") + "."), true);
        return count;
    }
}
