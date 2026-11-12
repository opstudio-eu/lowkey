# Typing latency implementation and validation

The requested outcome is less delay between typing and visible text. The four
reviews identified repeated synchronous editor reads, a synchronous spelling
search on a boundary cache miss, and release-order typing with overlapping thumbs.

Use the existing Android/Compose implementation and dependencies. Keep ordinary
tap release, accents, cancellation, sensitive-field behavior, and correction undo.
Do not infer a phone-level speedup from emulator or synthetic measurements.

## Work

- [x] Capture baseline instrumented input and regression-test results.
- [x] Read a bounded editor snapshot once per suggestion refresh; derive context
  locally. Deduplicate equal requests and invalidate obsolete results on edits,
  cursor changes, configuration changes and input-session changes.
- [x] Keep spelling search off the boundary event. Use a matching cached result;
  on a miss preserve literal input rather than delay or retroactively correct it.
  Compute the correction before optional suggestions; cancel obsolete CPU work.
- [x] Add a shared pending-character queue so overlapping taps commit in press
  order, while an established long press remains owned by its popup.
- [x] Remember keyboard layouts across suggestion-only updates.
- [x] Run regression tests for context limits, duplicate requests, stale results,
  cache misses, cancellation, rollover, long press and pointer cancellation.
- [x] Run the full unit suite and real emulator input probes. Compare baseline
  and changed code using the same test connection and injected delays; record
  call counts and measured handler durations separately from visible latency.

## Validation limits

Existing safety checks must not be replaced by a time-window assumption that
could mistake an external cursor move for an editor echo. Cache misses may skip
a spelling correction during fast typing; they must never delay a later edit or
apply an old correction at a new cursor. No broad release-build/R8 changes are
included without evidence that they are necessary for this fault.

Rollover follows the reviewed proposal: a second typing-key press accepts an
earlier pending tap. It cannot subsequently be cancelled or turned into an
accent. A hold that has already opened its popup remains independent; to combine
an accent with another finger, open the popup first. This is the unavoidable
tradeoff of committing the first tap early instead of waiting for its release.
