"""
Option 1 backend: run GazeFollower on a laptop and STREAM gaze to the phone over
UDP. GazeFollower does webcam eye tracking (~1.1 cm after calibration) and gives
gaze in the LAPTOP screen's pixel coordinates; we forward each sample to the
phone, which maps laptop-px -> phone-px with its own short calibration.

--------------------------------------------------------------------------------
SETUP (on the laptop, same Wi-Fi as the phone):
    python -m pip install -r tools/gazefollower-requirements.txt
    (first run downloads the gaze model; needs a webcam + a reasonable CPU)

PHYSICAL RIG (important for accuracy):
    Put the PHONE within / just in front of the LAPTOP screen area, webcam above,
    user ~35-50 cm away. GazeFollower calibrates to the laptop screen, so the
    phone must sit inside that calibrated field for the gaze at the phone to be
    accurate.

RUN:
    # verify GazeFollower works first (prints gaze, no phone needed):
    python gazefollower_stream.py --print --phone-ip 0.0.0.0

    # then stream to the phone (get the IP from the Android Wi-Fi listener screen):
    python gazefollower_stream.py --phone-ip 192.168.1.42 --port 5005

FLOW: a preview window opens (check the camera sees your face; close it) ->
calibration dots on the laptop screen (look at each) -> streaming starts.
Each UDP packet is ASCII "x,y,timestamp" (x,y = laptop-screen pixels).
Ctrl+C to stop.
--------------------------------------------------------------------------------
"""

import argparse
import os
import socket
import time

from gazefollower import GazeFollower


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--phone-ip", required=True, help="phone's Wi-Fi IP (or 0.0.0.0 with --print)")
    parser.add_argument("--port", type=int, default=5005)
    parser.add_argument(
        "--coords",
        choices=["filtered", "calibrated", "raw"],
        default="filtered",
        help="which gaze coordinate to stream (filtered = smoothed, recommended)",
    )
    parser.add_argument("--print", action="store_true", help="also print gaze to the console")
    args = parser.parse_args()

    # Make Ctrl+C (and Ctrl+Break on Windows) kill the process instantly, before
    # GazeFollower's cleanup runs.
    import signal

    def _force_exit(*_):
        os._exit(0)

    signal.signal(signal.SIGINT, _force_exit)
    try:
        signal.signal(signal.SIGBREAK, _force_exit)  # Windows only
    except (AttributeError, ValueError):
        pass

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    addr = (args.phone_ip, args.port)
    sending = args.phone_ip not in ("0.0.0.0", "", None)
    sample_count = 0

    def on_gaze(face_info, gaze_info, *_):
        nonlocal sample_count
        _ = face_info
        if gaze_info is None or not gaze_info.status:
            return
        if args.coords == "filtered":
            coords = gaze_info.filtered_gaze_coordinates
        elif args.coords == "calibrated":
            coords = gaze_info.calibrated_gaze_coordinates
        else:
            coords = gaze_info.raw_gaze_coordinates
        if coords is None or len(coords) < 2:
            return
        x, y = coords
        sample_count += 1
        if sending:
            try:
                sock.sendto(f"{x:.1f},{y:.1f},{gaze_info.timestamp}".encode("ascii"), addr)
            except OSError:
                pass
        if args.print and sample_count % 5 == 0:
            print(f"gaze=({x:.0f}, {y:.0f})")

    gaze_follower = GazeFollower()
    try:
        gaze_follower.preview()
        gaze_follower.calibrate()
        gaze_follower.add_subscriber(on_gaze)
        # GazeFollower.start_sampling() also attaches its CSV writer, which crashes on
        # occasional frames where raw coordinates are missing. The UDP bridge only
        # needs live callbacks, so start the camera sampler directly.
        gaze_follower.camera.start_sampling()
        print(f"Streaming '{args.coords}' gaze to {addr if sending else '(print-only)'}. Ctrl+C to stop.")

        import pygame

        print("STOP: press ESC or Q in the gaze window, or Ctrl+C in this terminal.")
        last_t, last_n = time.time(), 0
        running = True
        while running:
            try:
                if pygame.get_init():
                    for event in pygame.event.get():
                        if event.type == pygame.QUIT:
                            running = False
                        elif event.type == pygame.KEYDOWN and event.key in (
                            pygame.K_ESCAPE,
                            pygame.K_q,
                        ):
                            running = False
            except Exception:
                pass
            time.sleep(0.05)
            now = time.time()
            if now - last_t >= 2.0:
                rate = (sample_count - last_n) / (now - last_t)
                print(f"[heartbeat] {sample_count} samples total, ~{rate:.0f}/s")
                last_t, last_n = now, sample_count
    except KeyboardInterrupt:
        print("\nStopping.")
    finally:
        try:
            gaze_follower.stop_sampling()
        except Exception:
            pass
        try:
            gaze_follower.release()
        except Exception:
            pass
        try:
            sock.close()
        except Exception:
            pass
        os._exit(0)


if __name__ == "__main__":
    main()

