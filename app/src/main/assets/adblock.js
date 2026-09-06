/**
 * NGYT ad-blocker for m.youtube.com.
 *
 * Injected by MainActivity via evaluateJavascript on every page
 * start / finish / progress change. Guards against double-install
 * so repeated injection is safe (YouTube is a single-page app).
 *
 * Strategy (client-side only):
 *  1. CSS: hide banner / overlay / promoted / masthead ad containers.
 *  2. JS: detect video ads (.ad-showing), mute + seek to end, click skip.
 *  3. MutationObserver + interval: re-apply as YouTube rewrites the DOM.
 */
(function () {
  'use strict';

  // Avoid installing twice when MainActivity re-injects on SPA navigation.
  if (window.__ngytAdblockInstalled) {
    return;
  }
  window.__ngytAdblockInstalled = true;

  /** CSS selectors for banner / overlay / promoted ad containers. */
  var HIDE_SELECTORS = [
    '.ytp-ad-module',
    '.ytp-ad-player-overlay',
    '.ytp-ad-image-overlay',
    'ytd-display-ad-renderer',
    'ytd-promoted-sparkles-web-renderer',
    'ytd-promoted-video-renderer',
    'ytd-action-companion-ad-renderer',
    'ytd-companion-slot-renderer',
    '#masthead-ad',
    '.ytd-masthead-ad-v3-renderer',
    'ytd-in-feed-ad-layout-renderer',
    'ytd-ad-slot-renderer',
    '.ytp-ad-text-overlay',
    '#player-ads',
    '#mealbar-promo-renderer'
  ];

  /** Inject a <style> tag hiding known ad containers. */
  function injectCss() {
    if (document.getElementById('__ngyt-adblock-css')) {
      return;
    }
    var css = HIDE_SELECTORS.join(', ') + ' { display: none !important; }';
    var style = document.createElement('style');
    style.id = '__ngyt-adblock-css';
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }

  /** Click any visible YouTube "Skip ad" button. */
  function clickSkipButtons() {
    var btns = document.querySelectorAll(
      '.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern'
    );
    for (var i = 0; i < btns.length; i++) {
      try {
        /** @type {HTMLElement} */
        var b = btns[i];
        if (b && b.offsetParent !== null) {
          b.click();
        }
      } catch (e) { /* ignore */ }
    }
  }

  /** Fast-forward / mute video ads so pre-rolls end instantly. */
  function skipVideoAds() {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      try {
        var v = videos[i];
        var player = document.getElementById('movie_player');
        var isAd = (player && player.classList.contains('ad-showing')) ||
          (player && player.classList.contains('ad-interrupting')) ||
          document.querySelector('.ad-showing, .ad-interrupting') !== null;
        if (isAd) {
          // Mute so nothing is heard, then jump to the end of the ad.
          v.muted = true;
          if (isFinite(v.duration) && v.duration > 0) {
            v.currentTime = v.duration;
          }
          // Fire 'ended' fallback for players waiting on the event.
          if (v.paused) {
            var p = v.play();
            if (p && p.catch) { p.catch(function () {}); }
          }
        }
      } catch (e) { /* ignore */ }
    }
  }

  /** Remove overlay / banner ad nodes that slip past CSS. */
  function removeAdNodes() {
    var nodes = document.querySelectorAll(HIDE_SELECTORS.join(', '));
    for (var i = 0; i < nodes.length; i++) {
      try {
        var n = nodes[i];
        if (n && n.parentNode) {
          n.parentNode.removeChild(n);
        }
      } catch (e) { /* ignore */ }
    }
  }

  /** One sweep: CSS + skip + remove. Cheap enough to run often. */
  function sweep() {
    injectCss();
    clickSkipButtons();
    skipVideoAds();
    removeAdNodes();
  }

  // Initial sweep.
  sweep();

  // Re-sweep on DOM changes (YouTube rewrites nodes on navigation).
  try {
    var observer = new MutationObserver(function () {
      sweep();
    });
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'src']
    });
  } catch (e) { /* MutationObserver unavailable: interval below covers it */ }

  // Fallback timer for video-ad fast-forwarding (500 ms is responsive yet cheap).
  setInterval(sweep, 500);
})();
