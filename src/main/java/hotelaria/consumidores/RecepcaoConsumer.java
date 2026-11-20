
package hotelaria.consumidores;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
@Profile("recepcao")
public class RecepcaoConsumer {

    private final JsonMapper objectMapper;

    public RecepcaoConsumer(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "hotel.recepcao")
    public void receber(String mensagem) throws Exception {

        JsonNode evento = objectMapper.readTree(mensagem);
        JsonNode dados = evento.path("dados");

        String eventoId = evento.path("eventoId").asText();
        String hospedagemId = dados.path("hospedagemId").asText();
        String quarto = dados.path("quarto").asText();
        String dataChegada = dados.path("dataChegada").asText();

        if (eventoId.isBlank() || hospedagemId.isBlank()
                || quarto.isBlank() || dataChegada.isBlank()) {
            throw new IllegalArgumentException("Dados incompletos");
        }

        Path arquivo = Path.of("dados", "chegadas.csv");
        Files.createDirectories(arquivo.getParent());

        // Verificar se a hospedagem ja foi registrada
        if (Files.exists(arquivo)) {
            for (String linha : Files.readAllLines(arquivo)) {
                String[] colunas = linha.split(";", -1);

                if (colunas.length >= 2 &&
                    (colunas[0].equals(eventoId) ||
                     colunas[1].equals(hospedagemId))) {

                    System.out.println(
                            "[RECEPCAO] Hospedagem ja registrada!"
                    );
                    return;
                }
            }
        }

        // Registrar chegada em arquivo
        String registro = eventoId + ";"
                + hospedagemId + ";"
                + quarto + ";"
                + dataChegada
                + System.lineSeparator();

        Files.writeString(
                arquivo,
                registro,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println("[RECEPCAO] Hospedagem confirmada!");
        System.out.println("Hospedagem: " + hospedagemId);
        System.out.println("Quarto: " + quarto);
        System.out.println("Chegada: " + dataChegada);
    }
}
