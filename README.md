# Volts Driver

Painel para motoristas de aplicativo, feito para rodar em um tablet Android fixado no carro.
O app pode ser definido como **tela inicial** do tablet, que passa a ligar direto no painel.

## O que já faz

| Aba | Função |
| --- | --- |
| **Painel** | Atalhos grandes para Uber Driver, 99 Motorista, inDrive, Waze e Google Maps (e qualquer outro app que você fixar). "Abrir todos" inicia todos de uma vez; toque longo abre em tela dividida. Botão de iniciar/encerrar turno com km e tempo ao vivo. |
| **Postos** | Mapa e lista de postos de combustível e eletropostos próximos (dados do OpenStreetMap), com eletropostos da rede Volts em destaque e botão para navegar pelo Waze/Maps. |
| **Cobrar** | Teclado de valor e três formas de receber: Pix (gera QR Code e Copia e Cola da sua chave), aproximação/NFC (abre o app Tap to Pay da sua adquirente) e dinheiro. |
| **Relatório** | Km rodados, tempo online, valor recebido e, por app, quantas ofertas chegaram, valor médio, média por km e total ofertado — hoje, 7 ou 30 dias. |
| **Ajustes** | Tela inicial, apps do painel, leitura de ofertas, chave Pix, app de pagamento e raio de busca dos postos. |

Todos os dados ficam somente no aparelho (banco local).

## Limites conhecidos

- **Pagamento por aproximação:** receber cartão por NFC direto no tablet exige o SDK certificado de uma
  adquirente (Stone, Mercado Pago, PagBank, InfinitePay...) e um contrato com ela. Por enquanto o app
  abre o aplicativo Tap to Pay que o motorista já usa e registra o recebimento; a integração direta
  depende de escolher a adquirente. O tablet precisa ter NFC.
- **Pix:** o QR Code é estático (chave + valor). A confirmação do recebimento é manual, pois a
  confirmação automática exige a API de um banco/PSP.
- **Leitura de ofertas:** usa o serviço de acessibilidade do Android para ler valor, km e minutos dos
  cartões de oferta. A regra de reconhecimento é genérica e precisa ser calibrada com telas reais de
  cada app. Ainda não distingue ofertas aceitas de recusadas.
- **Tela dividida:** depende do suporte do tablet (Android 12L ou superior funciona melhor).

## Como compilar

Requer JDK 17+ e o Android SDK (plataforma 35) com `ANDROID_HOME` configurado.

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Estrutura

- `app/src/main/java/br/com/volts/driver/ui` – telas (Jetpack Compose)
- `apps` – catálogo e abertura dos outros aplicativos
- `tracking` – serviço de turno e cálculo de distância por GPS
- `offers` – leitura e interpretação das ofertas de corrida
- `pay` – geração do Pix (BR Code) e QR Code
- `stations` – busca de postos na Overpass API
- `data` – banco local (Room) e preferências
