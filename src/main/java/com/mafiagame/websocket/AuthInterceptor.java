package com.mafiagame.websocket;

import com.mafiagame.model.Player;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.PlayerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements ChannelInterceptor {
    private static final Pattern GAME_TOPIC =
            Pattern.compile("^/game-session/game/([^/]+)(/mafia)?$");
//            Pattern.compile("^/game-session/game/([0-9a-fA-F-]{36})(/mafia)?$");

    final PlayerRepository players;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;

        if (acc != null && StompCommand.CONNECT.equals(acc.getCommand())) {
            String token = acc.getFirstNativeHeader("X-Session-Token");
            if (token == null) throw new MessagingException("Missing token");
            Player p = players.findBySessionTokenHash(SessionTokenGenerator.hashToken(token));
            if (p == null) throw new MessagingException("Invalid token");
            acc.setUser(new PlayerPrincipal(p.getId(), p.getGame().getId()));
        }

        if (acc.getCommand() == StompCommand.SUBSCRIBE) {
            if (!(acc.getUser() instanceof PlayerPrincipal user) || acc.getDestination() == null) {
                throw new MessagingException("Unauthorized");
            }
            String dest = acc.getDestination();
            if (dest.startsWith("/user/")) return message;

            Matcher m = GAME_TOPIC.matcher(dest);
            if (!m.matches() || !user.gameId().equals(UUID.fromString(m.group(1)))) {
                throw new MessagingException("Forbidden");
            }
            if (m.group(2) != null) { // /mafia
                PlayerRole role = players.findById(user.playerId()).map(Player::getRole).orElse(null);
                if (role != PlayerRole.MAFIA) throw new MessagingException("Forbidden");
            }
        }

        return message;
    }
}
