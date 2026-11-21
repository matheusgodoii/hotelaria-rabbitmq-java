
package hotelaria.consumidores;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
@Profile("auditoria")
public class AuditoriaConsumer {

    private final JsonMapper jsonMapper;

    public AuditoriaConsumer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = "hotel.auditoria")
    public void receber(String mensagem) throws Exception {

        JsonNode evento = jsonMapper.readTree(mensagem);

        String id = evento.path("eventoId").asText();
        String tipo = evento.path("tipo").asText();
        String data = evento.path("ocorridoEm").asText();

        if (id.isBlank() || tipo.isBlank() || data.isBlank()) {
            throw new IllegalArgumentException(
                "Evento incompleto para auditoria"
            );
        }

        // Arquivo que armazena o historico
        Path arquivo = Path.of("dados", "auditoria.jsonl");

        Files.createDirectories(arquivo.getParent());

        // Salva o evento completo
        Files.writeString(
            arquivo,
            mensagem + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );

        System.out.println("[AUDITORIA] Evento registrado!");
        System.out.println("ID: " + id);
        System.out.println("Tipo: " + tipo);
        System.out.println("Data: " + data);
    }
}
