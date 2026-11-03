package com.mafiagame.session;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements ChannelInterceptor {

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

        return message;
    }
}
