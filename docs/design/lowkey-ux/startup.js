/* Lowkey startup motion for the design prototype. No dependencies, network, storage or logging.
 * API: window.LowkeyStartup.play(root, {force}) -> Promise<{played, reason}>
 * Call it synchronously right after the first render, before the browser paints.
 * It never hides content permanently, never blocks input and only animates transform,
 * opacity and clip-path, so layout stays stable. The logo paths are faded, never redrawn. */
(function () {
  'use strict';
  var EASE = 'cubic-bezier(.2,.7,.1,1)'; // decelerating, close to Android's emphasized decelerate
  var played = false;
  var current = null; // { animations, ring, cleanup, resolve }

  function visible(el) {
    return !!el && el.isConnected && el.getClientRects().length > 0;
  }

  function reducedMotion() {
    return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }

  function finishAll() {
    if (!current) return;
    current.animations.forEach(function (a) {
      try { a.finish(); } catch (e) { try { a.cancel(); } catch (e2) { /* already gone */ } }
    });
  }

  function settle(reason) {
    if (!current) return;
    var run = current;
    current = null;
    run.cleanup();
    // fill: backwards leaves no effect after the end; cancel releases the animations.
    run.animations.forEach(function (a) { try { a.cancel(); } catch (e) { /* ignore */ } });
    run.resolve({ played: true, reason: reason });
  }

  function play(root, options) {
    options = options || {};
    var force = options.force === true;
    if (current) { finishAll(); settle('replaced'); }
    if (played && !force) return Promise.resolve({ played: false, reason: 'already-played' });
    played = true; // once per document, also when skipped
    if (!root || !visible(root)) return Promise.resolve({ played: false, reason: 'no-root' });
    if (options.reducedMotion === true || reducedMotion()) return Promise.resolve({ played: false, reason: 'reduced-motion' });
    if (typeof root.animate !== 'function') return Promise.resolve({ played: false, reason: 'unsupported' });
    if (document.visibilityState === 'hidden') return Promise.resolve({ played: false, reason: 'hidden-tab' });

    var animations = [];
    function add(el, frames, delay, duration) {
      if (!visible(el)) return;
      try {
        animations.push(el.animate(frames, { delay: delay, duration: duration, easing: EASE, fill: 'backwards' }));
      } catch (e) { /* an unsupported property only skips that one element */ }
    }

    var brand = root.querySelector('.brand');
    var ring = null;
    if (visible(brand)) {
      var mark = brand.querySelector('svg');
      if (visible(mark)) {
        // The mark settles as one unit; its four original paths only fade in, in source order.
        add(mark, [{ opacity: 0, transform: 'translateY(4px) scale(.94)' }, { opacity: 1, transform: 'none' }], 0, 320);
        Array.prototype.slice.call(mark.querySelectorAll('path'), 0, 4).forEach(function (path, i) {
          add(path, [{ opacity: 0 }, { opacity: 1 }], i * 40, 240);
        });
        // One soft "listening" ring, a nod to on-device voice. Plays once, then is removed.
        brand.setAttribute('data-lk-ring', '');
        var b = brand.getBoundingClientRect(), m = mark.getBoundingClientRect();
        ring = document.createElement('span');
        ring.className = 'lk-startup-ring';
        ring.setAttribute('aria-hidden', 'true');
        ring.style.left = (m.left - b.left + m.width / 2) + 'px';
        ring.style.top = (m.top - b.top + m.height / 2) + 'px';
        brand.appendChild(ring);
        add(ring, [
          { opacity: 0, transform: 'translate(-50%,-50%) scale(.55)' },
          { opacity: .3, transform: 'translate(-50%,-50%) scale(.85)', offset: .25 },
          { opacity: 0, transform: 'translate(-50%,-50%) scale(1.45)' }
        ], 80, 440);
      }
      add(brand.querySelector('.wordmark'), [{ opacity: 0, transform: 'translateX(-6px)' }, { opacity: 1, transform: 'none' }], 90, 300);
      add(brand.querySelector('.product'), [
        { opacity: 0, clipPath: 'inset(0 100% 0 0)' }, { opacity: 1, clipPath: 'inset(0 0 0 0)' }
      ], 150, 300);
    }

    // The work surface rises like a keyboard sheet; the first lines of the view follow.
    var surface = root.querySelector('.surface');
    var brandShown = visible(brand);
    add(surface, [{ opacity: 0, transform: 'translateY(14px)' }, { opacity: 1, transform: 'none' }], brandShown ? 60 : 0, 420);
    var view = surface && (surface.querySelector('main') || surface.firstElementChild);
    if (view) {
      // Every line of the view follows in reading order, so no button appears before its heading.
      // The stagger stops after five steps; later lines share the last step.
      Array.prototype.slice.call(view.children, 0, 16).forEach(function (child, i) {
        add(child, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], 160 + Math.min(i, 4) * 40, 240);
      });
    }
    // Longest track: 160 + 4 * 40 + 240 = 560 ms. Everything else ends earlier.

    if (!animations.length) {
      if (ring) ring.remove();
      if (brand) brand.removeAttribute('data-lk-ring');
      return Promise.resolve({ played: false, reason: 'nothing-to-animate' });
    }

    return new Promise(function (resolve) {
      var mq = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
      // Any input completes the motion at once: no forced wait, nothing to click through.
      function onInput() { finishAll(); }
      function onVisibility() { if (document.visibilityState === 'hidden') finishAll(); }
      function onMotionPref() { if (mq && mq.matches) finishAll(); }
      var guard = setTimeout(function () { finishAll(); settle('timeout'); }, 900);
      root.addEventListener('pointerdown', onInput, true);
      root.addEventListener('keydown', onInput, true);
      root.addEventListener('wheel', onInput, { capture: true, passive: true });
      document.addEventListener('visibilitychange', onVisibility);
      if (mq && mq.addEventListener) mq.addEventListener('change', onMotionPref);
      if (options.signal && options.signal.addEventListener) options.signal.addEventListener('abort', onInput);
      root.setAttribute('data-lk-startup', 'playing');

      current = {
        animations: animations,
        resolve: resolve,
        cleanup: function () {
          clearTimeout(guard);
          root.removeEventListener('pointerdown', onInput, true);
          root.removeEventListener('keydown', onInput, true);
          root.removeEventListener('wheel', onInput, { capture: true });
          document.removeEventListener('visibilitychange', onVisibility);
          if (mq && mq.removeEventListener) mq.removeEventListener('change', onMotionPref);
          if (options.signal && options.signal.removeEventListener) options.signal.removeEventListener('abort', onInput);
          if (ring) ring.remove();
          if (brand) brand.removeAttribute('data-lk-ring');
          root.removeAttribute('data-lk-startup');
        }
      };
      var run = current;
      // Removed or cancelled targets reject or never matter; allSettled keeps the promise honest.
      Promise.all(animations.map(function (a) {
        return a.finished.then(function () {}, function () {});
      })).then(function () { if (current === run) settle('done'); });
    });
  }

  window.LowkeyStartup = {
    play: play,
    /* Optional: jump to the end of a running startup (for example before a scripted test). */
    finish: function () { finishAll(); }
  };
})();
