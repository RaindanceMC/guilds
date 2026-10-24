package de.raindancer.modules.guilds;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("guilds")
                .settings(GuildsSettings.class, GuildsSettings.DEFAULTS)
                .settings(GuildProgressSettings.class, GuildProgressSettings.DEFAULTS)
                .settings(GuildNpcSettings.class, GuildNpcSettings.DEFAULTS)
                .otherTopics("claims", "staff-tools")
                .commands(new GuildsModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
