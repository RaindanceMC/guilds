package de.raindancer.modules.guilds.listener;

import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.modules.guilds.model.Activity;
import de.raindancer.modules.guilds.model.XpSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressListenerTest {

    @Test
    @DisplayName("what players do comes from Core's Activities — the guild listens to no break of its own, which "
            + "at MONITOR could read PlacedBlocks after Core took the mark off and pay for a placed ore")
    void detectsNothingCoreKnows() {
        assertThat(Arrays.stream(ProgressListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .map(Method::getParameterTypes))
                .as("only rivals defeated, which are no activity of Core's")
                .containsExactly(new Class<?>[] {PlayerDeathEvent.class});
    }

    @Test
    @DisplayName("each of Core's activities the guild knows earns from the source of the same name, what and amount "
            + "kept; one it does not know earns nothing")
    void mapsCoreActivities() {
        java.util.Set<String> known = java.util.Arrays.stream(XpSource.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        for (ActivityKind kind : ActivityKind.values()) {
            if (kind == ActivityKind.PLAY) {
                continue;
            }
            var guild = ProgressListener.toGuild(new de.raindancer.core.world.activity.Activity(kind, "iron", 3));
            if (known.contains(kind.name())) {
                assertThat(guild).as(kind.name()).contains(new Activity(XpSource.valueOf(kind.name()), "iron", 3));
            } else {
                assertThat(guild).as(kind.name()).isEmpty();
            }
        }
        assertThat(known).as("the old ones still earn").contains("MONSTER", "MINE", "FISH");
    }

    @Test
    @DisplayName("Core's minutes played are not counted twice — the guild counts its own, not while away")
    void playIsCountedByTheGuild() {
        assertThat(ProgressListener.toGuild(de.raindancer.core.world.activity.Activity.of(ActivityKind.PLAY, 1)))
                .isEmpty();
    }
}
