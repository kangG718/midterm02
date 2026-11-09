"""Durable, single-worker uploads and a persistent recovery history for the camera UI."""
import atexit
from contextlib import contextmanager
import json
import os
import sqlite3
import threading
import time
import uuid
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
import requests
from filelock import FileLock, Timeout


class UploadQueue:
    def __init__(self, directory, host, username, password, retry_failed=False):
        self.directory = Path(directory).resolve()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.host, self.username, self.password = host.rstrip('/'), username, password
        self.lock = FileLock(str(self.directory / 'worker.lock'))
        try:
            self.lock.acquire(timeout=0)
        except Timeout:
            raise RuntimeError('Another reliable-upload process is already running') from None
        self.db = self.directory / 'queue.sqlite3'
        self.stop = threading.Event()
        self.token = None
        with self.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY, created TEXT, host TEXT, username TEXT,
                    image TEXT, payload TEXT, status TEXT, attempts INTEGER DEFAULT 0,
                    due REAL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS history (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT, stamp TEXT, message TEXT);
            ''')
            recovered = db.execute("SELECT count(*) FROM jobs WHERE status IN ('pending','sending')").fetchone()[0]
            db.execute("UPDATE jobs SET status='pending', due=0 WHERE status='sending'")
            if recovered:
                self.log(db, f'RESTART: restored {recovered} unsent record(s)')
            if retry_failed:
                count = db.execute("UPDATE jobs SET status='pending', due=0 WHERE status='action' AND host=? AND username=?",
                                   (self.host, self.username)).rowcount
                self.log(db, f'Manual retry: {count} record(s)')
        self.thread = threading.Thread(target=self.work, daemon=True, name='upload-worker')
        self.thread.start()
        atexit.register(self.close)

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.db, timeout=10)
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    @staticmethod
    def log(db, message):
        stamp = datetime.now().strftime('%m-%d %H:%M:%S')
        db.execute('INSERT INTO history(stamp,message) VALUES (?,?)', (stamp, message))
        print(f'[Upload] {stamp} {message}', flush=True)

    def enqueue(self, image, title, text):
        event_id = uuid.uuid4().hex
        path = self.directory / f'{event_id}.jpg'
        ok, encoded = cv2.imencode('.jpg', image)
        if not ok:
            raise RuntimeError('Could not encode event image; record was not queued')
        temporary = path.with_suffix('.tmp')
        with temporary.open('wb') as stream:
            stream.write(encoded.tobytes())
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(path)
        created = datetime.now().astimezone().isoformat()
        payload = json.dumps({'title': title, 'text': text, 'created_date': created, 'published_date': created})
        with self.connect() as db:
            db.execute('INSERT INTO jobs(id,created,host,username,image,payload,status) VALUES (?,?,?,?,?,?,?)',
                       (event_id, created, self.host, self.username, str(path), payload, 'pending'))
            self.log(db, f'SAVED {event_id[:6]}: image + record stored on disk')
        return event_id

    @staticmethod
    def classify(response):
        if 200 <= response.status_code < 300:
            return 'sent'
        if response.status_code in (408, 425, 429) or response.status_code >= 500:
            return 'pending'
        return 'action'

    def deliver(self, row):
        _, _, host, username, image, payload, _, _, _ = row
        if host != self.host or username != self.username:
            return 'action', 'destination/account differs; restore settings and use --retry-failed'
        try:
            if self.token is None:
                response = requests.post(host + '/api-token-auth/',
                                         data={'username': username, 'password': self.password},
                                         timeout=(3, 5), allow_redirects=False)
                state = self.classify(response)
                if state != 'sent':
                    return state, f'authentication HTTP {response.status_code}'
                self.token = response.json().get('token')
                if not self.token:
                    return 'action', 'authentication response has no token'
            with open(image, 'rb') as stream:
                response = requests.post(host + '/api_root/Post/', data=json.loads(payload),
                                         files={'image': stream}, headers={'Authorization': 'Token ' + self.token},
                                         timeout=(3, 5), allow_redirects=False)
            state = self.classify(response)
            if response.status_code in (401, 403):
                self.token = None
            return state, f'HTTP {response.status_code}'
        except (requests.ConnectionError, requests.Timeout):
            return 'pending', 'connection failed; record retained'
        except (OSError, ValueError, requests.RequestException) as exc:
            return 'action', type(exc).__name__ + '; inspect settings / saved image'

    def work(self):
        try:
            while not self.stop.is_set():
                with self.connect() as db:
                    row = db.execute("SELECT * FROM jobs WHERE status='pending' AND due<=? ORDER BY created LIMIT 1",
                                     (time.time(),)).fetchone()
                    if row:
                        db.execute("UPDATE jobs SET status='sending' WHERE id=?", (row[0],))
                if not row:
                    self.stop.wait(.2)
                    continue
                state, detail = self.deliver(row)
                attempts = row[7] + 1
                delay = (1, 2, 4, 8, 16, 30)[min(attempts - 1, 5)]
                with self.connect() as db:
                    db.execute('UPDATE jobs SET status=?, attempts=?, due=? WHERE id=?',
                               (state, attempts, time.time() + delay, row[0]))
                    if state == 'sent':
                        self.log(db, f"{'AUTO RESEND OK' if row[7] else 'UPLOAD OK'} {row[0][:6]}: {detail}")
                    elif state == 'action':
                        self.log(db, f'ACTION NEEDED {row[0][:6]}: {detail}')
                    elif attempts == 1:
                        self.log(db, f'FAILED {row[0][:6]}: {detail}; automatic retry enabled')
        except Exception as exc:
            # Keep unfinished rows recoverable; never report them as successfully sent.
            with self.connect() as db:
                self.log(db, f'WORKER STOPPED: {type(exc).__name__}; restart after checking disk')

    def snapshot(self):
        with self.connect() as db:
            counts = dict(db.execute('SELECT status,count(*) FROM jobs GROUP BY status'))
            history = db.execute('SELECT stamp,message FROM history ORDER BY seq DESC LIMIT 7').fetchall()[::-1]
        return counts, history

    def render(self, frame):
        counts, history = self.snapshot()
        width = 1100
        scale = min(width / frame.shape[1], 580 / frame.shape[0])
        resized = cv2.resize(frame, (round(frame.shape[1] * scale), round(frame.shape[0] * scale)))
        preview = np.zeros((resized.shape[0], width, 3), dtype=np.uint8)
        left = (width - resized.shape[1]) // 2
        preview[:, left:left + resized.shape[1]] = resized
        panel = np.full((250, width, 3), 24, dtype=np.uint8)
        pending = counts.get('pending', 0) + counts.get('sending', 0)
        lines = [f"AUTO UPLOAD | Pending: {pending} | Success: {counts.get('sent', 0)} | Action: {counts.get('action', 0)}",
                 f'Target: {self.host} | History survives restart']
        lines += [f'{stamp}  {message}' for stamp, message in history]
        for i, line in enumerate(lines):
            color = (100, 240, 100) if 'OK' in line else (230, 230, 230)
            cv2.putText(panel, line, (12, 25 + i * 26), cv2.FONT_HERSHEY_SIMPLEX, .51, color, 1, cv2.LINE_AA)
        return np.vstack((preview, panel))

    def close(self):
        self.stop.set()
        self.thread.join(timeout=20)
        if not self.thread.is_alive() and self.lock.is_locked:
            self.lock.release()
