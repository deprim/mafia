package com.mafiagame.session;

import com.mafiagame.model.Player;
import com.mafiagame.repository.PlayerRepository;
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

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements ChannelInterceptor {
    final PlayerRepository players;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (acc != null && StompCommand.CONNECT.equals(acc.getCommand())) {
            //todo: here should be implemented authentication
//            String token = acc.getFirstNativeHeader("X-Session-Token");
//            if (token == null) throw new MessagingException("Missing token");
//            Player p = players.findBySessionTokenHash(Token.hash(token))
//                    .orElseThrow(() -> new MessagingException("Invalid token"));
//            acc.setUser(new PlayerPrincipal(p.getId(), p.getGameId(), p.getNickname()));
        }

        //todo: create subscription method
        if (acc.getCommand() == StompCommand.SUBSCRIBE) {
            if (!(acc.getUser() instanceof PlayerPrincipal user) || acc.getDestination() == null) {
                throw new MessagingException("Unauthorized");
            }
            String dest = acc.getDestination();
            if (dest.startsWith("/user/")) return message;

//            Matcher m = GAME_TOPIC.matcher(dest);
//            if (!m.matches() || !user.gameId().equals(UUID.fromString(m.group(1)))) {
//                throw new MessagingException("Forbidden");
//            }
//            if (m.group(2) != null) { // /mafia
//                PlayerRole role = players.findById(user.playerId()).map(Player::getRole).orElse(null);
//                if (role != PlayerRole.MAFIA) throw new MessagingException("Forbidden");
//            }
        }

        return message;
    }
}
