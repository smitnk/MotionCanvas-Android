from pathlib import Path
p = Path("build-source/app/build.gradle.kts")
s = p.read_text()
s = s.replace("androidx.media3:media3-transformer:1.11.1", "androidx.media3:media3-transformer:1.4.1")
s = s.replace("androidx.media3:media3-common:1.11.1", "androidx.media3:media3-common:1.4.1")
p.write_text(s)
print("Media3 dependencies pinned to 1.4.1 for compileSdk 34 / AGP 8.5.2")
