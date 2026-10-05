package edn.lakeopossmc.drivebysable.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// --- THE ADVANCEMENT TRIGGER "drivebysable:event" --- //
// * One trigger type for everything, told apart by "event" name
// * in the advancement file: { "trigger": "drivebysable:event", "conditions": { "event": "..." } }
public class CableEventTrigger extends SimpleCriterionTrigger<CableEventTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(final ServerPlayer player, final String event) {
        this.trigger(player, instance -> instance.event().equals(event));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String event)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event)
        ).apply(instance, TriggerInstance::new));
    }
}