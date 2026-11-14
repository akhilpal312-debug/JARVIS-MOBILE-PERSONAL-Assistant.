# Bundled assets and attribution — Jarvis

The Jarvis APK ships its speech models and runtimes so the user never downloads
a separate model.

| Component | Version / model | Licence | Source |
|---|---|---|---|
| sherpa-onnx runtime (JNI + Kotlin API) | v1.12.24 | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx |
| ONNX Runtime (Android) | bundled with sherpa-onnx v1.12.24 | MIT | https://github.com/microsoft/onnxruntime |
| Whisper tiny (multilingual, int8 encoder+decoder) | csukuangfj/sherpa-onnx-whisper-tiny | MIT (Whisper) | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny |
| Hindi voice: VITS / Piper hi_IN-priyamvada-medium | tts-models release | MIT (Piper voices) | https://github.com/k2-fsa/sherpa-onnx/releases (tts-models) |
| English voice: VITS / Piper en_US-lessac-medium | tts-models release | MIT (Piper voices) | https://github.com/k2-fsa/sherpa-onnx/releases (tts-models) |
| espeak-ng data (phoneme dictionaries used by the Piper voices) | bundled with the voice packages | GPL-3.0 | https://github.com/espeak-ng/espeak-ng |

Model cards shipped with the voice packages are kept beside the models
(`assets/models/tts/hi/MODEL_CARD`, `assets/models/tts/en/MODEL_CARD`).

Because the espeak-ng data is GPL-3.0, redistribution of this APK must satisfy
the GPL for those data files: the licence text is included here and the exact
upstream source is https://github.com/espeak-ng/espeak-ng.
