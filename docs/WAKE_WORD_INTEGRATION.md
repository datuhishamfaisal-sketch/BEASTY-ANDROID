# BEASTY Wake Word Integration (sherpa-onnx)

Status: implementation added on `feature/sherpa-onnx-wakeword`; automated Android build and physical-device verification are still pending.

## Selected engine

BEASTY uses sherpa-onnx KeywordSpotter with the English Zipformer GigaSpeech KWS model:

- Model: sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
- Upstream model release: https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- Android native release: https://github.com/k2-fsa/sherpa-onnx/releases
- Android KWS sample: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- KWS documentation: https://k2-fsa.github.io/sherpa/onnx/kws/

## Current implementation

- A GitHub Actions workflow builds the official sherpa-onnx Android AAR from upstream source and downloads the official Android native libraries.
- The workflow downloads the matching English KWS model and generates the custom `HEY BEASTY` keyword tokens with that model's BPE tokenizer.
- `WakeWordService.kt` runs local/offline keyword spotting from a microphone foreground service.
- `MainActivity.java` has a user-controlled Hey BEASTY on/off control. When the wake phrase is detected, it hands over to the existing speech-recognition flow.
- The foreground notification shows listening status and includes a stop action.
- Existing Gemini, Groq, manual MIC, chat, and text-to-speech code is retained.

## Runtime and safety notes

- Microphone permission must be granted before enabling wake-word detection.
- Start the microphone foreground service from the visible app after a user action. Android background microphone restrictions still apply.
- Wake-word detection is local; the wake phrase model does not need an API key or network connection.
- The current model is English. Detection accuracy and sensitivity for different accents, devices, and noisy environments must be tested on a physical Android device.
- The app must be tested for microphone hand-off, screen-off behavior, battery use, notification stop action, and restart after voice input before declaring the feature fully verified.

## Build

The workflow is `.github/workflows/android-build.yml`. It creates the Sherpa-ONNX AAR and packages the model assets during CI, then uploads a debug APK artifact named `BEASTY-debug-apk`.
