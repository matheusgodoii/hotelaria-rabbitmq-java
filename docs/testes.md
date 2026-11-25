# Roteiro de testes e demonstração

Antes dos testes: executar `docker compose up -d`, compilar com `.\mvnw.cmd clean package` e iniciar as aplicações indicadas no [README](../README.md). Os comandos abaixo usam PowerShell, na pasta do projeto. Em cada teste, utilize identificadores novos, exceto no teste proposital de duplicidade.

## Contrato JSON

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

Exemplo de vistoria reprovada:

```json
{
  "eventoId": "evt-vistoria-001",
  "tipo": "VISTORIA_REPROVADA",
  "ocorridoEm": "2026-11-24T20:05:00-03:00",
  "dados": {
    "quarto": 204,
    "pendencias": ["Trocar toalhas", "Limpar banheiro"]
  }
}
```

O produtor exige `eventoId`, `tipo`, `ocorridoEm` e um objeto `dados`; cada consumidor também depende dos dados necessários ao seu efeito.

## Publicar um evento (PowerShell)

```powershell
$evento = @{
  eventoId = "evt-" + [guid]::NewGuid().ToString("N")
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

## Demonstração principal (até 20 minutos)

| Ordem | Teste | Procedimento | Evidência esperada | Tempo |
|---|---|---|---|---|
| 1 | Arquitetura e contrato | Mostrar diagrama, exchange, cinco filas e exemplo JSON | Explicar produtor → broker → consumidor | 3 min |
| 2 | Roteamento | Publicar os quatro tipos e comparar com a matriz | Recepção/preparação recebem apenas confirmação; governança só vistoria; auditoria tudo | 5 min |
| 3 | Concorrência | Iniciar duas instâncias de `preparacao`; publicar eventos distintos | Ambas conectadas à `hotel.preparacao`; tarefas distribuídas com identificação da instância | 3 min |
| 4 | Parada e recuperação | Desligar `comunicacao`; publicar confirmação; verificar fila e religar | Comunicação pendente (`Ready`) e depois processada; recepção e auditoria não param | 4 min |
| 5 | Duplicidade + ACK | Republicar o **mesmo JSON** com mesmo `eventoId`; verificar `chegadas.csv` | Uma única chegada para a hospedagem; explicar que o Spring envia ACK após método concluir sem erro | 5 min |

## Teste de concorrência — exemplo

Inicie as duas instâncias de preparação como no README. Para facilitar a visualização, publique oito eventos novos:

```powershell
for ($i = 201; $i -le 208; $i++) {
  $evento = @{
    eventoId = "evt-demo-$i"
    tipo = "HOSPEDAGEM_CONFIRMADA"
    ocorridoEm = (Get-Date).ToString("o")
    dados = @{
      hospedagemId = "HDEMO-$i"
      hospede = "Hospede $i"
      quarto = $i
      dataChegada = "2026-12-01"
    }
  }
  $json = $evento | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Uri "http://localhost:8080/eventos" -Method Post -ContentType "application/json" -Body $json
  Start-Sleep -Milliseconds 300
}
```

```powershell
Import-Csv .\dados\tarefas.csv -Delimiter ";" -Header eventoId,hospedagemId,quarto,dataChegada,instancia |
  Group-Object instancia | Select-Object Name,Count
```

A divisão não precisa ser 4/4. Se esses IDs já foram usados em testes anteriores, altere o intervalo.

## Teste de recuperação

1. Deixe produtor, recepção, auditoria e comunicação iniciados.
2. Interrompa apenas comunicação (`Ctrl+C`). No painel, confira `Consumers = 0` para `hotel.comunicacao`.
3. Publique **uma hospedagem confirmada com novos IDs**.
4. Confira que recepção/auditoria processaram e que `hotel.comunicacao` tem uma mensagem `Ready` a mais (caso não haja outras atividades).
5. Reinicie comunicação: ela deverá consumir a pendência e registrar `dados/comunicacao.jsonl`.

## Teste de duplicidade da recepção

1. Inicie o produtor e a recepção. Envie uma confirmação usando o exemplo PowerShell desta página.
2. **Sem alterar `$json`**, envie exatamente o mesmo corpo novamente:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/eventos" -Method Post -ContentType "application/json" -Body $json
```

3. Verifique que `dados/chegadas.csv` possui uma única linha com esse `eventoId`. A recepção deve indicar que a hospedagem já estava registrada.

```powershell
(Get-Content .\dados\chegadas.csv | Select-String -SimpleMatch $evento.eventoId).Count
```

Resultado esperado: `1`, se o primeiro processamento concluiu. A auditoria pode gravar as duas publicações no histórico; isso não é uma segunda chegada.

## Casos adicionais

| Caso | Procedimento | Esperado |
|---|---|---|
| Evento incompleto | Remover `eventoId` ou `dados` | HTTP 400 |
| Tipo inválido | Usar `tipo = QUALQUER_COISA` | HTTP 400 |
| Vistoria aprovada | Publicar `VISTORIA_APROVADA` com `dados.quarto` | Governança registra situação aprovada; auditoria registra evento |
| Vistoria reprovada | Publicar `VISTORIA_REPROVADA` com `dados.quarto` e `dados.pendencias` | Governança registra reprovação e pendências; comunicação registra aviso; auditoria registra evento |
| Evento futuro | Explicar `vistoria.#` sem precisar enviar pela API | Novas chaves da família serão roteadas, mas produtor atual requer atualização |

## Onde conferir

- RabbitMQ: http://localhost:15672 → `Exchanges` e `Queues and Streams` (`Ready`, `Unacked`, `Consumers`).
- Terminal de cada consumidor.
- Arquivos `dados/chegadas.csv`, `dados/comunicacao.jsonl`, `dados/governanca.jsonl`, `dados/auditoria.jsonl`, `dados/tarefas.csv`.

**Cuidados:** a auditoria grava os eventos recebidos, inclusive publicações repetidas. ACK significa confirmação após processamento, não garantia de execução exatamente uma vez em qualquer cenário. Evite apagar o volume do RabbitMQ durante os testes de recuperação.
