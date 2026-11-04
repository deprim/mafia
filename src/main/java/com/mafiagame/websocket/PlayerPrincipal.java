package com.mafiagame.websocket;

import java.security.Principal;
import java.util.UUID;

public record PlayerPrincipal(UUID playerId, UUID gameId) implements Principal {
    @Override
    public String getName() { return playerId.toString(); }

}
