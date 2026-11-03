package com.mafiagame.session;

import com.mafiagame.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;


//example controller is not for the final version of the project
//template for future usage and functionalities only
@Controller
public class webSocketExampleController {

    @MessageMapping("/{gameId}/send-message")
    @SendTo("/game-session/game/{gameId}")
    public ChatMessage sendMessage(ChatMessage message) {
        return message;
    }
}
