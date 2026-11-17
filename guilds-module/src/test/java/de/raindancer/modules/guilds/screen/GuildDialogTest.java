package de.raindancer.modules.guilds.screen;

import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuildDialogTest {

    private final List<DialogPage> shown = new ArrayList<>();
    private final Player viewer = mock(Player.class);

    @BeforeEach
    void standIn() {
        when(viewer.isOnline()).thenReturn(true);
        DialogPage.runtime(new DialogPage.Runtime() {
            @Override
            public void show(Player player, DialogPage page) {
                shown.add(page);
            }

            @Override
            public void onPlayerThread(Player player, Runnable task) {
                task.run();
            }
        });
    }

    @AfterEach
    void game() {
        DialogPage.runtime(null);
    }

    private static String plain(net.kyori.adventure.text.Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static List<String> labels(DialogPage page) {
        return page.pressable().stream().map(button -> plain(button.label())).toList();
    }

    @Test
    @DisplayName("the guild is the title and the card on top, every door a button in order, leaving last")
    void theGuildsPage() {
        AtomicInteger opened = new AtomicInteger();
        DialogPage page = GuildDialog.page(viewer, "The Elves", null, "<gray>Led by <white>Ada\n<gray>Level <white>3", List.of(
                GuildDialog.Door.open("<white>Members", "<gray>4 of 10.", opened::incrementAndGet),
                GuildDialog.Door.locked("<white>Invite and applications", "<gray>2 waiting.",
                        "Your rank cannot invite.", opened::incrementAndGet),
                GuildDialog.Door.open("<white>Ranks", "<gray>3 ranks.", opened::incrementAndGet)),
                () -> { });

        assertThat(plain(page.title())).contains("The Elves");
        assertThat(page.plainText()).contains("Led by Ada").contains("Level 3");
        assertThat(labels(page)).containsExactly("Members", "Invite and applications", "Ranks", "How this works",
                "Leave the guild");

        page.button("Members").press(viewer, DialogAnswers.none());
        assertThat(opened).hasValue(1);
        assertThat(page.button("Invite and applications").allowed()).isFalse();
        page.button("Invite and applications").press(viewer, DialogAnswers.none());
        assertThat(opened).as("a greyed door opens nothing").hasValue(1);
    }

    @Test
    @DisplayName("leaving asks first, in a window of its own, and only Yes leaves")
    void leavingAsksFirst() {
        AtomicInteger left = new AtomicInteger();
        DialogPage page = GuildDialog.page(viewer, "The Elves", null, "", List.of(), left::incrementAndGet);

        page.button("Leave the guild").press(viewer, DialogAnswers.none());
        DialogPage question = shown.getLast();
        assertThat(question.isConfirmation()).isTrue();
        assertThat(question.plainText()).contains("Leave The Elves?");
        assertThat(left).as("nothing yet").hasValue(0);

        question.pressable().getFirst().press(viewer, DialogAnswers.none());
        assertThat(left).hasValue(1);
    }

    @Test
    @DisplayName("somebody in no guild gets founding, every guild and their invitations, founding greyed with a reason")
    void noGuild() {
        DialogPage page = GuildDialog.noGuild(viewer, List.of(
                GuildDialog.Door.locked("<white>Found a guild", "<gray>Costs 200,000.", "Play 2 h more first.", () -> { }),
                GuildDialog.Door.open("<white>Every guild", "<gray>12 on this server.", () -> { }),
                GuildDialog.Door.open("<white>Your invitations", "<gray>1 waiting.", () -> { })));

        assertThat(plain(page.title())).contains("Guilds");
        assertThat(labels(page)).containsExactly("Found a guild", "Every guild", "Your invitations", "How this works");
        assertThat(page.button("Found a guild").allowed()).isFalse();
    }
}
