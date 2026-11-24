
package hotelaria.consumidores;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Profile("preparacao")
public class PreparacaoConsumer {

    private final JsonMapper jsonMapper;

    @Value("${app.instancia}")
    private String instancia;

    public PreparacaoConsumer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = "hotel.preparacao")
    public void receber(String mensagem) throws Exception {

        // Converter a mensagem JSON
        JsonNode evento = jsonMapper.readTree(mensagem);
        JsonNode dados = evento.path("dados");

        String eventoId = evento.path("eventoId").asText();
        String hospedagemId = dados.path("hospedagemId").asText();
        String quarto = dados.path("quarto").asText();
        String dataChegada = dados.path("dataChegada").asText();

        // Validar os campos obrigatorios
        if (eventoId.isBlank() || hospedagemId.isBlank()
                || quarto.isBlank() || dataChegada.isBlank()) {
            throw new IllegalArgumentException(
                    "Dados da hospedagem incompletos"
            );
        }

        // Arquivo compartilhado pelas duas instancias
        Path arquivo = Path.of("dados", "tarefas.csv");

        Files.createDirectories(arquivo.getParent());

        boolean registrado = false;

        // O bloqueio impede as duas instancias de
        // gravarem no arquivo ao mesmo tempo
        try (RandomAccessFile raf =
                     new RandomAccessFile(arquivo.toFile(), "rw");
             FileChannel canal = raf.getChannel();
             FileLock bloqueio = canal.lock()) {

            // Verificar se o evento ja foi processado
            raf.seek(0);

            boolean duplicado = false;
            String linha;

            while ((linha = raf.readLine()) != null) {
                String[] colunas = linha.split(";", -1);

                if (colunas.length >= 2 &&
                    (colunas[0].equals(eventoId) ||
                     colunas[1].equals(hospedagemId))) {
                    duplicado = true;
                    break;
                }
            }

            if (!duplicado) {

                String registro =
                        eventoId + ";" +
                        hospedagemId + ";" +
                        quarto + ";" +
                        dataChegada + ";" +
                        instancia + "\n";

                // Gravar a tarefa no final do arquivo
                raf.seek(raf.length());

                raf.write(
                    registro.getBytes(StandardCharsets.UTF_8)
                );

                // Solicitar persistencia da escrita
                canal.force(true);

                registrado = true;
            }
        }

        if (registrado) {
            System.out.println(
                "[" + instancia + "] Tarefa criada!"
            );
            System.out.println("Hospedagem: " + hospedagemId);
            System.out.println("Quarto: " + quarto);
            System.out.println("Chegada: " + dataChegada);
        } else {
            System.out.println(
                "[" + instancia + "] Tarefa ja existente: "
                + hospedagemId
            );
        }
    }
}

