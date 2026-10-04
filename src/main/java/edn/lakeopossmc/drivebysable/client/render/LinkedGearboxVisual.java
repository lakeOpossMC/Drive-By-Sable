package edn.lakeopossmc.drivebysable.client.render;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import net.minecraft.core.Direction;

// --- FLYWHEEL VISUAL FOR THE LINKED GEARBOX COG --- //
public class LinkedGearboxVisual extends OrientedRotatingVisual<LinkedGearboxBlockEntity> {

    public LinkedGearboxVisual(final VisualizationContext context, final LinkedGearboxBlockEntity be, final float partialTick) {
        super(context, be, partialTick,
                Direction.UP,
                be.getBlockState().getValue(LinkedGearboxBlock.FACING),
                Models.partial(LinkedGearboxPartialModels.COG));
    }
}