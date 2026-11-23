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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("comunicacao")
public class ComunicacaoConsumer {

    private final JsonMapper jsonMapper;

    public ComunicacaoConsumer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = "hotel.comunicacao")
    public void receber(String mensagem) throws Exception {

        JsonNode evento = jsonMapper.readTree(mensagem);

        String eventoId = evento.path("eventoId").asText();
        String tipo = evento.path("tipo").asText();
        String ocorridoEm = evento.path("ocorridoEm").asText();

        if (eventoId.isBlank() || tipo.isBlank() || ocorridoEm.isBlank()) {
            throw new IllegalArgumentException(
                    "Evento incompleto para comunicação"
            );
        }

        String aviso = switch (tipo) {
            case "HOSPEDAGEM_CONFIRMADA" ->
                    "Sua hospedagem foi confirmada.";

            case "HOSPEDAGEM_CANCELADA" ->
                    "Sua hospedagem foi cancelada.";

            case "VISTORIA_REPROVADA" ->
                    "A vistoria foi reprovada. É necessário realizar ajustes.";

            default ->
                    throw new IllegalArgumentException(
                            "Tipo de evento não suportado: " + tipo
                    );
        };

        Map<String, Object> registro = new LinkedHashMap<>();

        registro.put("eventoId", eventoId);
        registro.put("tipo", tipo);
        registro.put("ocorridoEm", ocorridoEm);
        registro.put("aviso", aviso);
        registro.put("dados", evento.path("dados"));

        String registroJson = jsonMapper.writeValueAsString(registro);

        Path arquivo = Path.of("dados", "comunicacao.jsonl");

        Files.createDirectories(arquivo.getParent());

        Files.writeString(
                arquivo,
                registroJson + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println("[COMUNICACAO] Aviso registrado!");
        System.out.println("Evento: " + eventoId);
        System.out.println("Tipo: " + tipo);
        System.out.println("Aviso: " + aviso);
    }
}