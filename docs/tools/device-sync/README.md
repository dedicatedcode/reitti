# reitti-device-sync

A small bash tool that pulls GPX/FIT files from USB-attached GPS devices and
pushes them into your [reitti](https://github.com/dedicatedcode/reitti)
instance. It was built for the **Garmin Edge 830** (FIT via MTP) and the
**Columbus P-1 Mark II** (GPX via USB mass storage) but is generic: every
device is just a config section with an upload URL and an API token.

When you plug a device in, a systemd user service starts automatically, finds
files that have **not been uploaded before**, and pushes them to reitti. Files
that are still being written are deferred; failed uploads are retried on the
next run.

## How it prevents double uploads

`reitti-device-sync` keeps a local **ledger** in
`~/.local/state/reitti-device-sync/<device>.tsv`. A file is only written to
the ledger **after** the server confirmed a successful import (`HTTP 200` +
`"success": true`). On every run the tool checks each candidate file against
the ledger twice:

1. **Fast path** — same path + size + mtime already uploaded → skip.
2. **Content path** — `sha256` of the file already uploaded → skip. This also
   catches files that were renamed or re-downloaded.

As a final safety net, reitti itself ignores duplicate location points
(`ON CONFLICT (user_id, device_id, timestamp) DO NOTHING`), so even a
redundant upload can never produce duplicate points on the map.

## Requirements

- Linux with bash 4+ (uses associative arrays)
- `curl`, `jq`, `flock` (util-linux), `sha256sum`/`stat`/`find` (coreutils)
  - Debian/Ubuntu: `sudo apt install curl jq util-linux coreutils`
  - Fedora: `sudo dnf install curl jq util-linux coreutils`
- For the Garmin Edge 830 (MTP): a desktop environment with `gvfs`
  (`gvfs-backends` on Debian/Ubuntu) — this is the default on GNOME/KDE.
  See *Headless / alternative MTP mounting* below otherwise.
- A reitti instance you can reach over HTTP(S).

## 1. Set up reitti

1. **Create the devices**: reitti → *Settings → Devices* — create one device
   per tracker (e.g. "Garmin Edge 830", "Columbus P-1 Mark II").
2. **Create one token per device**: reitti → *Settings → API Tokens* — create
   two tokens and attach each token to its device. Device-bound tokens keep
   the ingested data attributed to the right tracker, so you do not need the
   optional `device_id` config key.

## 2. Install the script

```bash
mkdir -p ~/.local/bin
cp docs/tools/device-sync/reitti-device-sync ~/.local/bin/
chmod +x ~/.local/bin/reitti-device-sync
```

## 3. Create the config

```bash
mkdir -p ~/.config/reitti-device-sync
cp docs/tools/device-sync/config.example.conf ~/.config/reitti-device-sync/config.conf
```

Edit `~/.config/reitti-device-sync/config.conf`:

- Set `url` to your reitti base URL. FIT devices use `/api/v2/fit/import`,
  GPX devices use `/api/v1/gpx/import`.
- Set `token` per device (one token per section).
- Replace `1000` in the `source` globs with your own uid (`id -u`).

### Where the files live

| Device | Connection | Typical source |
|---|---|---|
| Garmin Edge 830/840 | USB, **MTP** | `/run/user/<uid>/gvfs/mtp:host=*/Garmin/[Aa]ctivit*` **and** `/run/user/<uid>/gvfs/mtp:host=*/*/Garmin/[Aa]ctivit*` |
| Columbus P-1 Mark II | USB mass storage | `/run/media/<uid>/<volume>` |

Notes:

- `*` in a `source` glob does **not** match `/`, so if the activities are
  nested deeper (the Edge 840 stores them in `Internal Storage/Garmin/Activities`)
  you need a second `source` line for that depth — both lines ship in the
  example config.
- **Never copy the backslashes** your shell shows when tab-completing gvfs
  paths (`mtp\:host\=...`, `Internal\ Storage/...`). Those are display
  escapes only — the real directory is `mtp:host=.../Internal Storage/...`.
- Paths with spaces are supported (one pattern per `source` line).

For the Edge devices the preset only scans the `Garmin/Activities` folder
(ride activities). Files under `Garmin/Logs` etc. are intentionally not
uploaded; change the `source` globs if you want them.

For the P-1 Mark II the whole mounted volume is scanned recursively for
`*.gpx`. Once you know the folder the device writes to, you can narrow
`source` and `include_glob`.

## 4. Test

Plug the device in (or skip this step and point `source` at any folder with a
few `.gpx`/`.fit` files) and run:

```bash
reitti-device-sync --dry-run --verbose
```

You should see the files that *would* be uploaded. Then do a real run:

```bash
reitti-device-sync --verbose
```

Run it a second time — it should report `skipped(already-uploaded)=N` and
upload nothing.

## 5. Auto-sync on connection (systemd user units)

```bash
mkdir -p ~/.config/systemd/user
cp docs/tools/device-sync/systemd/reitti-device-sync.service \
   docs/tools/device-sync/systemd/reitti-device-sync-garmin.path \
   docs/tools/device-sync/systemd/reitti-device-sync-p1.path \
   ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now reitti-device-sync-garmin.path reitti-device-sync-p1.path
```

The `.path` units watch the mount points (`gvfs` for MTP, `/run/media` for
USB mass storage). When a device appears they start
`reitti-device-sync.service`, which scans **all** configured devices and
uploads everything new; devices that are not connected are skipped quietly.

> **Note:** the units use `%U` (your uid) in the watch paths. On current
> systemd user units this resolves correctly; if the trigger does not fire on
> your system, replace `%U` in the two `.path` files with your numeric uid
> (`id -u`) and run `systemctl --user daemon-reload` again.

Check the logs at any time:

```bash
journalctl --user -u reitti-device-sync.service -f
```

## Behaviour details

- **Nothing is ever deleted from the device.** The tool is read-only.
- **Files still being written** (the P-1 logs into the active file while it is
  connected) are skipped while they were modified within
  `skip_modified_recent_min` and picked up on a later run.
- **Slow device warm-up**: MTP devices often need several seconds after
  mounting before they serve directory listings. When a source matches but no
  files are found, the tool re-scans twice (`settle_seconds` apart, default 5)
  before reporting "nothing to do".
- **Failed uploads** (device pulled mid-transfer, reitti unreachable) are not
  recorded in the ledger and therefore retried automatically on the next run.
- **Growing files**: when a file grows after being uploaded, its sha256
  changes and it is uploaded once more — only the new points are added.
- **`max_age_days`** keeps old files from being re-sent after a fresh ledger
  reset.
- **Concurrency**: a lock file prevents two runs (udev trigger + manual run)
  from racing; the second run simply exits.

### The ledger

`~/.local/state/reitti-device-sync/<device>.tsv`, one line per uploaded file:

```
sha256  size  mtime  path  uploaded_at  points
```

To force a re-upload of everything for one device, delete its ledger file
(`rm ~/.local/state/reitti-device-sync/garmin-edge830.tsv`) — harmless, since
reitti ignores duplicate points server-side.

## Troubleshooting

**Garmin does not show up under `/run/user/<uid>/gvfs/`.**
Install gvfs (`gvfs-backends` on Debian/Ubuntu), unlock the screen of a GNOME
session, and replug. Test with `gio mount --list | grep -i mtp`. Some Edge
units also require you to confirm USB mass storage mode on the device.

**Garmin mount is there but appears empty / "Datei oder Verzeichnis nicht
gefunden".**
The MTP session died — Edge units drop it when the screen times out or the
USB mode changes. The mount point lingers in `/run/user/<uid>/gvfs/` but
reads fail or return nothing. Replug the device (or toggle the USB mode on
the device) and check `gio mount --list | grep -i mtp` again.

**Headless / alternative MTP mounting.**
Without a desktop session, mount the device yourself and point `source` at
it, e.g. with [go-mtpfs](https://github.com/hanwen/go-mtpfs):

```bash
go-mtpfs /mnt/edge830 &   # add to a udev rule or systemd unit if you like
```

```ini
[garmin-edge830]
source = /mnt/edge830/GARMIN/Activity
```

A udev-rule alternative (root-owned trigger) also works for mass-storage
devices such as the P-1:

```
# /etc/udev/rules.d/99-reitti-device-sync.rules
ACTION=="add", SUBSYSTEM=="block", ENV{ID_FS_USAGE}=="filesystem", \
  RUN+="/usr/bin/systemd-run --uid=1000 --machine=1000@.host /home/1000/.local/bin/reitti-device-sync"
```

but the shipped user-level `.path` units are the recommended, simpler path on
desktop systems.

**`systemctl --user enable --now ...path` fails with "Job failed".**
Check `journalctl --user -xe`. The most common cause: the `.path` unit
triggers a service named like itself by default
(`reitti-device-sync-garmin.path` → `reitti-device-sync-garmin.service`).
The shipped units set `Unit=reitti-device-sync.service` in the `[Path]`
section for exactly this reason — if you copied older versions, re-copy them
and run `systemctl --user daemon-reload`.

**The sync never fires although the device is plugged in when the unit
starts.** `.path` units trigger on the *transition* — the mount appearing.
If you arm the units while the device is already mounted, either replug it or
run `systemctl --user start reitti-device-sync.service` once manually.

**`HTTP 401`/`403`.** Token wrong, expired, or not bound to a device. Re-check
*Settings → API Tokens*.

**`server rejected file`.** The endpoint answered 200 but with
`success: false` — usually a wrong file type for that endpoint (FIT files
belong to `/api/v2/fit/import`, GPX to `/api/v1/gpx/import`) or a file
without usable location points.

**No files found although the device is mounted.** Run
`reitti-device-sync --dry-run --verbose` and adjust `source` /
`include_glob` to the actual layout (the debug output shows which sources
matched).

## Testing without hardware

Point `source` at any local folder containing a few GPX/FIT files and run
with `--dry-run --verbose`, then against your reitti dev instance
(`mvn spring-boot:run -Dspring-boot.run.profiles=dev`).
