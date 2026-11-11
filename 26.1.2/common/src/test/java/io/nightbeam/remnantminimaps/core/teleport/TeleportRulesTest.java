package io.nightbeam.remnantminimaps.core.teleport;

import io.nightbeam.remnantminimaps.core.teleport.TeleportRules.Verdict;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportRulesTest {
    private static TeleportRules.Request request(boolean enabled, boolean integrated, int level, boolean permitted) {
        return new TeleportRules.Request(enabled, integrated, level, permitted, true, true, false, 0, false);
    }

    @Test
    void operatorOnDedicatedServerIsAllowed() {
        assertEquals(Verdict.ALLOWED, TeleportRules.check(request(true, false, 2, true)));
    }

    @Test
    void nonOperatorOnDedicatedServerIsRefused() {
        assertEquals(Verdict.NO_PERMISSION, TeleportRules.check(request(true, false, 0, false)));
    }

    @Test
    void disabledBeatsEverything() {
        assertEquals(Verdict.DISABLED, TeleportRules.check(request(false, false, 4, true)));
        assertEquals(Verdict.DISABLED, TeleportRules.access(false, true, 4, true));
    }

    @Test
    void singlePlayerWithoutCheatsIsRefusedEvenIfConfiguredForEveryone() {
        // permissionLevel 0 in the config makes "permitted" true for everybody...
        assertEquals(Verdict.CHEATS_OFF, TeleportRules.check(request(true, true, 0, true)));
        // ...but with cheats (level 4 for the host) it works.
        assertEquals(Verdict.ALLOWED, TeleportRules.check(request(true, true, 4, true)));
    }

    @Test
    void permissionPluginCanDenyAnOperator() {
        assertEquals(Verdict.NO_PERMISSION, TeleportRules.check(request(true, false, 4, false)));
    }

    @Test
    void dedicatedServerCanOpenItToEveryone() {
        assertEquals(Verdict.ALLOWED, TeleportRules.check(request(true, false, 0, true)));
    }

    @Test
    void requestChecksComeInOrder() {
        assertEquals(Verdict.WRONG_DIMENSION, TeleportRules.check(
                new TeleportRules.Request(true, false, 2, true, false, false, true, 500, true)));
        assertEquals(Verdict.OUTSIDE_WORLD, TeleportRules.check(
                new TeleportRules.Request(true, false, 2, true, true, false, true, 500, true)));
        assertEquals(Verdict.BUSY, TeleportRules.check(
                new TeleportRules.Request(true, false, 2, true, true, true, true, 500, true)));
        assertEquals(Verdict.COOLDOWN, TeleportRules.check(
                new TeleportRules.Request(true, false, 2, true, true, true, false, 500, true)));
        assertEquals(Verdict.VETOED, TeleportRules.check(
                new TeleportRules.Request(true, false, 2, true, true, true, false, 0, true)));
    }

    @Test
    void levelIsClamped() {
        assertEquals(0, TeleportRules.clampLevel(-3));
        assertEquals(2, TeleportRules.clampLevel(2));
        assertEquals(4, TeleportRules.clampLevel(99));
    }

    @Test
    void cooldownCountsDownAndCanBeForgotten() {
        TeleportRules.Cooldowns cooldowns = new TeleportRules.Cooldowns();
        UUID player = UUID.randomUUID();
        assertEquals(0, cooldowns.remainingMs(player, 1_000, 3_000));
        cooldowns.mark(player, 1_000);
        assertEquals(3_000, cooldowns.remainingMs(player, 1_000, 3_000));
        assertEquals(1_000, cooldowns.remainingMs(player, 3_000, 3_000));
        assertEquals(0, cooldowns.remainingMs(player, 4_000, 3_000));
        assertEquals(0, cooldowns.remainingMs(player, 1_500, 0), "no cooldown configured");
        // a clock that jumped backwards never yields more than the cooldown itself
        assertEquals(3_000, cooldowns.remainingMs(player, -50_000, 3_000));
        cooldowns.forget(player);
        assertEquals(0, cooldowns.remainingMs(player, 1_000, 3_000));
    }
}
