# Signal protocol + WebRTC ship native/JNI code; keep their classes intact under R8.
-keep class org.signal.libsignal.** { *; }
-keep class org.webrtc.** { *; }
