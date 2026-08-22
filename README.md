# Neuron Tracer

A small full stack web app that traces sensory neuron images. Upload a JPEG or PNG, and it
returns a zip holding a [NeuronJ](https://imagescience.org/meijering/software/neuronj/) data
file you can open in Fiji, traced JPEGs, and a short statistics report.

This replaces the earlier split setup, where the page lived in an S3 bucket and the tracer ran
as a container behind a Lambda URL. Everything is now one container that runs happily on a
Raspberry Pi.

## How it works

```
browser  --POST /api/trace (raw image bytes + query params)-->  job id
         --GET  /api/trace/{id} (poll every 2s)------------->  zip + preview, base64
```

- **No database and no disk.** Images are traced in memory, held only long enough for the
  browser to collect the result, and then dropped. Nothing is ever written to disk, so the
  container runs with a read-only filesystem.
- **Submit and poll, not one long request.** A trace on a Pi can easily run past the 100
  second limit Cloudflare puts on an origin response, which would show up as a 524 error.
  Submitting a job and polling for it keeps every request short.
- **No dependencies.** The tracer runs on `java.desktop` and the web layer on the JDK's own
  HTTP server, so the build pulls nothing beyond the Maven plugins.

## Layout

```
Dockerfile                 multi-stage build: Maven -> JRE runtime
docker-compose.yml         the app plus a cloudflared sidecar
pom.xml                    dependency-free Java 21 build
src/main/java/com/neurontracer/
    Main.java              starts the HTTP server
    AppConfig.java         settings, all from the environment
    http/                  request handling, JSON output, static file serving
    trace/                 the tracing algorithm, job registry and zip packing
    image/                 pixel helpers
src/main/resources/web/    the single page front end, served from inside the jar
```

## Running it on the Pi

```bash
cp .env.example .env        # optional, every value has a default
docker compose up -d --build
docker compose logs -f neurontracer
```

The app listens on port 8080 inside the container. It is only exposed to the compose network,
so the `cloudflared` sidecar reaches it and nothing else does.

To run it without the tunnel, for example while testing on the LAN:

```bash
docker build -t neurontracer .
docker run --rm -p 8080:8080 neurontracer
# then open http://<pi-address>:8080
```

## Cloudflare tunnel

`cloudflared/config.yml` is checked into the repo and already points at `neurontracer.com` and
its tunnel, so `git pull` plus `docker compose up` is enough on a Pi that already has the
tunnel's credentials in place. Nothing in that file is secret -- the tunnel ID is already public
in DNS as `<tunnel>.cfargotunnel.com`, and it carries no keys.

What *does* stay off git, on the Pi only, is `cert.pem` and the tunnel's `<id>.json` (the actual
credentials). Deliberately kept in their own directory, separate from any other tunnel on the
same Pi, so two projects sharing a Pi can never reuse each other's Cloudflare authorization by
accident:

```bash
mkdir -p ~/.cloudflared-neurontracer

docker run --rm -it \
  -v ~/.cloudflared-neurontracer:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:2026.2.0 \
  tunnel login
# opens a URL -- log in and pick neurontracer.com

docker run --rm -it \
  -v ~/.cloudflared-neurontracer:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:2026.2.0 \
  tunnel create neurontracer

docker run --rm -it \
  -v ~/.cloudflared-neurontracer:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:2026.2.0 \
  tunnel route dns neurontracer neurontracer.com
```

`tunnel create` prints a tunnel ID and writes `<id>.json` into that directory. Put the same ID
into `cloudflared/config.yml`'s `tunnel:` and `credentials-file:` fields (the current committed
file already has the one already in production).

Only needed once per Pi. After that, `cp .env.example .env` already points `CLOUDFLARED_DIR` at
`~/.cloudflared-neurontracer`, and `docker compose up -d --build` picks the credentials up from
there. Override `CLOUDFLARED_DIR` in `.env` if you put them somewhere else.

The cloudflared container here is named `neurontracer-cloudflared`, so it will not collide with
the `cloudflared` container the recipes stack already runs.

## Configuration

Every setting is read from the environment and has a working default.

| Variable | Default | What it does |
| --- | --- | --- |
| `PORT` | `8080` | Port the server listens on |
| `BIND_ADDRESS` | `0.0.0.0` | Address the server binds to |
| `HTTP_THREADS` | `max(4, cpus)` | Threads serving HTTP requests |
| `BACKLOG` | `32` | Accept queue depth |
| `MAX_UPLOAD_MB` | `25` | Largest upload accepted |
| `MAX_IMAGE_MEGAPIXELS` | `40` | Largest image accepted |
| `TRACE_CONCURRENCY` | `max(1, min(4, cpus / 2))` | Traces running at once |
| `TRACE_QUEUE_DEPTH` | `4` | Traces allowed to wait for a slot |
| `TRACE_TIMEOUT_SECONDS` | `300` | How long one trace may run |
| `RESULT_RETENTION_SECONDS` | `600` | How long a finished result stays in memory |
| `MAX_STORED_RESULTS` | `16` | Results held in memory at once |
| `JAVA_OPTS` | see Dockerfile | JVM flags, including the heap cap |

Tracing is CPU and memory hungry. On a Pi, keep `TRACE_CONCURRENCY` at 1 or 2 and leave
`JAVA_OPTS` with its `-XX:MaxRAMPercentage=70` cap so the JVM does not crowd out the rest of
the machine.

## API

### `POST /api/trace`

The request body is the raw image bytes. Everything else rides along as query parameters, so
there is no multipart or JSON parsing on the server.

| Parameter | Default | Range |
| --- | --- | --- |
| `branchShade` | `230` | 5-254, red channel cut-off for every branch |
| `cellBranchShade` | `220` | 5-254, cut-off for branches leaving the cell body |
| `xOffset` | `40` | 5-100, horizontal search distance from the cell body |
| `yOffset` | `40` | 5-100, vertical search distance from the cell body |
| `ignore` | none | `N`, `S`, `E` or `W`, the side holding the axon |
| `name` | `neuron` | Original file name, used to name the files in the zip |

Values outside those ranges are clamped rather than rejected.

```bash
curl -X POST --data-binary @neuron.jpg \
  "http://localhost:8080/api/trace?branchShade=230&ignore=W&name=neuron.jpg"
```

```json
{"jobId":"...","statusUrl":"/api/trace/...","pollAfterSeconds":2}
```

Returns `202` on success, `400` with no body, `413` over the upload limit, or `503` when the
tracer is already at capacity.

### `GET /api/trace/{id}`

- `202` while it is still running: `{"state":"running","elapsedSeconds":12,"pollAfterSeconds":2}`
- `200` when it is done, with the zip and a preview JPEG base64 encoded:

```json
{
  "state": "done",
  "filename": "neuron-trace.zip",
  "stats": {"branches": 436, "pixels": 22287, "microns": 8432.46, "averageMicrons": 19.34, "pixelsPerMicron": 2.643},
  "preview": "<base64 jpeg>",
  "archive": "<base64 zip>"
}
```

- `404` once the result has aged out or been abandoned
- `415` if the upload was not a readable image, `504` if the trace ran past its timeout

A successful result stays available until it ages out, so a dropped download can be retried.

### `GET /healthz`

`{"status":"ok"}`, used by the container health check.

## What is in the archive

| File | Contents |
| --- | --- |
| `<name>-traced.jpg` | Branches drawn over the original image, with the search box marked |
| `<name>-traced-on-white.jpg` | The same trace on a white background |
| `<name>-traced-on-black.jpg` | Branches drawn in black over the original |
| `Tracings.ndf` | NeuronJ data file, ready to open in Fiji |
| `stats.txt` | Branch count and lengths, at 2.643 pixels per micron |

## Tuning a trace

- **Fill the cell body with pink first.** The tracer looks for pink to find the cell body.
  Without it, it falls back to detecting the darkest region, which is less reliable on low
  contrast images.
- **Nothing found?** Raise `cellBranchShade` so more pixels around the cell body count as
  branch, then raise `branchShade`.
- **Too much found?** Lower both shades.
- **Branches at the cell body missed?** Adjust `xOffset` and `yOffset` so the search box clears
  the cell body without swallowing the branches.
- **Axon traced as a dendrite?** Set `ignore` to the side it leaves from.

## Local development

```bash
mvn package
java -jar target/neuron-tracer.jar
# http://localhost:8080
```

Java 21 or newer. There are no dependencies and no test suite.
