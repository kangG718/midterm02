"""Mouse-selected watch zone and short-lived geometric person tracking."""

import json
import math
import time
from dataclasses import dataclass
from pathlib import Path

import cv2


@dataclass
class Track:
    id: int
    box: tuple
    last_seen: float
    visible: bool = True
    inside: bool = False
    dwell: float = 0.0
    reported: bool = False


def match_cost(a, b):
    """Reject large jumps; prefer overlapping boxes with nearby centers."""
    ax, ay = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
    bx, by = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    scale = max(math.hypot(a[2] - a[0], a[3] - a[1]), 1)
    distance = math.hypot(ax - bx, ay - by) / scale
    intersection = max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(
        0, min(a[3], b[3]) - max(a[1], b[1])
    )
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - intersection
    iou = intersection / max(union, 1)
    if distance > 0.5 or (iou < 0.1 and distance > 0.25):
        return None
    return 1 - iou + distance


class IntrusionMonitor:
    def __init__(self, zone, dwell_seconds=2.0, missing_seconds=0.5):
        if not math.isfinite(dwell_seconds) or dwell_seconds <= 0:
            raise ValueError("Dwell time must be a positive finite number")
        if len(zone) != 4 or not (0 <= zone[0] < zone[2] <= 1 and 0 <= zone[1] < zone[3] <= 1):
            raise ValueError("Watch zone must contain normalized x1, y1, x2, y2 coordinates")
        self.zone = tuple(zone)
        self.dwell_seconds = dwell_seconds
        self.missing_seconds = missing_seconds
        self.tracks = {}
        self.next_id = 1
        self.events = 0
        self.upload_status = "No events yet"

    def update(self, boxes, width, height, now=None):
        """Return newly confirmed tracks; missing observations never add dwell time."""
        now = time.monotonic() if now is None else now
        self.tracks = {i: t for i, t in self.tracks.items() if now - t.last_seen <= self.missing_seconds}
        boxes = [tuple(float(v) for v in b) for b in boxes]
        edges = []
        for ident, track in self.tracks.items():
            for index, box in enumerate(boxes):
                cost = match_cost(track.box, box)
                if cost is not None:
                    edges.append((cost, ident, index))
        matched, used = {}, set()
        for _, ident, index in sorted(edges):
            if ident not in matched and index not in used:
                matched[ident] = index
                used.add(index)
        for index, box in enumerate(boxes):
            if index not in used:
                ident = self.next_id
                self.next_id += 1
                self.tracks[ident] = Track(ident, box, now, visible=False)
                matched[ident] = index

        events = []
        x1, y1, x2, y2 = self.zone
        for ident, track in self.tracks.items():
            if ident not in matched:
                track.visible = False
                continue
            box = boxes[matched[ident]]
            # Any positive-area overlap of the person box and watch zone counts as entry.
            inside = (
                max(box[0], x1 * width) < min(box[2], x2 * width)
                and max(box[1], y1 * height) < min(box[3], y2 * height)
            )
            if not inside:
                track.dwell = 0.0
                track.reported = False
            elif track.inside and track.visible:
                track.dwell += max(0, now - track.last_seen)
            track.box, track.last_seen = box, now
            track.inside, track.visible = inside, True
            if inside and not track.reported and track.dwell + 1e-9 >= self.dwell_seconds:
                track.reported = True
                self.events += 1
                events.append(track)
        return events

    def pause(self, seconds):
        """Exclude blocking upload time from both dwell and track expiration."""
        for track in self.tracks.values():
            track.last_seen += seconds

    def draw(self, frame):
        height, width = frame.shape[:2]
        x1, y1, x2, y2 = [round(v * (width if i % 2 == 0 else height)) for i, v in enumerate(self.zone)]
        cv2.rectangle(frame, (x1, y1), (min(x2, width - 1), min(y2, height - 1)), (255, 200, 0), 3)
        font_scale = max(0.55, width / 1600)
        thickness = max(1, round(width / 800))
        for track in self.tracks.values():
            if not track.visible:
                continue
            status = "OUTSIDE" if not track.inside else ("INTRUSION" if track.reported else "DWELLING")
            color = (0, 0, 255) if track.reported else ((0, 200, 255) if track.inside else (160, 160, 160))
            a, b, c, d = [round(v) for v in track.box]
            cv2.rectangle(frame, (a, b), (c, d), color, 3)
            label = f"ID {track.id} {status} {track.dwell:.1f}/{self.dwell_seconds:g}s"
            (label_width, _), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness)
            label_x = max(0, min(a, width - label_width - 4))
            cv2.putText(frame, label, (label_x, min(height - 8, max(95, b - 12))), cv2.FONT_HERSHEY_SIMPLEX,
                        font_scale, color, thickness, cv2.LINE_AA)
        # OpenCV's built-in font is ASCII; Korean event text is sent to the blog.
        cv2.rectangle(frame, (0, 0), (width, 72), (25, 25, 25), -1)
        cv2.putText(frame, f"WATCH ZONE | dwell {self.dwell_seconds:g}s | events {self.events}",
                    (10, 28), cv2.FONT_HERSHEY_SIMPLEX, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)
        cv2.putText(frame, self.upload_status, (10, 60), cv2.FONT_HERSHEY_SIMPLEX,
                    font_scale, (255, 255, 255), thickness, cv2.LINE_AA)


def open_monitor(frame, config_path, dwell_seconds, reset=False):
    """Restore normalized ROI, or select it once using a scaled camera preview."""
    config_path = Path(config_path)
    if config_path.exists() and not reset:
        zone = json.loads(config_path.read_text())["zone"]
        return IntrusionMonitor(zone, dwell_seconds)
    height, width = frame.shape[:2]
    scale = min(1, 960 / width, 640 / height)
    preview = cv2.resize(frame, (round(width * scale), round(height * scale)))
    window = "Select watch zone - drag, ENTER to confirm, C to cancel"
    x, y, w, h = cv2.selectROI(window, preview, showCrosshair=True, fromCenter=False)
    cv2.destroyWindow(window)
    if w <= 0 or h <= 0:
        raise SystemExit("Watch zone selection cancelled; no event uploaded.")
    ph, pw = preview.shape[:2]
    zone = (x / pw, y / ph, (x + w) / pw, (y + h) / ph)
    monitor = IntrusionMonitor(zone, dwell_seconds)
    config_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = config_path.with_suffix(".tmp")
    temporary.write_text(json.dumps({"zone": zone}, indent=2))
    temporary.replace(config_path)
    return monitor
