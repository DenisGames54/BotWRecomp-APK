# BotWRecomp-APK

Launcher + empacotamento Android do [BotWRecomp-Android](https://github.com/DenisGames54/BotWRecomp-Android)
(fork Android/Vulkan do [BotWRecomp](https://github.com/IshaanKalra2103/BotWRecomp)).

> [!IMPORTANT]
> **Este projeto NAO fornece o jogo nem nenhum tipo de arquivo protegido** (ROMs, chaves, dumps, codigo do jogo,
> nem binarios derivados dele). E apenas uma ferramenta. Voce precisa do seu proprio dump legal do
> *The Legend of Zelda: Breath of the Wild* (Wii U, USA, update 1.6.0 / v208), feito do seu console.
> O APK e **gerado na sua maquina a partir do seu dump** e **nao pode ser distribuido**.
> Nao peca arquivos do jogo em issues ou discussoes.

> [!WARNING]
> Estagio inicial. Hoje so o launcher existe (fase 1); o motor nativo ainda nao esta ligado ao APK.

## O que o launcher faz
- Escolhe a pasta do jogo (`code/ content/ meta/`) **sem copiar nada**: le direto da memoria interna
  (requer "Acesso a todos os arquivos").
- Verifica o SHA-256 do `U-King.rpx` (v208 USA).
- Edita as opcoes reais do runtime, gravando o mesmo `settings.ini` que o menu do jogo usa:
  fps, resolucao, tela, filtro, touch, botoes, FPS na tela, giroscopio, vibracao, volume.
- Dados do usuario (saves, cache, log, settings) ficam em `/storage/emulated/0/BotWRecomp/user`.

## Build (WSL2, Windows)
```bash
git clone --recurse-submodules https://github.com/DenisGames54/BotWRecomp-APK
cd BotWRecomp-APK
scripts/setup_wsl.sh        # uma vez: JDK, SDK, NDK, Gradle
scripts/build_apk.sh        # gera app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap
1. Launcher (esta fase).
2. Build NDK do runtime (`libmain.so`) + `GameActivity` (SDL3) + touch.
3. Script que recompila o *seu* dump e gera o APK pessoal.
4. Importar drivers Turnip customizados (testes de desempenho).

Veja `NOTICE` para creditos.
