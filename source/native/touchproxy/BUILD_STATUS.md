# Touchproxy build status

This file intentionally triggers the input-engine CI after the strict `timerfd` read-result fix.

Expected gates:
- frozen PixelTrigger v2.12 detection-engine tests pass;
- host C++20 compile passes with `-Wall -Wextra -Werror`;
- Android NDK arm64-v8a build passes;
- Android NDK x86_64 build passes.
