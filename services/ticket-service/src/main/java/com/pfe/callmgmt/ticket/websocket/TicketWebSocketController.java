package com.pfe.callmgmt.ticket.websocket;

import com.pfe.callmgmt.ticket.dto.TicketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class TicketWebSocketController {

    @MessageMapping("/ticket.update")
    @SendTo("/topic/tickets.update")
    public TicketResponse broadcastTicketUpdate(TicketResponse ticketResponse) {
        log.debug("Broadcasting ticket update via WebSocket: {}", ticketResponse.getNumero());
        return ticketResponse;
    }
}
