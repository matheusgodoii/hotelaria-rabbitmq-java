# Hotelaria com RabbitMQ — Sistemas Distribuídos

Projeto acadêmico de mensageria usando **Java 21, Spring Boot 4.1.2 e RabbitMQ**. Uma API publica quatro tipos de eventos, e cinco responsabilidades independentes processam mensagens de suas filas.

**Fluxo:** `POST /eventos` → produtor → exchange `hotel.eventos` (`topic`) → filas → consumidores → registros locais em `dados/`.

Documentação: [Arquitetura](docs/arquitetura.md) · [Matriz de roteamento](docs/matriz-roteamento.md) · [Testes](docs/testes.md).

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 e Spring Boot 4.1.2 | API e aplicações consumidoras |
| Spring AMQP | `RabbitTemplate`, `@RabbitListener` e ACK |
| Jackson 3 (`tools.jackson`) | Processamento JSON |
| RabbitMQ 4.2 com Management | Exchange, filas, bindings e monitoramento |
| Docker Compose | Executar o broker localmente |
| Maven Wrapper | Compilar e empacotar |

## Estrutura

```text
src/main/java/com/hotelaria/hotelaria/
├── HotelariaApplication.java
├── config/RabbitConfig.java
├── produtor/EventoController.java
└── consumidores/
    ├── RecepcaoConsumer.java       -> dados/chegadas.csv
    ├── ComunicacaoConsumer.java    -> dados/comunicacao.jsonl
    ├── GovernancaConsumer.java     -> dados/governanca.jsonl
    ├── AuditoriaConsumer.java      -> dados/auditoria.jsonl
    └── PreparacaoConsumer.java     -> dados/tarefas.csv
```

## Como executar no Windows (PowerShell)

Pré-requisitos: **JDK 21**, **Docker Desktop** e terminal na pasta do projeto que contém `pom.xml`. Inicie o Docker Desktop antes.

1. Iniciar RabbitMQ:

```powershell
docker compose up -d
```

Painel: http://localhost:15672 — login de desenvolvimento: `hotel` / `hotel123`.

2. Compilar:

```powershell
.\mvnw.cmd clean package
```

3. Abrir **um terminal por aplicação** e executar os comandos abaixo, mantendo os terminais abertos. Se o JAR tiver outro nome, confira a pasta `target/` e substitua-o nos comandos.

**Produtor:**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=produtor
```

**Recepção:**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=recepcao --spring.main.web-application-type=none
```

**Comunicação:**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=comunicacao --spring.main.web-application-type=none
```

**Governança:**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=governanca --spring.main.web-application-type=none
```

**Auditoria:**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=auditoria --spring.main.web-application-type=none
```

**Preparação (duas instâncias, dois terminais separados):**

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=preparacao --spring.main.web-application-type=none --app.instancia=preparacao-1
```

```powershell
java -jar .\target\hotelaria-0.0.1-SNAPSHOT.jar --spring.profiles.active=preparacao --spring.main.web-application-type=none --app.instancia=preparacao-2
```

As duas instâncias de preparação usam **a mesma fila** `hotel.preparacao`; elas não recebem duas cópias independentes. Não são necessárias interfaces gráficas, autenticação ou implantação em nuvem.

4. Enviar evento de exemplo usando um terminal PowerShell:

```powershell
$evento = @{
    eventoId = "evt-exemplo-" + [guid]::NewGuid().ToString("N")
    tipo = "HOSPEDAGEM_CONFIRMADA"
    ocorridoEm = (Get-Date).ToString("o")
    dados = @{
        hospedagemId = "H-" + [guid]::NewGuid().ToString("N").Substring(0,8)
        hospede = "Maria Souza"
        quarto = 204
        dataChegada = "2026-12-01"
    }
}
$json = $evento | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "http://localhost:8080/eventos" -Method Post -ContentType "application/json" -Body $json
```

5. Conferir logs, filas no painel do RabbitMQ e os arquivos na pasta `dados/`.

## Contrato dos eventos

Cada evento contém `eventoId` (identificador da publicação), `tipo`, `ocorridoEm` e objeto `dados`. Os campos dentro de `dados` variam conforme o tipo e os efeitos esperados. Exemplo:

```json
{
  "eventoId": "evt-001",
  "tipo": "HOSPEDAGEM_CONFIRMADA",
  "ocorridoEm": "2026-11-24T20:00:00-03:00",
  "dados": {
    "hospedagemId": "H001",
    "hospede": "Maria Souza",
    "quarto": 204,
    "dataChegada": "2026-12-01"
  }
}
```

Tipos atualmente aceitos pelo produtor: `HOSPEDAGEM_CONFIRMADA`, `HOSPEDAGEM_CANCELADA`, `VISTORIA_APROVADA`, `VISTORIA_REPROVADA`.

## Comportamento e limitações

- As filas são duráveis, e o produtor marca as mensagens como persistentes.
- O consumo utiliza ACK gerenciado pelo Spring: confirmação após conclusão bem-sucedida do método; falhas podem provocar reentrega conforme a configuração.
- Recepção e preparação fazem verificação de duplicidade antes de registrar os efeitos; governança também verifica `eventoId`. A auditoria registra eventos recebidos; **não se garante efeito único em todos os consumidores**.
- Os arquivos locais são adequados à demonstração, mas não substituem um banco transacional para implantação distribuída real. O bloqueio de arquivo na preparação é local e pressupõe que as duas instâncias enxerguem o mesmo arquivo.
- Um evento novo com chave `vistoria.*` será encaminhado à governança e à auditoria pelos bindings existentes, **mas o produtor atual só aceita os quatro tipos listados**. Para demonstrar um evento novo pela API, é preciso ampliar o mapeamento do produtor.

## Equipe

| Integrante | Responsabilidade |
|---|---|
| Integrante 1 | Produtor, RabbitMQ, recepção, auditoria, preparação e testes de mensageria |
| Integrante 2 | Comunicação com hóspedes |
| Integrante 3 | Governança |
| Integrante 4 | Documentação e diagrama |

Substituir os rótulos acima pelos nomes reais antes da entrega.
