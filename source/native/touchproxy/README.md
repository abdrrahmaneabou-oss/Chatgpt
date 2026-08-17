# PixelTrigger Unified Touch Proxy

Goal: keep the player's physical fingers and PixelTrigger's synthetic tap in **one Linux multi-touch device stream**.

The daemon:

1. opens the physical touchscreen evdev node;
2. clones its identity/capabilities into a uinput touchscreen with one additional MT slot;
3. grabs the physical evdev node only after the virtual device is ready;
4. relays physical events without blocking;
5. reserves the additional MT slot for PixelTrigger;
6. receives `TAP` commands from the app over a Unix-domain socket;
7. emits synthetic `TRACKING_ID/POSITION` down and up while continuing to relay physical events;
8. releases `EVIOCGRAB` and destroys uinput on every normal/signal shutdown path.

The 1 ms contact is scheduled with `timerfd`, never `sleep`, so the relay loop does not stop while the synthetic pointer is down.

This backend requires root/privileged access to `/dev/input/event*`, `EVIOCGRAB`, and `/dev/uinput`. It is intentionally isolated from the white-detection engine.
