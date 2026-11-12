package faygolover.zoneartifacts.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Items worn in Curios slots (RP Lights headlamp, belt lamp). Curios is soft: without it nothing is worn.
 * Called through MethodHandles so Curios is not needed to build.
 */
public final class CuriosWorn {
    private CuriosWorn() {}

    private static MethodHandle inventory, equipped;
    private static boolean tried;

    /** Whether any worn item passes {@code test}. */
    public static boolean any(LivingEntity entity, Predicate<ItemStack> test) {
        if (!ready()) return false;
        try {
            Object lazy = inventory.invoke(entity);
            if (!(lazy instanceof LazyOptional<?> opt)) return false;
            Optional<?> handler = opt.resolve();
            if (handler.isEmpty()) return false;
            Object items = equipped.invoke(handler.get());
            if (!(items instanceof IItemHandler h)) return false;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && test.test(s)) return true;
            }
        } catch (Throwable ignored) {
            inventory = null;
        }
        return false;
    }

    private static boolean ready() {
        if (tried) return inventory != null;
        tried = true;
        if (!ModList.get().isLoaded("curios")) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            inventory = l.findStatic(api, "getCuriosInventory", MethodType.methodType(LazyOptional.class, LivingEntity.class));
            equipped = l.findVirtual(handler, "getEquippedCurios", MethodType.methodType(
                    Class.forName("net.minecraftforge.items.IItemHandlerModifiable")));
        } catch (Throwable t) {
            inventory = null;
        }
        return inventory != null;
    }
}
