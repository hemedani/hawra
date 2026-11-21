# Running the app against the backend on this Mac

The debug APK talks to a Lesan server over plain HTTP on port 1407. Five things have to line up,
and four of them fail **silently** — the phone looks offline rather than misconfigured. This is the
order that works.

## 0. Sign in — this is the connectivity test

The app opens on a **gate**: an email, a password, and either *Sign in* or *Register*. It does not
run until one of them succeeds, because history recorded before sign-in belongs to nobody. That
makes the gate the fastest way to tell whether the phone can reach the Mac at all:

- **Register succeeds** → the whole path works (DNS/route, firewall, cleartext, `/lesan`, Mongo).
  Everything else in the app can only fail for its own reasons.
- **"Cannot reach the server"** → the phone never got an answer. Work through steps 1–4.
- **"Email or password is incorrect"** → the phone is talking to the server fine. You are on the
  wrong account, not the wrong network. (The message is deliberately the same for an unknown
  address and a wrong password.)

Register a throwaway account for testing (`probe@example.com` / `password123`); it costs nothing and
keeps your real history separate. Two test accounts already exist on this machine's Mongo —
`probe1@example.com` and `syd@example.com`, both `password123` — so signing in with one of them is
the quickest possible check that the phone can reach the Mac. The form will not let you submit a
malformed address or, when registering, a password under 8 characters; those two checks run on the
device, so they say nothing about the connection.

## 1. Backend up, on the LAN

```sh
cd back
deno task start
```

The banner prints both the localhost URL and the address the phone needs:

```
🚀 Lesan server is running successfully!
📡 API Endpoint: http://localhost:1407/lesan
📶 Reachable from your phone (same Wi-Fi):
   http://192.168.1.111:1407/lesan   (en0)
```

Only **private** addresses are listed (10/8, 172.16/12, 192.168/16). A VPN's `utun` address is
filtered out on purpose — printing it next to the real one turns the banner into a guess.

MongoDB must be running (`brew services start mongodb-community`, or just `mongod`). If the server
exits at boot with a connection error, that is why.

## 2. macOS firewall

Check it:

```sh
/usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate
```

If it is enabled and `deno` is not in the list, incoming connections are blocked and the phone will
time out with no useful error anywhere:

```sh
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --add /Users/syd/.deno/bin/deno
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --unblockapp /Users/syd/.deno/bin/deno
```

(Or turn the firewall off for the duration of the test.) Verify from the Mac first — this proves the
bind, not the firewall:

```sh
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://192.168.1.111:1407/lesan \
  -H 'content-type: application/json' \
  -d '{"model":"audioFile","act":"getAudioMirrorStatus","details":{"set":{}}}'
# 200
```

## 3. Point the app at that address

`mobile/android/local.properties` (untracked, per machine):

```properties
hawraa.baseUrl=http://192.168.1.111:1407
```

**No `/lesan` suffix.** The value is an *origin*; `LesanClient` appends `/lesan`. Getting this wrong
is the classic failure: the server answers
`501 {"message":"Invalid request: Expected POST to /lesan, got POST to /"}` and the app swallows it,
because every network call is wrapped so a sync failure cannot reach the UI.

`-Phawraa.baseUrl=http://…` overrides it for a one-off build.

## 4. Build and install

```sh
cd mobile/android
JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No cable? *Settings → Developer options → Wireless debugging* on the phone, then
`adb pair <ip>:<port>` and `adb connect <ip>:<port>`.

Cleartext HTTP is permitted in **debug builds only** (`app/src/debug/AndroidManifest.xml`). A release
build will refuse this server, which is the correct behaviour.

## 5. Audio the server can serve itself (optional)

The catalogue points at upstream CDNs; the phone can download from them directly. Mirroring puts
copies on the Mac, which is faster over Wi-Fi, works when upstream is unreachable, and lets the
manifest declare an exact byte size:

```sh
cd back
deno task audio-mirror --edition=parhizgar-murattal-ayah-48 --scope=surah:1
deno task audio-mirror --edition=parhizgar-murattal-ayah-48 --scope=range:112:1-114:6
```

Scopes: `surah:N`, `juz:N`, `ayah:S:A`, `range:S:A-S:A`. Re-running skips what is already stored;
`--dry-run` and `--limit=N` are there for sanity checks. Already mirrored on this machine: al-Fatiha
and al-Ikhlas / al-Falaq / an-Nas (22 files).

What the app gets from this: `getDownloadManifest` adds `bytes` and `mirrorPath` per mirrored file,
and `DownloadCodec` resolves `mirrorPath` against `SERVER_URL` — so the download goes to the Mac, and
the finished file is checked against the declared size.

## Troubleshooting, in the order things actually go wrong

| Symptom | Cause |
|---|---|
| Gate says "Cannot reach the server" | `hawraa.baseUrl` wrong, or firewall (step 2), or cleartext blocked (release build). |
| Gate says "email or password is incorrect" | The network is fine — the account is not. Register instead. |
| App shows nothing to download, no error | Signed in? The catalogue needs a token, and a signed-out device is not given one. |
| Every request fails instantly | Cleartext blocked — you are running a release build. |
| Requests time out | Firewall (step 2), or the phone is on a different network / guest Wi-Fi. |
| Downloads work but playback is silent | The file was fetched from upstream and upstream is blocked; mirror the scope first. |
| Server restarts while mirroring | `--watch-exclude=uploads` missing from `deno task start`. |

## Checking what the server holds

```sh
curl -s -X POST http://192.168.1.111:1407/lesan \
  -H 'content-type: application/json' \
  -d '{"model":"audioFile","act":"getAudioMirrorStatus","details":{"set":{}}}'
```

No token required — it reports on the server, not on a user, and it is the one call worth being able
to make with `curl` while a phone misbehaves.
