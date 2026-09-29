package faygolover.zoneartifacts.tesla;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The Cold Comet: the {@link CometEntity} in soul fire — freezes where the Comet burns. */
public class ColdCometEntity extends CometEntity {

    public ColdCometEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isCold() {
        return true;
    }
}
