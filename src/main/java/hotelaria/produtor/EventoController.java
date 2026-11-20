
package hotelaria.produtor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Profile("produtor")
@RequestMapping("/eventos")
public class EventoController {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper objectMapper;

    public EventoController(RabbitTemplate rabbitTemplate,
                        JsonMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
}

    @PostMapping
    public ResponseEntity<Map<String, String>> publicar(
            @RequestBody Map<String, Object> evento)
             {

        String id = evento.get("eventoId") instanceof String s ? s : "";
        String tipo = evento.get("tipo") instanceof String s ? s : "";
        String data = evento.get("ocorridoEm") instanceof String s ? s : "";

        if (id.isBlank() || data.isBlank()
                || !(evento.get("dados") instanceof Map)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Evento incompleto"));
        }

        String chave = switch (tipo) {
            case "HOSPEDAGEM_CONFIRMADA" -> "hospedagem.confirmada";
            case "HOSPEDAGEM_CANCELADA" -> "hospedagem.cancelada";
            case "VISTORIA_APROVADA" -> "vistoria.aprovada";
            case "VISTORIA_REPROVADA" -> "vistoria.reprovada";
            default -> null;
        };

        if (chave == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Tipo de evento invalido"));
        }

        String json = objectMapper.writeValueAsString(evento);

        rabbitTemplate.convertAndSend(
                "hotel.eventos",
                chave,
                json,
                mensagem -> {
                    mensagem.getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return mensagem;
                }
        );

        System.out.println("[PRODUTOR] Evento enviado: " + tipo);

        return ResponseEntity.ok(Map.of(
                "mensagem", "Evento enviado",
                "eventoId", id,
                "chave", chave
        ));
    }
}
