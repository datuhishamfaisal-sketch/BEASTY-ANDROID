# BEASTY Wake Word Integration (sherpa-onnx)

Status: preparation branch only. Wake-word detection is NOT enabled in the app yet.

## Selected engine

Use sherpa-onnx KeywordSpotter with the English Zipformer GigaSpeech KWS model:

- Model: sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
- Upstream model release: https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- Android native release: https://github.com/k2-fsa/sherpa-onnx/releases
- Android KWS sample: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- KWS documentation: https://k2-fsa.github.io/sherpa/onnx/kws/

The KWS model supports custom phrases by generating a keywords file from the model's BPE tokenizer. The phrase planned for BEASTY is `HEY BEASTY`.

## Required model assets

The English model archive contains the encoder, decoder, joiner, `tokens.txt`, and `bpe.model`. For an Android app, the assets must be copied under:

`app/src/main/assets/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/`

The custom `keywords.txt` must be generated with the matching `tokens.txt` and `bpe.model`; do not hand-write token pieces.

## Required Android native library

The app also needs sherpa-onnx Android native libraries for the target device ABI (at minimum `arm64-v8a`, ideally also `armeabi-v7a`). These are distributed in the upstream Android release archive. They must be placed in the app's `jniLibs/<abi>/` directories and paired with the matching Kotlin/Java API sources.

## Integration safety

- Do not start a microphone service until the native library and all model assets are present.
- Keep the existing Gemini, Groq, manual MIC, and text-to-speech code intact.
- Implement wake-word listening as a user-controlled foreground microphone service with a persistent notification and an explicit stop action.
- On Android 14+ / target SDK 36, request microphone permission while the app is visible and start the microphone foreground service only from an allowed, user-initiated flow.
- When the keyword is detected, stop/release the KWS stream and hand off to the existing voice interaction path; avoid opening a second microphone recorder at the same time.
- Verify the build and test on a physical Android device before describing wake-word detection as working.

## Current repository findings

- `AndroidManifest.xml` already declares `.WakeWordService`, but that Java class is not present on the main branch.
- `MainActivity.java` currently has a manual SpeechRecognizer MIC button and Gemini/Groq chat.
- `app/build.gradle` currently has no sherpa-onnx dependency or Kotlin plugin.
- This branch intentionally does not add a fake service or a placeholder dependency that could make the app fail to build.
