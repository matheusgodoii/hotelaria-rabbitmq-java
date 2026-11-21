
package hotelaria.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    public TopicExchange hotelExchange() {
        return new TopicExchange("hotel.eventos", true, false);
    }

    @Bean
    public Queue filaRecepcao() {
        return new Queue("hotel.recepcao", true);
    }

    @Bean
    public Binding bindingRecepcao() {
        return BindingBuilder
                .bind(filaRecepcao())
                .to(hotelExchange())
                .with("hospedagem.confirmada");
    }


    // FILA DA COMUNICACAO
    @Bean
    public Queue filaComunicacao() {
        return new Queue("hotel.comunicacao", true);
    }

    @Bean
    public Binding comunicacaoConfirmada() {
        return BindingBuilder.bind(filaComunicacao())
                .to(hotelExchange())
                .with("hospedagem.confirmada");
    }

    @Bean
    public Binding comunicacaoCancelada() {
        return BindingBuilder.bind(filaComunicacao())
                .to(hotelExchange())
                .with("hospedagem.cancelada");
    }

    @Bean
    public Binding comunicacaoVistoriaReprovada() {
        return BindingBuilder.bind(filaComunicacao())
                .to(hotelExchange())
                .with("vistoria.reprovada");
    }

    // FILA DA GOVERNANCA
    @Bean
    public Queue filaGovernanca() {
        return new Queue("hotel.governanca", true);
    }

    @Bean
    public Binding governancaVistorias() {
        return BindingBuilder.bind(filaGovernanca())
                .to(hotelExchange())
                .with("vistoria.#");
    }

    // FILA DA AUDITORIA
    @Bean
    public Queue filaAuditoria() {
        return new Queue("hotel.auditoria", true);
    }

    @Bean
    public Binding auditoriaTodosEventos() {
        return BindingBuilder.bind(filaAuditoria())
                .to(hotelExchange())
                .with("#");
    }

}
