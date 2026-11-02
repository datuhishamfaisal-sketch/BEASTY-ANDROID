# BEASTY Android

Fresh Android starter project for the BEASTY assistant.

## Included in this first build
- Android app project (Java 17, Android SDK 36, minimum Android 8.0 / API 26)
- Chat interface
- Smart Mode, Fast Mode and Deep Mode response instructions
- Voice input through Android speech recognition
- Spoken replies through Android Text-to-Speech
- Gemini first, Groq automatic fallback
- GitHub Actions workflow to build and upload a debug APK
- Module roadmap labels for Client Memory, Business Hunter, Web Creator, App Creator, AI Video Creator and Trend Hunter

The specialist modules listed in the roadmap are not implemented yet in this starter version. They will be added in later development steps.

## Add API keys safely
In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**.
Create:
- `GEMINI_API_KEY`
- `GROQ_API_KEY`

Never paste API keys into source files or commit them to GitHub. You can add one provider key or both; BEASTY tries Gemini first and falls back to Groq when Gemini is unavailable.

## Build
Push the project to the `main` branch, or open **Actions → Build BEASTY APK → Run workflow**.
When the workflow finishes, download the artifact named `BEASTY-debug-apk`.

## Package
Application ID: `com.beasty.assistant`
Version: `1.0`
