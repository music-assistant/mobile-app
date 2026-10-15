# Play request feedback and coalescing (mobile-app#769)

Written to ASD-STE100 rules.

## Problem

A tap on "Play" gives no feedback until the server starts playback. This can take 3 to 5 seconds, and more than 30 seconds for a large artist. Users tap again. Each tap starts one more full play action on the server.

## Server facts

These facts come from `music-assistant/server`, tag `2.9.0` and branch `dev`. The client minimum is server 2.9, so all of them apply.

- `player_queues/play_media` answers only after the server resolves the media, starts `play_index`, and waits for the player update. The answer of the RPC is the "playback started" signal.
- Each play action holds a per-queue lock (`handle_play_action`). A second request waits for the first one. The server never cancels a running action. On `dev`, a request that waits too long fails with `ResourceBusyError`.
- While a play action runs, the server sets `extra_attributes["play_action_in_progress"]` on the queue to `true`. It sends a queue update when the flag goes on and when it goes off.
- The flag is for each queue, not for each request. It is also `true` for play actions from other clients: CarPlay, Android Auto, the web UI, and other phones.

## Decisions

- One app-scoped component sends every `play_media` request. A screen does not send it from `viewModelScope`.
- There is no timeout. `RpcEngine.failAll` ends every open request when the connection stops.
- The client coalesces requests for each queue. The rules are in [Coalescing rules](#coalescing-rules).
- The busy indicator is a ring around the main play button. The button stays active. The client never blocks input.
- The ring shows when the server flag is `true` or when the client has a request for that queue in progress or waiting.
- There is no spinner on list rows. There is no optimistic "now playing" item.

## Coalescing rules

A request is the full value of `(queueId, media, option, radio, startItem)`. Two requests are equal if all five fields are equal.

The client keeps one request in progress and an ordered list of waiting requests for each queue. When a new request comes:

1. If it is a replacing request (`PLAY` or `REPLACE`) and the last waiting request is also a replacing request, remove the last waiting request.
2. Find the predecessor: the last waiting request, or the request in progress if no request waits.
3. If the new request is equal to the predecessor, drop it. It is a double tap.
4. In all other cases, add the request to the end of the list.

Rule 1 comes before rule 3 on purpose. If "Play A" is in progress and "Play B" waits, a new tap on "Play A" removes "Play B". The newest tap is "Play A", and it is already in progress.

`ADD` and `NEXT` requests are never replaced and never move. This keeps their order relative to the play requests. For example, "Play B, then Add C" still adds C after B starts.

Requests for different queues do not wait for each other.

The rules are a pure function. Put them in a function that is separate from the coroutine code, so that the tests do not need a coroutine.

## Work packages

Do the work packages in sequence. Each package is one pull request.

### PR 1: One app-scoped play dispatcher (refactor, no behavior change)

1. Add `PlayRequest` (data class) and `PlayDispatcher` in `data/play/`.
   - `PlayDispatcher(apiClient: ServiceClient, scope: CoroutineScope)`.
   - `fun play(request: PlayRequest)` returns immediately. It sends `Request.Library.play` in the dispatcher scope.
   - It logs each request and each failure with the `PlayDispatch` tag.
2. Register it in `di/SharedModule.kt` as a `single`. Give it `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, the same as `QueueOverlayRepository`.
3. Change the 7 screen call sites to use `playDispatcher.play(...)`:
   - `HomeScreenViewModel.onPlayClick`
   - `BrowseViewModel.onPlayClick`
   - `LibraryListViewModel.onPlayClick`
   - `SearchViewModel.onPlayClick`
   - `ActionsViewModel.onPlayClick`
   - `ItemDetailsViewModel.onPlayClick`
   - `ItemDetailsViewModel.onChapterClick`
4. Change `executeLocalPlayerDispatch` (`data/LocalPlayerDispatch.kt`). It still sends and waits for the detach request first. Then it gives the play request to the dispatcher. `onRpcFailure` stays for the detach request only.
5. Update the 2 callers of `executeLocalPlayerDispatch`: `AutoLibrary` (Android Auto) and `KmpHelper.playOnLocalPlayer` (CarPlay).
6. Update `LocalPlayerDispatchTest`. The test still checks that the detach request goes before the play request.

Result: a play request continues after the user leaves the screen. All play requests go through one place.

### PR 2: Duplicate filter and coalescing

1. Add the pure function for the [Coalescing rules](#coalescing-rules).
2. In `PlayDispatcher`, keep the state for each queue: the request in progress and the waiting list. Protect the state with one `Mutex`.
3. Start one worker coroutine for a queue when the first request comes. The worker sends the requests one at a time. It stops when the list is empty.
4. Expose `busyQueueIds: StateFlow<Set<String>>`. A queue is busy if it has a request in progress or waiting.
5. Tests:
   - The pure function: each rule, and the "Play B, then Add C" order.
   - The dispatcher, with `StubServiceClient`:
     - 10 different taps while one request is in progress cause 2 requests.
     - A failed request does not stop the next request.
     - A connection failure (`failAll`) clears the busy state.

### PR 3: Busy ring on the player

1. Add `@SerialName("extra_attributes")` to `ServerQueue` with a small `@Serializable` class that has only `play_action_in_progress: Boolean = false`. Do not decode the full map.
2. Add `playActionInProgress: Boolean = false` to `QueueInfo`. Set it in `QueueFactory.create`.
3. Add `playRequestPending: Boolean = false` to `PlayerData`. Copy it in `PlayerData.updateFrom`.
4. In `MainDataSource`, add `playDispatcher.busyQueueIds` to the `combine` that calls `buildPlayerDataList`. The `combine` has 5 flows now, so use the overload with a list of flows. Set `playRequestPending` to `queueInfo.playActionInProgress || queueOrPlayerId in busyQueueIds`. Use the same method as the favorite overlay.
5. In `PlayerControls`, draw a `CircularProgressIndicator` ring around the main button when `playRequestPending` is `true`. The button stays active, with its usual icon and action. Do not change the local player `pendingPlay` spinner.
6. Do the change for the compact player and the full player. Both use `PlayerControls`, so one change is sufficient. Check this on a device.
7. Tests:
   - `ServerQueue` decodes with and without `extra_attributes`.
   - `QueueFactory` maps the flag.
   - `buildPlayerDataList` sets `playRequestPending` from each of the two sources.

## Risks

- **A stale snapshot can keep the ring on.** The flag does not change `elapsed_time_last_updated`. `mergeFullQueueSnapshot` can keep a snapshot that was made while the flag was `true`. The client gets a full snapshot only when a session starts, so the problem needs a play action that runs at that time. The next queue update corrects it. Decision: keep the merge as it is.
- **The ring also shows for other clients.** A play action from the car or the web UI also shows the ring. This is correct, because the player is busy. Do not hide it.
- **The ring also shows for other play actions.** The server sets the flag for each action that holds the play lock, for example `play_index` from the queue screen. This is correct for the same reason.
- **Coalescing does not stop other clients.** A tap storm from the web UI or from another phone still gets to the server. The server needs its own fix. Write a note about this in the issue.

## Out of scope

- A spinner on list rows.
- An optimistic "now playing" item before the server confirms.
- Server changes.
- `play_index` from the queue screen. It does not use `play_media`. It gets the ring through the server flag.
