package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.anomaly.WebSavedData;
import faygolover.zoneartifacts.client.web.WebClient;
import faygolover.zoneartifacts.network.SyncWebsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stretches Webs. Right-click on a block: the exact point clicked starts a thread; the next click
 * ends it there and starts the next one from the same point (a chain). Sneak + right-click: the web
 * is finished (the next click starts a new one). Left-click on a thread removes it, sneaking — the
 * whole web ({@code WebClient}, {@code WebEditPacket}).
 */
public class WebPlacerItem extends Item {

    private record Pending(int webId, Vec3 last) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final double MAX_LENGTH = 32.0;

    public WebPlacerItem(Properties properties) {
        super(properties);
    }

    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof WebPlacerItem || player.getOffhandItem().getItem() instanceof WebPlacerItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Vec3 hit = context.getClickLocation();
        boolean sneaking = player.isShiftKeyDown();
        Level level = context.getLevel();
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WebClient.onPlacerClick(hit, sneaking));
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
        if (sneaking) {
            finish(player);
            return InteractionResult.CONSUME;
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            PENDING.put(player.getUUID(), new Pending(-1, hit));
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.start"), true);
            return InteractionResult.CONSUME;
        }
        double length = pending.last().distanceTo(hit);
        if (length < 0.2) return InteractionResult.CONSUME;
        if (length > MAX_LENGTH) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.too_long", (int) MAX_LENGTH), true);
            return InteractionResult.CONSUME;
        }
        WebSavedData data = WebSavedData.get(server);
        WebSavedData.Web web = pending.webId() >= 0 ? data.get(pending.webId()) : null;
        if (web == null) web = data.create();
        web.strands.add(new WebSavedData.Strand(pending.last(), hit));
        data.setDirty();
        SyncWebsPacket.broadcast(server);
        PENDING.put(player.getUUID(), new Pending(web.id, hit));
        player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.strand", web.strands.size()), true);
        return InteractionResult.CONSUME;
    }

    /** Sneak + right-click in the air finishes the web too. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WebClient.onPlacerClick(null, true));
        } else {
            finish(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void finish(Player player) {
        if (PENDING.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.finished"), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltips.addDescription(getDescriptionId(), tooltip);
    }
}
