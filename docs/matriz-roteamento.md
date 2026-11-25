# Matriz de roteamento

O produtor escolhe a `routing key` conforme o campo `tipo` no JSON e publica na exchange `hotel.eventos`, tipo `topic`.

## Tipos e chaves

| Tipo (`tipo`) | Routing key |
|---|---|
| `HOSPEDAGEM_CONFIRMADA` | `hospedagem.confirmada` |
| `HOSPEDAGEM_CANCELADA` | `hospedagem.cancelada` |
| `VISTORIA_APROVADA` | `vistoria.aprovada` |
| `VISTORIA_REPROVADA` | `vistoria.reprovada` |

A API atual retorna HTTP 400 para um tipo desconhecido.

## Filas e bindings

| Fila | Binding(s) | Consumidor |
|---|---|---|
| `hotel.recepcao` | `hospedagem.confirmada` | Recepção |
| `hotel.comunicacao` | `hospedagem.confirmada`, `hospedagem.cancelada`, `vistoria.reprovada` | Comunicação |
| `hotel.governanca` | `vistoria.#` | Governança |
| `hotel.auditoria` | `#` | Auditoria |
| `hotel.preparacao` | `hospedagem.confirmada` | Preparação (duas instâncias da mesma fila) |

## Evento × responsabilidade

| Evento | Recepção | Comunicação | Governança | Auditoria | Preparação | Total de filas |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Hospedagem confirmada | ✅ | ✅ | — | ✅ | ✅ | **4** |
| Hospedagem cancelada | — | ✅ | — | ✅ | — | **2** |
| Vistoria aprovada | — | — | ✅ | ✅ | — | **2** |
| Vistoria reprovada | — | ✅ | ✅ | ✅ | — | **3** |
| Novo evento `vistoria.*`* | — | — | ✅ | ✅ | — | **2** |
| Novo evento de outra família* | — | — | — | ✅ | — | **1** |

*Linhas de comportamento hipotético do **broker**. O produtor atual aceita somente quatro tipos; para publicar eventos novos pela API, é necessário ajustar seu mapeamento. Dois consumidores de preparação não significam duas cópias: ambos competem pelas mensagens da **mesma fila**.*
