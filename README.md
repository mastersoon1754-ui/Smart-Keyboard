# AZERTY

Clavier français pour Android. L’application sert à l’activer et à la régler. La saisie elle-même est un `InputMethodService`.

L’assistant (Gemini ou Mistral) est un panneau dans la barre du clavier. Il ne lit pas l’écran et n’écrit pas dans le champ tant que l’utilisateur ne le demande pas. Les clés API sont chiffrées dans le coffre Android, sur l’appareil.

## Compilation

```bash
./gradlew test assembleDebug
```

Le SDK Android 35 et les build-tools 34 sont requis. `local.properties` peut contenir `sdk.dir`.
