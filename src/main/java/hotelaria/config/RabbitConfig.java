
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
}
