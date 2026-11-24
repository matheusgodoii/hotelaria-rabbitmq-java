
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumidor da governanca do hotel.
 *
 * Recebe toda a familia de eventos de vistoria (binding "vistoria.#"):
 * - VISTORIA_APROVADA  -> registra que o quarto foi aprovado
 * - VISTORIA_REPROVADA -> registra a reprovacao e as pendencias
 * - qualquer outro "vistoria.*" futuro -> registra de forma generica
 *
 * Os registros ficam em dados/governanca.jsonl (um JSON por linha).
 */
@Component
@Profile("governanca")
public class GovernancaConsumer {

    private static final Path ARQUIVO = Path.of("dados", "governanca.jsonl");

    private final JsonMapper jsonMapper;

    public GovernancaConsumer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = "hotel.governanca")
    public void receber(String mensagem) throws Exception {

        JsonNode evento = jsonMapper.readTree(mensagem);
        JsonNode dados = evento.path("dados");

        String eventoId = evento.path("eventoId").asText();
        String tipo = evento.path("tipo").asText();
        String ocorridoEm = evento.path("ocorridoEm").asText();
        String quarto = dados.path("quarto").asText();

        if (eventoId.isBlank() || tipo.isBlank() || ocorridoEm.isBlank()) {
            throw new IllegalArgumentException(
                "Evento incompleto para governanca"
            );
        }

        Files.createDirectories(ARQUIVO.getParent());

        // Evita registrar o mesmo evento duas vezes (reentrega da fila)
        if (jaRegistrado(eventoId)) {
            System.out.println("[GOVERNANCA] Evento ja registrado: " + eventoId);
            return;
        }

        String situacao;
        switch (tipo) {
            case "VISTORIA_APROVADA" -> situacao = "APROVADA";
            case "VISTORIA_REPROVADA" -> situacao = "REPROVADA";
            default -> situacao = "OUTRO_EVENTO_DE_VISTORIA";
        }

        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("eventoId", eventoId);
        registro.put("tipo", tipo);
        registro.put("situacao", situacao);
        registro.put("quarto", quarto);
        registro.put("ocorridoEm", ocorridoEm);
        registro.put("registradoEm", Instant.now().toString());

        if (situacao.equals("REPROVADA")) {
            // Pendencias podem vir como lista ["...", "..."] ou como texto
            JsonNode pendencias = dados.path("pendencias");
            registro.put("pendencias",
                pendencias.isMissingNode() ? java.util.List.of() : pendencias);
        }

        Files.writeString(
            ARQUIVO,
            jsonMapper.writeValueAsString(registro) + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );

        switch (situacao) {
            case "APROVADA" -> {
                System.out.println("[GOVERNANCA] Vistoria aprovada!");
                System.out.println("Quarto: " + quarto);
            }
            case "REPROVADA" -> {
                System.out.println("[GOVERNANCA] Vistoria reprovada!");
                System.out.println("Quarto: " + quarto);
                System.out.println("Pendencias: " + dados.path("pendencias"));
            }
            default -> {
                System.out.println("[GOVERNANCA] Evento de vistoria registrado.");
                System.out.println("Tipo: " + tipo);
                System.out.println("Quarto: " + quarto);
            }
        }
        System.out.println("Evento: " + eventoId);
    }

    private boolean jaRegistrado(String eventoId) throws Exception {
        if (!Files.exists(ARQUIVO)) {
            return false;
        }
        for (String linha : Files.readAllLines(ARQUIVO, StandardCharsets.UTF_8)) {
            if (linha.isBlank()) {
                continue;
            }
            JsonNode existente = jsonMapper.readTree(linha);
            if (eventoId.equals(existente.path("eventoId").asText())) {
                return true;
            }
        }
        return false;
    }
}
