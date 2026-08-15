package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HonorServiceTest {
    private final HonorService service = new HonorService();

    @Test
    void newPlayerStartsAtZero() {
        assertEquals(0L, service.honor(new PlayerRef(UUID.randomUUID())));
    }

    @Test
    void adjustAccumulatesAndCanGoNegative() {
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        service.adjust(player, 10L);
        service.adjust(player, -25L);
        assertEquals(-15L, service.honor(player));
    }

    @Test
    void loadHonorSeedsTheCacheForRehydration() {
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        service.loadHonor(new PlayerHonor(player, 42L));
        assertEquals(42L, service.honor(player));
        assertEquals(1, service.allHonor().size());
    }
}
