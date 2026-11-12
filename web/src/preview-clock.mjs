// Author: dishanhai
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

export class PreviewClock {
  timeMs = 0;
  playing = false;
  speed = 1;
  #previous;

  constructor(durationMs, fps) {
    this.durationMs = durationMs;
    this.frameMs = 1000 / fps;
  }

  pause() {
    this.playing = false;
    this.suspend();
  }

  resume() {
    this.playing = true;
    this.suspend();
  }

  suspend() { this.#previous = undefined; }

  seek(timeMs) {
    this.suspend();
    this.timeMs = clamp(timeMs, 0, this.durationMs);
    return this.timeMs;
  }

  stepFrame() {
    this.pause();
    return this.seek(this.timeMs + this.frameMs);
  }

  resetTime() { return this.seek(0); }

  setPlaybackSpeed(speed) {
    if (![0.25, 0.5, 1, 2].includes(speed)) throw new RangeError('无效的播放速度');
    this.speed = speed;
    this.suspend();
  }

  tick(realTimeMs) {
    if (!this.playing) return this.timeMs;
    if (this.#previous !== undefined) {
      this.timeMs += clamp(realTimeMs - this.#previous, 0, 100) * this.speed;
      if (this.timeMs > this.durationMs) this.timeMs %= this.durationMs;
    }
    this.#previous = realTimeMs;
    return this.timeMs;
  }
}

function sample(points, timeMs) {
  const next = points.findIndex(point => timeMs <= point.timeMs);
  if (next <= 0) return next === 0 ? points[0] : points.at(-1);
  const a = points[next - 1];
  const b = points[next];
  const t = (timeMs - a.timeMs) / (b.timeMs - a.timeMs);
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
}

export function replayFrame(scene, timeMs, livePointer, liveAction) {
  const { surface, movingObject } = scene;
  const point = livePointer ?? sample(scene.pointer, timeMs);
  const moved = sample(movingObject.points, timeMs);
  const latestBoundary = scene.inputEvents.findLast(event => event.timeMs <= timeMs
    && (event.type === 'pointerenter' || event.type === 'pointerleave'));
  const down = scene.inputEvents.findLast(event => event.timeMs <= timeMs && event.type === 'pointerdown');
  const up = scene.inputEvents.findLast(event => event.timeMs <= timeMs && event.type === 'pointerup');
  const start = down && Number.isFinite(down.x) ? down : down ? sample(scene.pointer, down.timeMs) : point;
  const end = up && Number.isFinite(up.x) ? up : up ? sample(scene.pointer, up.timeMs) : point;
  return {
    timeMs,
    pointer: [point.x, point.y],
    action: livePointer ? { ...liveAction, manual: true } : {
      pressed: !!down && (!up || up.timeMs < down.timeMs),
      origin: [start.x, start.y],
      end: [end.x, end.y],
      sincePress: down ? (timeMs - down.timeMs) / 1000 : 100,
      sinceRelease: up ? (timeMs - up.timeMs) / 1000 : 100,
    },
    light: {
      x: clamp((point.x * scene.width - surface.x) / surface.width * 2 - 1, -1, 1),
      y: clamp((point.y * scene.height - surface.y) / surface.height * 2 - 1, -1, 1),
      strength: livePointer || latestBoundary?.type === 'pointerenter' ? 0.75 : 0,
    },
    movingRect: { x: Math.round(moved.x), y: Math.round(moved.y), width: movingObject.width, height: movingObject.height },
  };
}
