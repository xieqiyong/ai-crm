import threading
import time


class SnowflakeIdGenerator:
    def __init__(self, worker_id: int = 21):
        self.worker_id = worker_id & 0x3FF
        self.sequence = 0
        self.last_timestamp = -1
        self.lock = threading.Lock()
        self.epoch = 1704067200000

    def next_id(self) -> int:
        with self.lock:
            timestamp = self._now_ms()
            if timestamp < self.last_timestamp:
                timestamp = self.last_timestamp
            if timestamp == self.last_timestamp:
                self.sequence = (self.sequence + 1) & 0xFFF
                if self.sequence == 0:
                    timestamp = self._wait_next_ms(timestamp)
            else:
                self.sequence = 0
            self.last_timestamp = timestamp
            return ((timestamp - self.epoch) << 22) | (self.worker_id << 12) | self.sequence

    def _wait_next_ms(self, timestamp: int) -> int:
        current = self._now_ms()
        while current <= timestamp:
            current = self._now_ms()
        return current

    def _now_ms(self) -> int:
        return int(time.time() * 1000)


id_generator = SnowflakeIdGenerator()
